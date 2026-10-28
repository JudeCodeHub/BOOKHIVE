package lk.bookhive.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.domain.OutboxMessage;
import lk.bookhive.notification.domain.OutboxStatus;
import lk.bookhive.notification.service.OutboxAdminService;
import lk.bookhive.notification.service.OutboxAdminService.OutboxCounts;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Message delivery for admins (step 319): failed emails/SMS and sending them again. */
@RestController
@RequestMapping("/admin/notifications/messages")
@Tag(name = "Admin: notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminOutboxController {

  private final OutboxAdminService outbox;

  public AdminOutboxController(OutboxAdminService outbox) {
    this.outbox = outbox;
  }

  /**
   * One queued, sent or failed message (the body isn't included: it can hold reset links).
   *
   * @param lastError why the last try failed
   * @param nextAttemptAt PENDING: when it will be tried next
   */
  public record OutboxMessageSummary(
      UUID id,
      Channel channel,
      NotificationType type,
      String recipient,
      String subject,
      OutboxStatus status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant updatedAt,
      Instant nextAttemptAt,
      Instant sentAt) {

    static OutboxMessageSummary from(OutboxMessage m) {
      return new OutboxMessageSummary(
          m.getId(),
          m.getChannel(),
          m.getNotificationType(),
          m.getRecipient(),
          m.getSubject(),
          m.getStatus(),
          m.getAttempts(),
          m.getLastError(),
          m.getCreatedAt(),
          m.getUpdatedAt(),
          m.getNextAttemptAt(),
          m.getSentAt());
    }
  }

  public record OutboxCountsResponse(long pending, long sent, long failed) {

    static OutboxCountsResponse from(OutboxCounts c) {
      return new OutboxCountsResponse(c.pending(), c.sent(), c.failed());
    }
  }

  public record RetryAllResponse(int requeued) {}

  @GetMapping("/counts")
  @Operation(summary = "How many messages wait, went out, or failed")
  public OutboxCountsResponse counts() {
    return OutboxCountsResponse.from(outbox.counts());
  }

  @GetMapping
  @Operation(summary = "Messages in a status (FAILED by default), most recently changed first")
  public PageResponse<OutboxMessageSummary> list(
      @RequestParam(defaultValue = "FAILED") OutboxStatus status,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    return PageResponse.from(outbox.list(status, page, size), OutboxMessageSummary::from);
  }

  @PostMapping("/{id}/retry")
  @Operation(summary = "Send a failed message again (fresh set of tries)")
  public OutboxMessageSummary retry(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return OutboxMessageSummary.from(outbox.retry(id, UUID.fromString(jwt.getSubject())));
  }

  @PostMapping("/retry-failed")
  @Operation(summary = "Send every failed message again")
  public RetryAllResponse retryAll(@AuthenticationPrincipal Jwt jwt) {
    return new RetryAllResponse(outbox.retryAllFailed(UUID.fromString(jwt.getSubject())));
  }
}
