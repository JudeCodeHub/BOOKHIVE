package lk.bookhive.notification.service;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import lk.bookhive.circulation.event.CheckoutReceiptRequested;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Loan emails. For now the checkout receipt (CIRC-8, step 239), sent only after the checkout is
 * saved. English only for now, like {@link MemberEmailListener}.
 */
@Component
public class LoanEmailListener {

  private static final Logger log = LoggerFactory.getLogger(LoanEmailListener.class);
  private static final DateTimeFormatter DATE =
      DateTimeFormatter.ofPattern("EEE d MMMM yyyy", Locale.ENGLISH);
  private static final DateTimeFormatter DATE_TIME =
      DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm", Locale.ENGLISH).withZone(LibraryTime.ZONE);

  private final EmailSender emailSender;

  public LoanEmailListener(EmailSender emailSender) {
    this.emailSender = emailSender;
  }

  @NotificationListener
  public void onReceipt(CheckoutReceiptRequested event) {
    String books =
        event.lines().stream()
            .map(
                line ->
                    "- %s (%s)%n  Due back: %s"
                        .formatted(line.title(), line.barcode(), DATE.format(line.dueDate())))
            .collect(Collectors.joining(System.lineSeparator()));
    String text =
        """
        Hello %s,

        Here is your receipt for the books you borrowed on %s.
        Member number: %s

        %s

        You can renew books from "My loans" on the website, unless someone has reserved them.
        Late returns are charged a small fine per day, so please bring them back on time.

        Happy reading!
        BookHive
        """
            .formatted(
                event.fullName(), DATE_TIME.format(event.checkedOutAt()), event.memberNo(), books);
    try {
      emailSender.send(
          new EmailMessage(
              event.email(),
              "Your BookHive receipt: %d %s borrowed"
                  .formatted(event.lines().size(), event.lines().size() == 1 ? "book" : "books"),
              text));
    } catch (RuntimeException e) {
      // The loans are saved either way; reliable retries come with the outbox (step 355)
      log.error("Could not send checkout receipt to member {}", event.memberNo(), e);
    }
  }
}
