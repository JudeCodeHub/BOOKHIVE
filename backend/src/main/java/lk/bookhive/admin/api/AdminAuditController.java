package lk.bookhive.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.audit.AuditLog;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.audit.AuditService.AuditFilter;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The audit log for admins (ADM-5, checklist step 359): who did what and when, newest first,
 * filtered by person, kind of action, the thing it was done to, and dates. Read-only: entries can't
 * be changed (a database trigger refuses it).
 */
@RestController
@RequestMapping("/admin/audit")
@Tag(name = "Admin: audit log")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminAuditController {

  /**
   * One entry.
   *
   * @param actorId null for things nobody logged in did (e.g. a failed login, a nightly job)
   * @param actorName null when there's no actor or the account no longer exists
   * @param details extra facts, e.g. {"amount": "300.00", "reason": "..."}
   * @param requestId matches the server's log lines for the same request
   */
  public record AuditEntrySummary(
      UUID id,
      Instant occurredAt,
      @Schema(nullable = true) UUID actorId,
      @Schema(nullable = true) String actorName,
      @Schema(nullable = true) String actorEmail,
      String action,
      @Schema(nullable = true) String entityType,
      @Schema(nullable = true) String entityId,
      Map<String, Object> details,
      @Schema(nullable = true) String ip,
      @Schema(nullable = true) String requestId) {}

  private final AuditService audit;
  private final AuthService authService;

  public AdminAuditController(AuditService audit, AuthService authService) {
    this.audit = audit;
    this.authService = authService;
  }

  @GetMapping
  @Operation(
      summary =
          "Audit entries, newest first. action: exact (fine.waived) or a whole area ending in a"
              + " dot (fine.); from/to: days in Sri Lankan time, both inclusive")
  public PageResponse<AuditEntrySummary> search(
      @RequestParam(required = false) UUID actorId,
      @RequestParam(required = false) @Size(max = 60) String action,
      @RequestParam(required = false) @Size(max = 60) String entityType,
      @RequestParam(required = false) @Size(max = 100) String entityId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size) {
    if (from != null && to != null && to.isBefore(from)) {
      throw new FieldValidationException(
          "to", "RANGE_INVALID", "The end date is before the start.");
    }
    Page<AuditLog> entries =
        audit.search(new AuditFilter(actorId, action, entityType, entityId, from, to), page, size);
    // Names for the people on this page, in one query
    Map<UUID, User> people =
        authService.findUsers(
            entries.stream()
                .map(AuditLog::getActorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
    return PageResponse.from(
        entries,
        e -> {
          User actor = e.getActorId() == null ? null : people.get(e.getActorId());
          return new AuditEntrySummary(
              e.getId(),
              e.getOccurredAt(),
              e.getActorId(),
              actor == null ? null : actor.getFullName(),
              actor == null ? null : actor.getEmail(),
              e.getAction(),
              e.getEntityType(),
              e.getEntityId(),
              e.getDetails() == null ? Map.of() : e.getDetails(),
              e.getIp(),
              e.getRequestId());
        });
  }

  @GetMapping("/actions")
  @Operation(summary = "Every kind of action recorded so far, for the filter")
  public List<String> actions() {
    return audit.actions();
  }
}
