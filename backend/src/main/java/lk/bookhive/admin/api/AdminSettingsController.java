package lk.bookhive.admin.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.admin.settings.Settings.Group;
import lk.bookhive.admin.settings.Settings.Holds;
import lk.bookhive.admin.settings.Settings.Loans;
import lk.bookhive.admin.settings.Settings.Membership;
import lk.bookhive.admin.settings.Settings.Notifications;
import lk.bookhive.admin.settings.SettingsService;
import lk.bookhive.admin.settings.SettingsService.Stored;
import lk.bookhive.auth.domain.User;
import lk.bookhive.auth.service.AuthService;
import lk.bookhive.common.config.OpenApiConfig;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Library-wide settings for admins (ADM-1, checklist step 356): loan rules, fines, holds,
 * membership and reminder timing. Each group is saved on its own, with the version it was read at,
 * so two admins editing at once can't silently overwrite each other (SETTINGS_CHANGED). Changes
 * apply within 30 seconds on every server and are written to the audit log.
 */
@RestController
@RequestMapping("/admin/settings")
@Tag(name = "Admin: settings")
@SecurityRequirement(name = OpenApiConfig.BEARER)
public class AdminSettingsController {

  /**
   * When a group was last changed.
   *
   * @param version send it back when saving this group
   * @param updatedBy null when it still has the values it was installed with
   */
  public record SettingChangeSummary(
      long version,
      Instant updatedAt,
      @Schema(nullable = true) UUID updatedBy,
      @Schema(nullable = true) String updatedByName) {}

  /**
   * All settings, by group.
   *
   * @param changes keyed by group: loans, fines, holds, membership, notifications
   */
  public record SettingsResponse(
      Loans loans,
      Fines fines,
      Holds holds,
      Membership membership,
      Notifications notifications,
      Map<String, SettingChangeSummary> changes) {}

  private final SettingsService settings;
  private final AuthService authService;

  public AdminSettingsController(SettingsService settings, AuthService authService) {
    this.settings = settings;
    this.authService = authService;
  }

  @GetMapping
  @Operation(summary = "All library settings, with who changed each group last")
  public SettingsResponse all() {
    Stored<Loans> loans = settings.stored("loans", Loans.class);
    Stored<Fines> fines = settings.stored("fines", Fines.class);
    Stored<Holds> holds = settings.stored("holds", Holds.class);
    Stored<Membership> membership = settings.stored("membership", Membership.class);
    Stored<Notifications> notifications = settings.stored("notifications", Notifications.class);

    List<Stored<?>> groups = List.of(loans, fines, holds, membership, notifications);
    Map<UUID, User> admins =
        authService.findUsers(
            groups.stream().map(Stored::updatedBy).filter(Objects::nonNull).toList());
    Map<String, SettingChangeSummary> changes = new LinkedHashMap<>();
    for (Stored<?> group : groups) {
      User admin = group.updatedBy() == null ? null : admins.get(group.updatedBy());
      changes.put(
          group.value().key(),
          new SettingChangeSummary(
              group.version(),
              group.updatedAt(),
              group.updatedBy(),
              admin == null ? null : admin.getFullName()));
    }
    return new SettingsResponse(
        loans.value(),
        fines.value(),
        holds.value(),
        membership.value(),
        notifications.value(),
        changes);
  }

  @PutMapping("/loans")
  @Operation(summary = "Save the loan rules (version: from the last read; 409 if it changed)")
  public SettingsResponse loans(
      @AuthenticationPrincipal Jwt jwt, @RequestParam long version, @RequestBody Loans body) {
    return save(body, version, jwt);
  }

  @PutMapping("/fines")
  @Operation(summary = "Save the fine rules (amounts in LKR)")
  public SettingsResponse fines(
      @AuthenticationPrincipal Jwt jwt, @RequestParam long version, @RequestBody Fines body) {
    return save(body, version, jwt);
  }

  @PutMapping("/holds")
  @Operation(summary = "Save the hold rules")
  public SettingsResponse holds(
      @AuthenticationPrincipal Jwt jwt, @RequestParam long version, @RequestBody Holds body) {
    return save(body, version, jwt);
  }

  @PutMapping("/membership")
  @Operation(summary = "Save the membership rules")
  public SettingsResponse membership(
      @AuthenticationPrincipal Jwt jwt, @RequestParam long version, @RequestBody Membership body) {
    return save(body, version, jwt);
  }

  @PutMapping("/notifications")
  @Operation(summary = "Save when reminders go out")
  public SettingsResponse notifications(
      @AuthenticationPrincipal Jwt jwt,
      @RequestParam long version,
      @RequestBody Notifications body) {
    return save(body, version, jwt);
  }

  private SettingsResponse save(Group group, long version, Jwt jwt) {
    settings.update(group, version, UUID.fromString(jwt.getSubject()));
    return all();
  }
}
