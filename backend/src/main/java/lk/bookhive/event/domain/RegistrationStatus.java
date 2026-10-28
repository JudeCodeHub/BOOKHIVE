package lk.bookhive.event.domain;

/** A member's place at an event (V30). REGISTERED and WAITLISTED are still active. */
public enum RegistrationStatus {
  REGISTERED,
  WAITLISTED,
  CANCELLED,
  ATTENDED;

  public boolean isActive() {
    return this == REGISTERED || this == WAITLISTED;
  }
}
