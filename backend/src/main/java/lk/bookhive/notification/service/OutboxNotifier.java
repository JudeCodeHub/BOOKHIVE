package lk.bookhive.notification.service;

import java.util.HashMap;
import java.util.Map;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.config.MailProperties;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.repository.OutboxMessageRepository;
import lk.bookhive.notification.service.EmailRenderer.RenderedEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * The {@link Notifier} (ADR 0004, step 310): finds the recipient's address and language, renders
 * the email in that language and queues it in the outbox; the dispatcher sends it with retries. SMS
 * (step 311), in-app messages (step 312) and preferences (step 313) plug in here.
 *
 * <p>Never throws: it runs after the action was saved, so a problem here (a template mistake, a
 * duplicate reminder) is logged and must not turn a successful checkout into an error.
 */
@Service
public class OutboxNotifier implements Notifier {

  private static final Logger log = LoggerFactory.getLogger(OutboxNotifier.class);

  private final AuthService authService;
  private final EmailRenderer emails;
  private final OutboxMessageRepository outbox;
  private final MailProperties mail;
  private final TransactionTemplate tx;
  private final LibraryTime time;

  public OutboxNotifier(
      AuthService authService,
      EmailRenderer emails,
      OutboxMessageRepository outbox,
      MailProperties mail,
      PlatformTransactionManager transactions,
      LibraryTime time) {
    this.authService = authService;
    this.emails = emails;
    this.outbox = outbox;
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
    Map<String, Object> params = new HashMap<>(request.params());
    params.putIfAbsent("name", user.getFullName());
    if (request.link() != null) {
      params.put("link", absolute(request.link(), language));
    }
    if (request.type().optional()) {
      // Reminders can be switched off: say where (preferences page, step 323)
      params.put("preferencesLink", absolute("/account/notifications", language));
    }

    if (request.type().defaultChannels().contains(Channel.EMAIL)) {
      queueEmail(request, user, language, params);
    }
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
