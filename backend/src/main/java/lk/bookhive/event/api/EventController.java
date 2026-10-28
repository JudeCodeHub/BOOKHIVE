package lk.bookhive.event.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.event.api.EventResponses.EventSummary;
import lk.bookhive.event.service.EventAdminService;
import lk.bookhive.event.service.EventQueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Library events on the public website (EVT-1, steps 332, 341-342); no login needed. */
@RestController
@RequestMapping("/events")
@Tag(name = "Events")
public class EventController {

  /** A calendar view asks for a month or so; this stops very large ranges. */
  private static final Duration MAX_RANGE = Duration.ofDays(62);

  private final EventQueryService events;
  private final EventAdminService admin;
  private final LibraryTime time;

  public EventController(EventQueryService events, EventAdminService admin, LibraryTime time) {
    this.events = events;
    this.admin = admin;
    this.time = time;
  }

  @GetMapping
  @Operation(summary = "Upcoming and ongoing events, soonest first")
  public PageResponse<EventSummary> upcoming(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
    Instant now = time.now();
    return PageResponse.from(events.upcoming(page, size), row -> EventSummary.from(row, now));
  }

  @GetMapping("/calendar")
  @Operation(summary = "Events starting between from and to (at most about two months)")
  public List<EventSummary> calendar(@RequestParam Instant from, @RequestParam Instant to) {
    if (!to.isAfter(from) || Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
      throw new FieldValidationException(
          "to", "RANGE_INVALID", "Ask for a range of up to two months.");
    }
    Instant now = time.now();
    return events.between(from, to).stream().map(row -> EventSummary.from(row, now)).toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "One event with places left (cancelled ones show the reason)")
  public EventSummary get(@PathVariable UUID id) {
    return EventSummary.from(events.get(id), time.now());
  }

  @GetMapping(path = "/{id}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  @Operation(summary = "The event poster; cache it (the URL changes with ?v= when it changes)")
  public ResponseEntity<byte[]> image(@PathVariable UUID id) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic().immutable())
        .contentType(MediaType.IMAGE_JPEG)
        .body(admin.image(id, true));
  }
}
