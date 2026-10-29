package lk.bookhive.event.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.event.api.EventResponses.RegistrationSummary;
import lk.bookhive.event.service.EventRegistrationService;
import lk.bookhive.member.service.BorrowerService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in member's event sign-ups (EVT-2, step 332). */
@RestController
@RequestMapping("/me/events")
@Tag(name = "My events")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyEventController {

  private final EventRegistrationService signUps;
  private final BorrowerService borrowers;

  public MyEventController(EventRegistrationService signUps, BorrowerService borrowers) {
    this.signUps = signUps;
    this.borrowers = borrowers;
  }

  @GetMapping
  @Operation(summary = "My sign-ups (not given up), newest first, with QR codes for my places")
  public List<RegistrationSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return signUps.mine(memberId(jwt)).stream().map(RegistrationSummary::from).toList();
  }

  @PostMapping("/{eventId}/register")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Sign up: a place, or the waitlist when the event is full")
  public RegistrationSummary register(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
    return RegistrationSummary.from(signUps.register(eventId, memberId(jwt)));
  }

  @GetMapping("/{eventId}/registration")
  @Operation(summary = "My sign-up for this event (204 if I'm not signed up)")
  public ResponseEntity<RegistrationSummary> mine(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
    return signUps
        .current(eventId, memberId(jwt))
        .map(view -> ResponseEntity.ok(RegistrationSummary.from(view)))
        .orElseGet(() -> ResponseEntity.noContent().build());
  }

  @PostMapping("/{eventId}/cancel")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Give up my place or leave the waitlist (the next person moves up)")
  public void cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID eventId) {
    signUps.cancel(eventId, memberId(jwt));
  }

  private UUID memberId(Jwt jwt) {
    return borrowers.memberIdOfUser(UUID.fromString(jwt.getSubject()));
  }
}
