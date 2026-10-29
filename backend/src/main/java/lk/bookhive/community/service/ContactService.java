package lk.bookhive.community.service;

import java.util.Map;
import lk.bookhive.admin.service.LibraryInfoService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.domain.Language;
import lk.bookhive.community.domain.ContactMessage;
import lk.bookhive.community.event.ContactMessageReceived;
import lk.bookhive.community.repository.ContactMessageRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The visit page's contact form (checklist step 207). */
@Service
public class ContactService {

  private final ContactMessageRepository messages;
  private final TurnstileVerifier turnstile;
  private final LibraryInfoService libraryInfo;
  private final ApplicationEventPublisher events;
  private final AuditService audit;

  public ContactService(
      ContactMessageRepository messages,
      TurnstileVerifier turnstile,
      LibraryInfoService libraryInfo,
      ApplicationEventPublisher events,
      AuditService audit) {
    this.messages = messages;
    this.turnstile = turnstile;
    this.libraryInfo = libraryInfo;
    this.events = events;
    this.audit = audit;
  }

  /** Checks the robot test, saves the message and has it emailed to the library. */
  @Transactional
  public ContactMessage submit(
      String name,
      String email,
      String subject,
      String text,
      Language language,
      String turnstileToken,
      String clientIp) {
    turnstile.verify(turnstileToken, clientIp);
    ContactMessage message =
        messages.save(new ContactMessage(name, email, subject, text, language));
    events.publishEvent(
        new ContactMessageReceived(
            message.getId(),
            libraryInfo.current().getEmail(),
            message.getName(),
            message.getEmail(),
            message.getSubject(),
            message.getMessage(),
            language.code()));
    audit.record("contact.received", null, "contact_message", message.getId(), Map.of());
    return message;
  }
}
