package lk.bookhive.event.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.event.domain.EventAudience;
import lk.bookhive.event.domain.EventStatus;
import lk.bookhive.event.domain.LibraryEvent;
import lk.bookhive.event.domain.LibraryEvent.Details;
import lk.bookhive.event.service.EventAdminService;
import lk.bookhive.event.service.EventAdminService.EventWithCounts;
import lk.bookhive.event.service.EventCheckInService;
import lk.bookhive.event.service.EventCheckInService.CheckIn;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Events for staff (EVT-1, step 331): list, create, edit, publish, cancel, poster image. */
@RestController
@RequestMapping("/staff/events")
@Tag(name = "Staff: events")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class StaffEventController {

  private final EventAdminService events;
  private final EventCheckInService checkIns;

  public StaffEventController(EventAdminService events, EventCheckInService checkIns) {
    this.events = events;
    this.checkIns = checkIns;
  }

  /**
   * @param code what the QR ticket holds (with or without the BOOKHIVE-EVENT: prefix)
   */
  public record CheckInRequest(@NotBlank @Size(max = 80) String code) {}

  /**
   * The member who was let in.
   *
   * @param attended members checked in so far
   * @param capacity places (null = no limit)
   */
  public record CheckInResponse(
      UUID registrationId,
      String memberNo,
      String fullName,
      Instant checkedInAt,
      long attended,
      Integer capacity) {}

  /**
   * @param description shown on the event page
   * @param capacity places; empty = no limit (ignored when sign-up isn't required)
   */
  public record EventRequest(
      @NotBlank @Size(max = 200) String title,
      @Size(max = 20_000) String description,
      @NotNull Instant startsAt,
      @NotNull Instant endsAt,
      @NotBlank @Size(max = 200) String venue,
      EventAudience audience,
      boolean registrationRequired,
      @Positive @Max(10_000) Integer capacity) {

    Details details() {
      return new Details(
          title, description, startsAt, endsAt, venue, audience, registrationRequired, capacity);
    }
  }

  public record CancelEventRequest(@NotBlank @Size(min = 5, max = 500) String reason) {}

  /**
   * An event for staff.
   *
   * @param registered members with a place (incl. checked in)
   * @param waitlisted members waiting for a place
   * @param hasImage there's a poster (GET .../image)
   */
  public record StaffEventSummary(
      UUID id,
      String title,
      String description,
      Instant startsAt,
      Instant endsAt,
      String venue,
      EventAudience audience,
      boolean registrationRequired,
      Integer capacity,
      EventStatus status,
      String cancelReason,
      Instant publishedAt,
      boolean hasImage,
      long registered,
      long waitlisted) {

    static StaffEventSummary from(EventWithCounts row) {
      LibraryEvent e = row.event();
      return new StaffEventSummary(
          e.getId(),
          e.getTitle(),
          e.getDescription(),
          e.getStartsAt(),
          e.getEndsAt(),
          e.getVenue(),
          e.getAudience(),
          e.isRegistrationRequired(),
          e.getCapacity(),
          e.getStatus(),
          e.getCancelReason(),
          e.getPublishedAt(),
          e.getImageKey() != null,
          row.registered(),
          row.waitlisted());
    }
  }

  @GetMapping
  @Operation(summary = "All events (drafts too), latest start first, with sign-up counts")
  public PageResponse<StaffEventSummary> list(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return PageResponse.from(events.list(page, size), StaffEventSummary::from);
  }

  @GetMapping("/{id}")
  @Operation(summary = "One event with sign-up counts")
  public StaffEventSummary get(@PathVariable UUID id) {
    return StaffEventSummary.from(events.get(id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create an event (as a draft only staff can see)")
  public StaffEventSummary create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody EventRequest request) {
    LibraryEvent event = events.create(request.details(), staffId(jwt));
    return StaffEventSummary.from(events.get(event.getId()));
  }

  @PutMapping("/{id}")
  @Operation(summary = "Change an event (places can't go below those already taken)")
  public StaffEventSummary update(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody EventRequest request) {
    events.update(id, request.details(), staffId(jwt));
    return StaffEventSummary.from(events.get(id));
  }

  @PostMapping("/{id}/publish")
  @Operation(summary = "Publish a draft: on the website and open for sign-up")
  public StaffEventSummary publish(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    events.publish(id, staffId(jwt));
    return StaffEventSummary.from(events.get(id));
  }

  @PostMapping("/{id}/cancel")
  @Operation(summary = "Cancel an event with a reason (everyone signed up is told)")
  public StaffEventSummary cancel(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CancelEventRequest request) {
    events.cancel(id, request.reason(), staffId(jwt));
    return StaffEventSummary.from(events.get(id));
  }

  @PostMapping("/{id}/check-in")
  @Operation(
      summary =
          "Check a member in by their QR ticket (422/409 explain: other event, waitlisted,"
              + " already in, outside the check-in window)")
  public CheckInResponse checkIn(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @Valid @RequestBody CheckInRequest request) {
    CheckIn result = checkIns.checkIn(id, request.code(), staffId(jwt));
    return new CheckInResponse(
        result.registration().getId(),
        result.member() == null ? null : result.member().memberNo(),
        result.fullName(),
        result.registration().getCheckedInAt(),
        result.attended(),
        events.get(id).event().getCapacity());
  }

  @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(summary = "Upload the poster (JPG or PNG, max 5 MB)")
  public StaffEventSummary uploadImage(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable UUID id,
      @RequestPart("image") MultipartFile image)
      throws IOException {
    events.uploadImage(id, image.getInputStream(), image.getSize(), staffId(jwt));
    return StaffEventSummary.from(events.get(id));
  }

  @DeleteMapping("/{id}/image")
  @Operation(summary = "Remove the poster")
  public StaffEventSummary removeImage(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    events.removeImage(id, staffId(jwt));
    return StaffEventSummary.from(events.get(id));
  }

  @GetMapping(path = "/{id}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(summary = "The poster (drafts too)")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noCache())
        .contentType(MediaType.IMAGE_JPEG)
        .body(events.image(id, false));
  }

  private static UUID staffId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
