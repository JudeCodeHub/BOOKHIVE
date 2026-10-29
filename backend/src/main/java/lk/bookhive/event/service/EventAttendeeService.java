package lk.bookhive.event.service;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.text.SpreadsheetCells;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.event.domain.EventRegistration;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.RegistrationStatus;
import lk.bookhive.event.repository.EventRegistrationRepository;
import lk.bookhive.event.repository.LibraryEventRepository;
import lk.bookhive.member.service.BorrowerService;
import lk.bookhive.member.service.BorrowerService.Borrower;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who's coming to an event (checklist step 344): everyone with a place, checked in or waiting, in
 * sign-up order, for the staff screen and as a CSV for the event organiser.
 */
@Service
public class EventAttendeeService {

  private static final List<RegistrationStatus> LISTED =
      List.of(
          RegistrationStatus.REGISTERED,
          RegistrationStatus.ATTENDED,
          RegistrationStatus.WAITLISTED);

  private static final DateTimeFormatter CSV_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(LibraryTime.ZONE);

  /**
   * One person signed up.
   *
   * @param waitlistPosition 1 = next to get a place; null when they have one
   * @param checkedInAt null until they're let in at the door
   */
  public record AttendeeSummary(
      UUID registrationId,
      String memberNo,
      String fullName,
      String email,
      @Schema(nullable = true) String phone,
      RegistrationStatus status,
      @Schema(nullable = true) Integer waitlistPosition,
      Instant registeredAt,
      @Schema(nullable = true) Instant checkedInAt) {}

  private final LibraryEventRepository events;
  private final EventRegistrationRepository registrations;
  private final BorrowerService borrowers;
  private final AuthService authService;
  private final AuditService audit;

  public EventAttendeeService(
      LibraryEventRepository events,
      EventRegistrationRepository registrations,
      BorrowerService borrowers,
      AuthService authService,
      AuditService audit) {
    this.events = events;
    this.registrations = registrations;
    this.borrowers = borrowers;
    this.authService = authService;
    this.audit = audit;
  }

  /** Places first (sign-up order), then the waitlist in its order. */
  @Transactional(readOnly = true)
  public List<AttendeeSummary> attendees(UUID eventId) {
    require(eventId);
    List<EventRegistration> rows =
        registrations.findByEventIdAndStatusInOrderByRegisteredAt(eventId, LISTED);
    Map<UUID, Borrower> members =
        borrowers.byIds(rows.stream().map(EventRegistration::getMemberId).toList());
    Map<UUID, User> users =
        authService.findUsers(members.values().stream().map(Borrower::userId).toList());

    List<AttendeeSummary> withPlace = new ArrayList<>();
    List<AttendeeSummary> waiting = new ArrayList<>();
    for (EventRegistration r : rows) {
      Borrower member = members.get(r.getMemberId());
      User user = member == null ? null : users.get(member.userId());
      boolean waits = r.getStatus() == RegistrationStatus.WAITLISTED;
      AttendeeSummary attendee =
          new AttendeeSummary(
              r.getId(),
              member == null ? null : member.memberNo(),
              user == null ? null : user.getFullName(),
              user == null ? null : user.getEmail(),
              user == null ? null : user.getPhone(),
              r.getStatus(),
              waits ? waiting.size() + 1 : null,
              r.getRegisteredAt(),
              r.getCheckedInAt());
      (waits ? waiting : withPlace).add(attendee);
    }
    withPlace.addAll(waiting);
    return withPlace;
  }

  /**
   * The list as CSV (UTF-8 with a byte order mark, so Excel shows Sinhala and Tamil names).
   * Contains contact details, so every export is written to the audit log.
   */
  @Transactional
  public String csv(UUID eventId, UUID staffId) {
    List<AttendeeSummary> attendees = attendees(eventId);
    StringWriter out = new StringWriter().append('﻿');
    try (CSVPrinter csv =
        new CSVPrinter(
            out,
            CSVFormat.DEFAULT
                .builder()
                .setHeader(
                    "Member no",
                    "Name",
                    "Email",
                    "Phone",
                    "Status",
                    "Waitlist position",
                    "Signed up",
                    "Checked in")
                .get())) {
      for (AttendeeSummary a : attendees) {
        csv.printRecord(
            SpreadsheetCells.safe(a.memberNo()),
            SpreadsheetCells.safe(a.fullName()),
            SpreadsheetCells.safe(a.email()),
            SpreadsheetCells.safe(a.phone()),
            a.status(),
            a.waitlistPosition(),
            CSV_TIME.format(a.registeredAt()),
            a.checkedInAt() == null ? "" : CSV_TIME.format(a.checkedInAt()));
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    audit.record(
        "event.attendees_exported", staffId, "event", eventId, Map.of("rows", attendees.size()));
    return out.toString();
  }

  /** A short file name: "attendees-2026-11-20-story-time.csv". */
  @Transactional(readOnly = true)
  public String fileName(UUID eventId) {
    LibraryEvent event = require(eventId);
    String slug =
        event.getTitle().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    String day =
        DateTimeFormatter.ISO_LOCAL_DATE.withZone(LibraryTime.ZONE).format(event.getStartsAt());
    return "attendees-"
        + day
        + (slug.isEmpty() ? "" : "-" + slug.substring(0, Math.min(40, slug.length())))
        + ".csv";
  }

  private LibraryEvent require(UUID eventId) {
    return events
        .findById(eventId)
        .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "Event not found."));
  }
}
