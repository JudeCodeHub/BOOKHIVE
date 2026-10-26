package lk.bookhive.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.domain.Language;

/** A message sent through the contact form (table {@code contact_messages}). */
@Entity
@Table(name = "contact_messages")
public class ContactMessage extends BaseEntity {

  @Column(nullable = false, length = 150)
  private String name;

  @Column(nullable = false, length = 254)
  private String email;

  @Column(nullable = false, length = 200)
  private String subject;

  @Column(nullable = false, length = 5000)
  private String message;

  @Column(nullable = false, length = 2)
  private Language language;

  private Instant handledAt;

  private UUID handledBy;

  protected ContactMessage() {
    // for JPA
  }

  public ContactMessage(
      String name, String email, String subject, String message, Language language) {
    this.name = name.strip();
    this.email = email.strip();
    this.subject = subject.strip();
    this.message = message.strip();
    this.language = language;
  }

  public String getName() {
    return name;
  }

  public String getEmail() {
    return email;
  }

  public String getSubject() {
    return subject;
  }

  public String getMessage() {
    return message;
  }

  public Language getLanguage() {
    return language;
  }

  public Instant getHandledAt() {
    return handledAt;
  }

  public UUID getHandledBy() {
    return handledBy;
  }
}
