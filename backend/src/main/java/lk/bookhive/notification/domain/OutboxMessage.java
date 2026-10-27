package lk.bookhive.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;

/**
 * One email or SMS waiting to be sent, or already sent (table {@code outbox_messages}, ADR 0004,
 * checklist step 307). The content is rendered when it's queued, so a retry sends exactly the same
 * message; the dispatcher (step 308) records each attempt.
 */
@Entity
@Table(name = "outbox_messages")
public class OutboxMessage extends BaseEntity {

  /** Longest error text kept (the column is 1000). */
  private static final int MAX_ERROR = 1000;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10, updatable = false)
  private Channel channel;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40, updatable = false)
  private NotificationType notificationType;

  @Column(updatable = false)
  private UUID userId;

  @Column(nullable = false, length = 254, updatable = false)
  private String recipient;

  @Column(nullable = false, length = 2, updatable = false)
  private String language;

  @Column(length = 300, updatable = false)
  private String subject;

  @Column(nullable = false, updatable = false, columnDefinition = "text")
  private String bodyText;

  @Column(updatable = false, columnDefinition = "text")
  private String bodyHtml;

  @Column(length = 200, updatable = false)
  private String dedupeKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private OutboxStatus status;

  @Column(nullable = false)
  private int attempts;

  @Column(nullable = false)
  private Instant nextAttemptAt;

  @Column(length = MAX_ERROR)
  private String lastError;

  private Instant sentAt;

  protected OutboxMessage() {
    // for JPA
  }

  /** An email, sent as soon as the dispatcher runs. {@code html} may be null (plain text only). */
  public static OutboxMessage email(
      NotificationType type,
      UUID userId,
      String address,
      Language language,
      String subject,
      String text,
      String html,
      String dedupeKey,
      Instant now) {
    OutboxMessage message = queued(Channel.EMAIL, type, userId, address, language, dedupeKey, now);
    message.subject = subject;
    message.bodyText = text;
    message.bodyHtml = html;
    return message;
  }

  /** A text message to a phone number. */
  public static OutboxMessage sms(
      NotificationType type,
      UUID userId,
      String phone,
      Language language,
      String text,
      String dedupeKey,
      Instant now) {
    OutboxMessage message = queued(Channel.SMS, type, userId, phone, language, dedupeKey, now);
    message.bodyText = text;
    return message;
  }

  private static OutboxMessage queued(
      Channel channel,
      NotificationType type,
      UUID userId,
      String recipient,
      Language language,
      String dedupeKey,
      Instant now) {
    if (channel == Channel.IN_APP) {
      throw new IllegalArgumentException("In-app messages don't go through the outbox");
    }
    OutboxMessage message = new OutboxMessage();
    message.channel = channel;
    message.notificationType = type;
    message.userId = userId;
    message.recipient = recipient;
    message.language = language.code();
    message.dedupeKey = dedupeKey;
    message.status = OutboxStatus.PENDING;
    message.nextAttemptAt = now;
    return message;
  }

  /** The provider accepted it. */
  public void markSent(Instant now) {
    this.attempts++;
    this.status = OutboxStatus.SENT;
    this.sentAt = now;
    this.lastError = null;
  }

  /**
   * This attempt failed: try again after {@code retryIn}, or give up (FAILED) when that was the
   * last allowed attempt.
   */
  public void markFailed(String error, Duration retryIn, int maxAttempts, Instant now) {
    this.attempts++;
    this.lastError =
        error == null ? null : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
    if (attempts >= maxAttempts) {
      this.status = OutboxStatus.FAILED;
    } else {
      this.nextAttemptAt = now.plus(retryIn);
    }
  }

  /** An admin sends a FAILED message again (step 319): a fresh set of attempts. */
  public void retryNow(Instant now) {
    if (status == OutboxStatus.FAILED) {
      this.status = OutboxStatus.PENDING;
      this.attempts = 0;
      this.nextAttemptAt = now;
    }
  }

  public Channel getChannel() {
    return channel;
  }

  public NotificationType getNotificationType() {
    return notificationType;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getRecipient() {
    return recipient;
  }

  public Language getLanguage() {
    return Language.fromCode(language);
  }

  public String getSubject() {
    return subject;
  }

  public String getBodyText() {
    return bodyText;
  }

  public String getBodyHtml() {
    return bodyHtml;
  }

  public String getDedupeKey() {
    return dedupeKey;
  }

  public OutboxStatus getStatus() {
    return status;
  }

  public int getAttempts() {
    return attempts;
  }

  public Instant getNextAttemptAt() {
    return nextAttemptAt;
  }

  public String getLastError() {
    return lastError;
  }

  public Instant getSentAt() {
    return sentAt;
  }
}
