package lk.bookhive.notification.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.domain.OutboxStatus;
import lk.bookhive.notification.repository.OutboxMessageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admins look after message delivery (checklist step 319): which emails and SMS failed after all
 * their tries and why, and sending them again once the cause is fixed (e.g. SMTP settings).
 */
@Service
public class OutboxAdminService {

  /** How many messages wait, went out, or failed. */
  public record OutboxCounts(long pending, long sent, long failed) {}

  private final OutboxMessageRepository outbox;
  private final AuditService audit;
  private final LibraryTime time;

  public OutboxAdminService(OutboxMessageRepository outbox, AuditService audit, LibraryTime time) {
    this.outbox = outbox;
    this.audit = audit;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public OutboxCounts counts() {
    return new OutboxCounts(
        outbox.countByStatus(OutboxStatus.PENDING),
        outbox.countByStatus(OutboxStatus.SENT),
        outbox.countByStatus(OutboxStatus.FAILED));
  }

  @Transactional(readOnly = true)
  public Page<OutboxMessage> list(OutboxStatus status, int page, int size) {
    return outbox.findByStatusOrderByUpdatedAtDesc(status, PageRequest.of(page, size));
  }

  /** Puts one FAILED message back in the queue with a fresh set of tries. */
  @Transactional
  public OutboxMessage retry(UUID id, UUID adminId) {
    OutboxMessage message =
        outbox
            .findById(id)
            .orElseThrow(() -> new NotFoundException("MESSAGE_NOT_FOUND", "Message not found."));
    if (message.getStatus() != OutboxStatus.FAILED) {
      throw new BusinessRuleException(
          "MESSAGE_NOT_FAILED", "Only messages that failed can be sent again.");
    }
    message.retryNow(time.now());
    audit.record(
        "notification.retry",
        adminId,
        "outbox_message",
        id,
        Map.of("type", message.getNotificationType().name()));
    return message;
  }

  /** Every FAILED message back in the queue (after fixing e.g. the mail server settings). */
  @Transactional
  public int retryAllFailed(UUID adminId) {
    List<OutboxMessage> failed = outbox.findByStatus(OutboxStatus.FAILED);
    failed.forEach(message -> message.retryNow(time.now()));
    audit.record("notification.retry_all", adminId, "outbox", null, Map.of("count", failed.size()));
    return failed.size();
  }
}
