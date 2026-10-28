package lk.bookhive.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;
import lk.bookhive.common.exception.ConflictException;

/**
 * A member's place at an event (table {@code event_registrations}, EVT-2/EVT-3, step 330): a place,
 * or a spot on the waitlist, and later "attended" when their QR ticket is scanned at the door. The
 * rules (capacity, who moves up) are in the event services.
 */
@Entity
@Table(name = "event_registrations")
public class EventRegistration extends BaseEntity {

  private static final SecureRandom RANDOM = new SecureRandom();

  @Column(nullable = false, updatable = false)
  private UUID eventId;

  @Column(nullable = false, updatable = false)
  private UUID memberId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private RegistrationStatus status;

  @Column(nullable = false, updatable = false)
  private Instant registeredAt;

  private Instant confirmedAt;

  private Instant cancelledAt;

  private Instant checkedInAt;

  private UUID checkedInBy;

  @Column(nullable = false, length = 40, updatable = false)
  private String checkInCode;

  protected EventRegistration() {
    // for JPA
  }

  /**
   * Signs up: a place if one is free, otherwise the waitlist.
   *
   * @param placeFree there's a free place (or no limit)
   */
  public static EventRegistration signUp(
      UUID eventId, UUID memberId, boolean placeFree, Instant now) {
    EventRegistration registration = new EventRegistration();
    registration.eventId = eventId;
    registration.memberId = memberId;
    registration.registeredAt = now;
    registration.checkInCode = newCode();
    if (placeFree) {
      registration.status = RegistrationStatus.REGISTERED;
      registration.confirmedAt = now;
    } else {
      registration.status = RegistrationStatus.WAITLISTED;
    }
    return registration;
  }

  /** A place came free and this is the first on the waitlist (step 332). */
  public void promote(Instant now) {
    if (status != RegistrationStatus.WAITLISTED) {
      throw new ConflictException("NOT_WAITLISTED", "This registration isn't on the waitlist.");
    }
    this.status = RegistrationStatus.REGISTERED;
    this.confirmedAt = now;
  }

  /**
   * Gives the place up (step 333).
   *
   * @return true when a place was freed (it was REGISTERED), so the waitlist can move up
   */
  public boolean cancel(Instant now) {
    if (!status.isActive()) {
      throw new ConflictException("REGISTRATION_NOT_ACTIVE", "This registration isn't active.");
    }
    boolean freedPlace = status == RegistrationStatus.REGISTERED;
    this.status = RegistrationStatus.CANCELLED;
    this.cancelledAt = now;
    return freedPlace;
  }

  /** Their ticket was scanned at the door (step 334). */
  public void checkIn(UUID staffId, Instant now) {
    if (status == RegistrationStatus.ATTENDED) {
      throw new ConflictException("ALREADY_CHECKED_IN", "Already checked in.");
    }
    if (status != RegistrationStatus.REGISTERED) {
      throw new ConflictException(
          "NOT_REGISTERED", "This ticket isn't for a confirmed place (waitlisted or cancelled).");
    }
    this.status = RegistrationStatus.ATTENDED;
    this.checkedInAt = now;
    this.checkedInBy = staffId;
  }

  /** 24 random URL-safe characters (144 bits): unguessable, short enough for a QR code. */
  private static String newCode() {
    byte[] bytes = new byte[18];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getMemberId() {
    return memberId;
  }

  public RegistrationStatus getStatus() {
    return status;
  }

  public Instant getRegisteredAt() {
    return registeredAt;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public Instant getCheckedInAt() {
    return checkedInAt;
  }

  public UUID getCheckedInBy() {
    return checkedInBy;
  }

  public String getCheckInCode() {
    return checkInCode;
  }
}
