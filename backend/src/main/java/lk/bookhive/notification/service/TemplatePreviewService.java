package lk.bookhive.notification.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.exception.BusinessRuleException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.config.MailProperties;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.repository.OutboxMessageRepository;
import lk.bookhive.notification.service.EmailRenderer.RenderedEmail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Previews of every message (checklist step 324): each type rendered for each channel in any
 * language, with made-up sample data, so admins can check wording and layout. Nothing is sent or
 * stored, except a test email an admin asks for (step 325): that goes only to their own address,
 * through the real outbox, marked [TEST].
 */
@Service
public class TemplatePreviewService {

  /**
   * @param email null when the type has no email
   * @param sms null when the type has no SMS wording
   * @param inAppTitle null when the type has no in-app message
   */
  public record Preview(
      NotificationType type,
      Language language,
      RenderedEmail email,
      String sms,
      String inAppTitle,
      String inAppBody) {}

  private final EmailRenderer emails;
  private final SmsRenderer sms;
  private final InAppTexts inApp;
  private final MailProperties mail;
  private final OutboxMessageRepository outbox;
  private final AuthService authService;
  private final AuditService audit;
  private final LibraryTime time;

  public TemplatePreviewService(
      EmailRenderer emails,
      SmsRenderer sms,
      InAppTexts inApp,
      MailProperties mail,
      OutboxMessageRepository outbox,
      AuthService authService,
      AuditService audit,
      LibraryTime time) {
    this.emails = emails;
    this.sms = sms;
    this.inApp = inApp;
    this.mail = mail;
    this.outbox = outbox;
    this.authService = authService;
    this.audit = audit;
    this.time = time;
  }

  /** Every type, in the order they're declared. */
  public List<NotificationType> types() {
    return Arrays.asList(NotificationType.values());
  }

  public Preview preview(NotificationType type, Language language) {
    Map<String, Object> params = sample(type, language);
    var channels = type.defaultChannels();
    RenderedEmail email =
        channels.contains(Channel.EMAIL) ? emails.render(type, language, params) : null;
    String text = channels.contains(Channel.SMS) ? sms.render(type, language, params) : null;
    boolean hasInApp = channels.contains(Channel.IN_APP);
    Map<String, String> facts = inApp.facts(params);
    return new Preview(
        type,
        language,
        email,
        text,
        hasInApp ? inApp.title(type, language) : null,
        hasInApp ? inApp.body(type, facts, language) : null);
  }

  /**
   * Sends the sample email of this type to the admin's own address (step 325), through the outbox
   * like any email, so it really goes through the mail server (Mailpit in development).
   *
   * @return the address it was sent to
   */
  @Transactional
  public String sendTestEmail(NotificationType type, Language language, UUID adminId) {
    Preview preview = preview(type, language);
    if (preview.email() == null) {
      throw new BusinessRuleException("NO_EMAIL_TEMPLATE", "This message isn't sent by email.");
    }
    User admin = authService.getProfile(adminId);
    outbox.save(
        OutboxMessage.email(
            type,
            adminId,
            admin.getEmail(),
            language,
            "[TEST] " + preview.email().subject(),
            preview.email().text(),
            preview.email().html(),
            null,
            time.now()));
    audit.record(
        "notification.test_email",
        adminId,
        "notification_type",
        null,
        Map.of("type", type.name(), "language", language.code()));
    return admin.getEmail();
  }

  /** Believable values for each type's template (see the comments in templates/email). */
  private Map<String, Object> sample(NotificationType type, Language language) {
    LocalDate today = time.today();
    LocalDate due = today.plusDays(2);
    String site = mail.frontendUrl() + "/" + language.code();
    Map<String, Object> book =
        Map.of("title", "Madol Doova", "barcode", "C0000123", "dueDate", due);
    Map<String, Object> params = new HashMap<>();
    params.put("name", "Nimali Perera");
    params.put("link", site + "/account");
    if (type.optional()) {
      params.put("preferencesLink", site + "/account/notifications/settings");
    }
    switch (type) {
      case STAFF_INVITE -> params.put("role", "LIBRARIAN");
      case MEMBERSHIP_APPROVED -> {
        params.put("membershipType", "Adult");
        params.put("memberNo", "BH-000123");
        params.put("expiresAt", today.plusYears(1));
      }
      case MEMBERSHIP_REJECTED -> params.put("reason", "The photo of the NIC can't be read.");
      case MEMBERSHIP_EXPIRING -> params.put("expiresAt", today.plusDays(30));
      case CHECKOUT_RECEIPT -> {
        params.put("memberNo", "BH-000123");
        params.put("checkedOutAt", LocalDateTime.now(LibraryTime.ZONE).withSecond(0).withNano(0));
        params.put(
            "books",
            List.of(book, Map.of("title", "Gamperaliya", "barcode", "C0000456", "dueDate", due)));
      }
      case DUE_SOON -> params.put("books", List.of(book));
      case OVERDUE ->
          params.put(
              "books",
              List.of(
                  Map.of("title", "Madol Doova", "dueDate", today.minusDays(3), "daysLate", 3)));
      case HOLD_READY, HOLD_EXPIRING -> {
        params.put("title", "Madol Doova");
        params.put("pickUpBy", today.plusDays(3));
      }
      case HOLD_EXPIRED -> params.put("title", "Madol Doova");
      case PAYMENT_RECEIVED -> {
        params.put("amount", new BigDecimal("175.00"));
        params.put("reference", "R-0042");
        params.put(
            "items",
            List.of(
                Map.of(
                    "description",
                    "Late return: Madol Doova",
                    "amount",
                    new BigDecimal("175.00"))));
      }
      case EVENT_REMINDER, EVENT_PLACE_CONFIRMED, EVENT_CANCELLED -> {
        params.put("title", "Children’s story time");
        params.put("date", today.plusDays(1));
        params.put("startsAt", today.plusDays(1).atTime(10, 0));
        params.put("venue", "Children’s corner");
        params.put("reason", "The storyteller is unwell. We’ll announce a new date soon.");
      }
      default -> {
        // Account emails only need the name and link
      }
    }
    return params;
  }
}
