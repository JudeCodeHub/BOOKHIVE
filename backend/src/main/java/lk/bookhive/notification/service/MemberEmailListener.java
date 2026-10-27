package lk.bookhive.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import lk.bookhive.member.event.MembershipApproved;
import lk.bookhive.member.event.MembershipRejected;
import lk.bookhive.notification.config.MailProperties;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Tells applicants the outcome of their membership application (MEM-3). Sent only after the
 * decision is saved. English only for now, like {@link AuthEmailListener}.
 */
@Component
public class MemberEmailListener {

  private static final Logger log = LoggerFactory.getLogger(MemberEmailListener.class);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

  private final EmailSender emailSender;
  private final MailProperties properties;

  public MemberEmailListener(EmailSender emailSender, MailProperties properties) {
    this.emailSender = emailSender;
    this.properties = properties;
  }

  @NotificationListener
  public void onApproved(MembershipApproved event) {
    String cardLink = link(event.language().code(), "account/card");
    send(
        event.email(),
        "Welcome to the library! Your membership is approved",
        """
        Hello %s,

        Good news: your %s membership has been approved.

        Your member number: %s
        Valid until: %s

        Your digital library card is ready here: %s
        Show it at the desk to borrow books.

        Happy reading!
        BookHive
        """
            .formatted(
                event.fullName(),
                event.membershipType(),
                event.memberNo(),
                DATE.format(event.expiresAt()),
                cardLink));
  }

  @NotificationListener
  public void onRejected(MembershipRejected event) {
    String applyLink = link(event.language().code(), "account/membership");
    send(
        event.email(),
        "About your BookHive membership application",
        """
        Hello %s,

        Unfortunately we could not approve your membership application.

        Reason: %s

        You can correct this and apply again here: %s
        If you have questions, please contact the library.

        BookHive
        """
            .formatted(event.fullName(), event.reason(), applyLink));
  }

  private String link(String language, String path) {
    return UriComponentsBuilder.fromUriString(properties.frontendUrl())
        .pathSegment(language)
        .path("/" + path)
        .toUriString();
  }

  private void send(String to, String subject, String text) {
    try {
      emailSender.send(new EmailMessage(to, subject, text));
    } catch (RuntimeException e) {
      // The decision is saved either way; reliable retries come with the outbox (step 355)
      log.error("Could not send membership email: {}", subject, e);
    }
  }
}
