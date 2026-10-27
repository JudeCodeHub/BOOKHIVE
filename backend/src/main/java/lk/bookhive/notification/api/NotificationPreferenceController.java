package lk.bookhive.notification.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.common.config.OpenApiConfig;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.service.NotificationPreferenceService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Which messages I get, and how (NOTIF-5, step 313). Only types that can be switched off. */
@RestController
@RequestMapping("/me/notification-preferences")
@Tag(name = "My notifications")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class NotificationPreferenceController {

  private final NotificationPreferenceService preferences;

  public NotificationPreferenceController(NotificationPreferenceService preferences) {
    this.preferences = preferences;
  }

  /**
   * One notification type.
   *
   * @param available the channels this type can use (offer only these)
   * @param email on/off; null when the type doesn't use email (same for sms, inApp)
   */
  public record PreferenceSummary(
      NotificationType type, List<Channel> available, Boolean email, Boolean sms, Boolean inApp) {}

  public record PreferenceChoice(
      @NotNull NotificationType type, boolean email, boolean sms, boolean inApp) {}

  public record PreferencesRequest(
      @NotNull @Size(max = 50) List<@Valid PreferenceChoice> choices) {}

  @GetMapping
  @Operation(summary = "My channel choices per optional notification type")
  public List<PreferenceSummary> mine(@AuthenticationPrincipal Jwt jwt) {
    return summaries(preferences.forMember(userId(jwt)));
  }

  @PutMapping
  @Operation(summary = "Save my channel choices (types that can't be switched off are ignored)")
  public List<PreferenceSummary> update(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PreferencesRequest request) {
    Map<NotificationType, Set<Channel>> choices = new LinkedHashMap<>();
    for (PreferenceChoice choice : request.choices()) {
      Set<Channel> channels = EnumSet.noneOf(Channel.class);
      if (choice.email()) {
        channels.add(Channel.EMAIL);
      }
      if (choice.sms()) {
        channels.add(Channel.SMS);
      }
      if (choice.inApp()) {
        channels.add(Channel.IN_APP);
      }
      choices.put(choice.type(), channels);
    }
    return summaries(preferences.update(userId(jwt), choices));
  }

  private static List<PreferenceSummary> summaries(Map<NotificationType, Set<Channel>> all) {
    return all.entrySet().stream()
        .map(
            entry -> {
              Set<Channel> available = entry.getKey().defaultChannels();
              Set<Channel> on = entry.getValue();
              return new PreferenceSummary(
                  entry.getKey(),
                  available.stream().sorted().toList(),
                  available.contains(Channel.EMAIL) ? on.contains(Channel.EMAIL) : null,
                  available.contains(Channel.SMS) ? on.contains(Channel.SMS) : null,
                  available.contains(Channel.IN_APP) ? on.contains(Channel.IN_APP) : null);
            })
        .toList();
  }

  private static UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }
}
