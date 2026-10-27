package lk.bookhive.notification.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.config.OutboxProperties;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.repository.OutboxMessageRepository;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends what's due in the outbox (ADR 0004, NOTIF-6, checklist step 308). Each round takes a batch
 * of PENDING messages whose time has come (row-locked with SKIP LOCKED, so a second instance takes
 * others) and tries each one:
 *
 * <ul>
 *   <li>accepted by the provider: SENT;
 *   <li>failed: tried again later, waiting 1 min, 2, 4... (up to the maximum wait), and marked
 *       FAILED after the last allowed try, for an admin to see (step 319).
 * </ul>
 *
 * One message failing never stops the others.
 */
@Service
public class OutboxDispatcher {

  private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);

  private final OutboxMessageRepository outbox;
  private final EmailSender email;
  private final SmsSender sms;
  private final OutboxProperties properties;
  private final TransactionTemplate tx;
  private final LibraryTime time;

  public OutboxDispatcher(
      OutboxMessageRepository outbox,
      EmailSender email,
      ObjectProvider<SmsSender> sms,
      OutboxProperties properties,
      TransactionTemplate tx,
      LibraryTime time) {
    this.outbox = outbox;
    this.email = email;
    this.sms = sms.getIfAvailable();
    this.properties = properties;
    this.tx = tx;
    this.time = time;
  }

  /**
   * Sends batches until nothing more is due (or a safety limit of rounds is reached).
   *
   * @return how many messages were sent
   */
  public int dispatchDue() {
    int sent = 0;
    for (int round = 0; round < 20; round++) {
      Integer batchSent = tx.execute(status -> dispatchBatch());
      if (batchSent == null || batchSent < 0) {
        break;
      }
      sent += batchSent;
    }
    return sent;
  }

  /**
   * One batch in one transaction: the rows stay locked until every message in it is recorded.
   *
   * @return messages sent; -1 when nothing was due
   */
  private int dispatchBatch() {
    Instant now = time.now();
    List<UUID> ids = outbox.lockDue(now, properties.batchSize());
    if (ids.isEmpty()) {
      return -1;
    }
    int sent = 0;
    for (OutboxMessage message : outbox.findAllById(ids)) {
      if (deliver(message, now)) {
        sent++;
      }
    }
    return sent;
  }

  private boolean deliver(OutboxMessage message, Instant now) {
    try {
      if (message.getChannel() == Channel.EMAIL) {
        email.send(
            new EmailMessage(message.getRecipient(), message.getSubject(), message.getBodyText()));
      } else if (sms != null) {
        sms.send(message.getRecipient(), message.getBodyText());
      } else {
        // Nothing to send it with: no point retrying until SMS is set up (step 311)
        message.markFailed("SMS sending is not configured", properties.firstRetry(), 1, now);
        return false;
      }
      message.markSent(now);
      return true;
    } catch (RuntimeException e) {
      message.markFailed(
          e.getClass().getSimpleName() + ": " + e.getMessage(),
          properties.retryAfter(message.getAttempts() + 1),
          properties.maxAttempts(),
          now);
      log.warn(
          "Outbox {} {} to {} failed (attempt {}): {}",
          message.getChannel(),
          message.getNotificationType(),
          message.getId(),
          message.getAttempts(),
          e.getMessage());
      return false;
    }
  }
}
