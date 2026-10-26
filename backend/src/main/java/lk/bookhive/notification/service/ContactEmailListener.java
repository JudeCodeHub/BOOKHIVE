package lk.bookhive.notification.service;

import lk.bookhive.community.event.ContactMessageReceived;
import lk.bookhive.notification.service.EmailSender.EmailMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Forwards contact form messages to the library's inbox, once they're saved. */
@Component
public class ContactEmailListener {

  private static final Logger log = LoggerFactory.getLogger(ContactEmailListener.class);

  private final EmailSender emailSender;

  public ContactEmailListener(EmailSender emailSender) {
    this.emailSender = emailSender;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onContactMessage(ContactMessageReceived event) {
    try {
      emailSender.send(
          new EmailMessage(
              event.libraryEmail(),
              "[Website] " + event.subject(),
              """
              New message from the website contact form.

              From: %s <%s>
              Language: %s

              %s

              Reply to the sender's email address above.
              """
                  .formatted(event.name(), event.email(), event.language(), event.message())));
    } catch (RuntimeException e) {
      // The message is saved either way; staff can still find it
      log.error("Could not email contact message {}", event.messageId(), e);
    }
  }
}
