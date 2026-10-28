package lk.bookhive.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;
import lk.bookhive.common.exception.FieldValidationException;

/**
 * A library event (table {@code events}, EVT-1, checklist step 330): story time, a book club, a
 * workshop. Named LibraryEvent so it isn't confused with the application events modules publish.
 */
@Entity
@Table(name = "events")
public class LibraryEvent extends BaseEntity {

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(nullable = false)
  private Instant startsAt;

  @Column(nullable = false)
  private Instant endsAt;

  @Column(nullable = false, length = 200)
  private String venue;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventAudience audience;

  @Column(nullable = false)
  private boolean registrationRequired;

  /** Places; null = no limit. */
  private Integer capacity;

  @Column(length = 300)
  private String imageKey;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EventStatus status;

  @Column(length = 500)
  private String cancelReason;

  @Column(updatable = false)
  private UUID createdBy;

  private Instant publishedAt;

  protected LibraryEvent() {
    // for JPA
  }

  /** What staff fill in. {@code description} is already sanitised (step 336). */
  public record Details(
      String title,
      String description,
      Instant startsAt,
      Instant endsAt,
      String venue,
      EventAudience audience,
      boolean registrationRequired,
      Integer capacity) {}

  /** A new event, as a draft only staff can see. */
  public static LibraryEvent draft(Details details, UUID createdBy) {
    LibraryEvent event = new LibraryEvent();
    event.status = EventStatus.DRAFT;
    event.createdBy = createdBy;
    event.update(details);
    return event;
  }

  public void update(Details details) {
    if (status == EventStatus.CANCELLED) {
      throw new ConflictException("EVENT_CANCELLED", "A cancelled event can't be changed.");
    }
    if (!details.endsAt().isAfter(details.startsAt())) {
      throw new FieldValidationException("endsAt", "ENDS_BEFORE_START", "End after the start.");
    }
    this.title = details.title().strip();
    this.description = details.description() == null ? "" : details.description();
    this.startsAt = details.startsAt();
    this.endsAt = details.endsAt();
    this.venue = details.venue().strip();
    this.audience = details.audience() == null ? EventAudience.ALL : details.audience();
    this.registrationRequired = details.registrationRequired();
    this.capacity = details.registrationRequired() ? details.capacity() : null;
  }

  /** On the site, open for sign-up. */
  public void publish(Instant now) {
    if (status != EventStatus.DRAFT) {
      throw new ConflictException("EVENT_NOT_DRAFT", "Only a draft can be published.");
    }
    this.status = EventStatus.PUBLISHED;
    this.publishedAt = now;
  }

  /** Called off; everyone signed up is told why (step 333). */
  public void cancel(String reason) {
    if (status == EventStatus.CANCELLED) {
      throw new ConflictException("EVENT_CANCELLED", "This event is already cancelled.");
    }
    this.status = EventStatus.CANCELLED;
    this.cancelReason = reason.strip();
  }

  public void setImageKey(String imageKey) {
    this.imageKey = imageKey;
  }

  /** Members can sign up now: published, not started, and sign-up is used. */
  public boolean isOpenForRegistration(Instant now) {
    return status == EventStatus.PUBLISHED && registrationRequired && now.isBefore(startsAt);
  }

  public boolean hasLimitedPlaces() {
    return capacity != null;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getVenue() {
    return venue;
  }

  public EventAudience getAudience() {
    return audience;
  }

  public boolean isRegistrationRequired() {
    return registrationRequired;
  }

  public Integer getCapacity() {
    return capacity;
  }

  public String getImageKey() {
    return imageKey;
  }

  public EventStatus getStatus() {
    return status;
  }

  public String getCancelReason() {
    return cancelReason;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getPublishedAt() {
    return publishedAt;
  }
}
