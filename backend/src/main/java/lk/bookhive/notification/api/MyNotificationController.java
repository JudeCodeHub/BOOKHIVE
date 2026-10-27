package lk.bookhive.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.common.domain.Language;
import lk.bookhive.common.web.PageResponse;
import lk.bookhive.notification.domain.InAppNotification;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.service.InAppNotificationService;
import lk.bookhive.notification.service.InAppTexts;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The logged-in user's in-app notifications (the bell, NOTIF-3, step 312). Texts come back in the
 * language the site is shown in ({@code ?lang=si}), whatever language they were created in.
 */
@RestController
@RequestMapping("/me/notifications")
@Tag(name = "My notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class MyNotificationController {

  private final InAppNotificationService notifications;
  private final InAppTexts texts;

  public MyNotificationController(InAppNotificationService notifications, InAppTexts texts) {
    this.notifications = notifications;
    this.texts = texts;
  }

  /**
   * @param link where it leads in the site, without the language (e.g. /account/holds)
   */
  public record NotificationSummary(
      UUID id,
      NotificationType type,
      String title,
      String body,
      String link,
      boolean read,
      Instant createdAt) {}

  public record UnreadCountResponse(long unread) {}

  public record MarkAllReadResponse(int marked) {}

  @GetMapping
  @Operation(summary = "My notifications, newest first, in the given language")
  public PageResponse<NotificationSummary> list(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam(defaultValue = "en") @Pattern(regexp = "en|si|ta") String lang,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    Language language = Language.fromCode(lang);
    return PageResponse.from(
        notifications.list(userId(jwt), page, size), n -> summary(n, language));
  }

  @GetMapping("/unread-count")
  @Operation(summary = "How many unread notifications (for the bell badge)")
  public UnreadCountResponse unreadCount(@AuthenticationPrincipal Jwt jwt) {
    return new UnreadCountResponse(notifications.unreadCount(userId(jwt)));
  }

  @PostMapping("/{id}/read")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Mark one notification as read")
  public void markRead(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    notifications.markRead(userId(jwt), id);
  }

  @PostMapping("/read-all")
  @Operation(summary = "Mark all my notifications as read")
  public MarkAllReadResponse markAllRead(@AuthenticationPrincipal Jwt jwt) {
    return new MarkAllReadResponse(notifications.markAllRead(userId(jwt)));
  }

  private NotificationSummary summary(InAppNotification n, Language language) {
    return new NotificationSummary(
        n.getId(),
        n.getType(),
        texts.title(n.getType(), language),
        texts.body(n.getType(), n.getParams(), language),
        n.getLink(),
        n.getReadAt() != null,
        n.getCreatedAt());
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
