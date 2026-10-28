package lk.bookhive.notification.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.config.MailProperties;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.InAppNotification;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.repository.InAppNotificationRepository;
import lk.bookhive.notification.repository.OutboxMessageRepository;
import lk.bookhive.notification.service.EmailRenderer.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The {@link Notifier} (ADR 0004, step 310): finds the recipient's address and language, renders
 * the email in that language and queues it in the outbox; the dispatcher sends it with retries. SMS
 * goes the same way when it's switched on and the member has a phone number (step 311). In-app
 * messages are stored straight away (step 312). The member's channel choices apply to optional
 * types (step 313); security messages and receipts always go out.
 *
 * <p>Never throws: it runs after the action was saved, so a problem here (a template mistake, a
 * duplicate reminder) is logged and must not turn a successful checkout into an error.
 */
@Service
public class OutboxNotifier implements Notifier {

  private static final Logger log = LoggerFactory.getLogger(OutboxNotifier.class);

  private final AuthService authService;
  private final EmailRenderer emails;
  private final SmsRenderer smsTexts;
  private final boolean smsOn;
  private final OutboxMessageRepository outbox;
  private final InAppNotificationRepository inApp;
  private final InAppTexts inAppTexts;
  private final NotificationPreferenceService preferences;
  private final MailProperties mail;
  private final TransactionTemplate tx;
  private final LibraryTime time;

  public OutboxNotifier(
      AuthService authService,
      EmailRenderer emails,
      SmsRenderer smsTexts,
      ObjectProvider<SmsSender> smsSender,
      OutboxMessageRepository outbox,
      InAppNotificationRepository inApp,
      InAppTexts inAppTexts,
      NotificationPreferenceService preferences,
      MailProperties mail,
      PlatformTransactionManager transactions,
      LibraryTime time) {
    this.authService = authService;
    this.emails = emails;
    this.smsTexts = smsTexts;
    // No SMS provider configured (the default): don't queue messages that can't be sent
    this.smsOn = smsSender.getIfAvailable() != null;
    this.outbox = outbox;
    this.inApp = inApp;
    this.inAppTexts = inAppTexts;
    this.preferences = preferences;
    this.mail = mail;
    // Its own short transaction: the caller's (if any) is already committed
    this.tx = new TransactionTemplate(transactions);
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.time = time;
  }

  @Override
  public void send(NotificationRequest request) {
    try {
      tx.executeWithoutResult(status -> queue(request));
    } catch (RuntimeException e) {
      log.error(
          "Notification {} for user {} could not be queued", request.type(), request.userId(), e);
    }
  }

  private void queue(NotificationRequest request) {
    User user = authService.getProfile(request.userId());
    Language language = user.getPreferredLanguage();
    Set<Channel> channels = preferences.channelsFor(user.getId(), request.type());
    if (channels.contains(Channel.IN_APP)) {
      storeInApp(request);
    }
    Map<String, Object> params = new HashMap<>(request.params());
    params.putIfAbsent("name", user.getFullName());
    if (request.link() != null) {
      params.put("link", absolute(request.link(), language));
    }
    if (request.type().optional()) {
      // Reminders can be switched off: say where (preferences page, step 323)
      params.put("preferencesLink", absolute("/account/notifications/settings", language));
    }

    if (channels.contains(Channel.EMAIL)) {
      queueEmail(request, user, language, params);
    }
    if (smsOn
        && channels.contains(Channel.SMS)
        && user.getPhone() != null
        && !user.getPhone().isBlank()) {
      queueSms(request, user, language, params);
    }
  }

  /** The bell's message: facts only, the words are written when it's read (any language). */
  private void storeInApp(NotificationRequest request) {
    if (request.dedupeKey() != null
        && inApp.existsByUserIdAndDedupeKey(request.userId(), request.dedupeKey())) {
      return;
    }
    inApp.save(
        new InAppNotification(
            request.userId(),
            request.type(),
            inAppTexts.facts(request.params()),
            request.link(),
            request.dedupeKey(),
            time.now()));
  }

  private void queueSms(
      NotificationRequest request, User user, Language language, Map<String, Object> params) {
    if (request.dedupeKey() != null
        && outbox.existsByDedupeKeyAndChannel(request.dedupeKey(), Channel.SMS)) {
      return;
    }
    String text = smsTexts.render(request.type(), language, params);
    if (text == null) {
      return;
    }
    outbox.save(
        OutboxMessage.sms(
            request.type(),
            user.getId(),
            user.getPhone(),
            language,
            text,
            request.dedupeKey(),
            time.now()));
  }

  private void queueEmail(
      NotificationRequest request, User user, Language language, Map<String, Object> params) {
    if (request.dedupeKey() != null
        && outbox.existsByDedupeKeyAndChannel(request.dedupeKey(), Channel.EMAIL)) {
      return;
    }
    RenderedEmail email = emails.render(request.type(), language, params);
    outbox.save(
        OutboxMessage.email(
            request.type(),
            user.getId(),
            user.getEmail(),
            language,
            email.subject(),
            email.text(),
            email.html(),
            request.dedupeKey(),
            time.now()));
  }

  /** "/account/loans" -> "http://localhost:3000/si/account/loans"; full URLs stay as they are. */
  private String absolute(String path, Language language) {
    if (path.startsWith("http://") || path.startsWith("https://")) {
      return path;
    }
    return UriComponentsBuilder.fromUriString(mail.frontendUrl())
        .path("/" + language.code() + path)
        .build()
        .toUriString();
  }
}
