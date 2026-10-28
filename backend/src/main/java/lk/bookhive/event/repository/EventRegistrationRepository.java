package lk.bookhive.event.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRegistrationRepository extends JpaRepository<EventRegistration, UUID> {

  /** Statuses that hold a place or a waitlist spot. */
  List<RegistrationStatus> ACTIVE =
      List.of(RegistrationStatus.REGISTERED, RegistrationStatus.WAITLISTED);

  /** Places taken (REGISTERED and ATTENDED count). */
  long countByEventIdAndStatusIn(UUID eventId, Collection<RegistrationStatus> statuses);

  /** The member's current (not cancelled) registration for an event. */
  Optional<EventRegistration> findFirstByEventIdAndMemberIdAndStatusNot(
      UUID eventId, UUID memberId, RegistrationStatus status);

  /** First on the waitlist (step 332). */
  Optional<EventRegistration> findFirstByEventIdAndStatusOrderByRegisteredAtAscIdAsc(
      UUID eventId, RegistrationStatus status);

  /** Everyone in these statuses, in sign-up order (attendee list, waitlist, CSV export). */
  List<EventRegistration> findByEventIdAndStatusInOrderByRegisteredAt(
      UUID eventId, Collection<RegistrationStatus> statuses);

  /** Day-before reminders: everyone with a place at these events. */
  List<EventRegistration> findByEventIdInAndStatus(
      Collection<UUID> eventIds, RegistrationStatus status);

  /** "My events". */
  List<EventRegistration> findByMemberIdOrderByRegisteredAtDesc(UUID memberId);

  /** The ticket scanned at the door (step 334). */
  Optional<EventRegistration> findByCheckInCode(String checkInCode);
}
