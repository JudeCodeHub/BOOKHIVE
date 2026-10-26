package lk.bookhive.admin.settings;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import lk.bookhive.admin.domain.LibrarySetting;
import lk.bookhive.admin.repository.LibrarySettingRepository;
import lk.bookhive.admin.settings.Settings.Fines;
import lk.bookhive.admin.settings.Settings.Group;
import lk.bookhive.admin.settings.Settings.Holds;
import lk.bookhive.admin.settings.Settings.Membership;
import lk.bookhive.admin.settings.Settings.Notifications;
import lk.bookhive.common.audit.AuditService;
import lk.bookhive.common.exception.FieldValidationException;
import lk.bookhive.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Library-wide settings, typed and cached (checklist step 216). Read on every checkout and return,
 * so they're kept in memory and re-read from the database at most every 30 seconds. A change made
 * here clears the cache straight away (after commit); other servers pick it up within those 30
 * seconds.
 *
 * <pre>settings.fines().perDay()</pre>
 */
@Service
public class SettingsService {

  /** How stale another server's copy can be after an admin change. */
  private static final Duration TTL = Duration.ofSeconds(30);

  private record Cached(Group value, Instant loadedAt) {}

  private final LibrarySettingRepository settings;
  private final JsonMapper json;
  private final AuditService audit;
  private final Clock clock;
  private final Map<String, Cached> cache = new ConcurrentHashMap<>();

  public SettingsService(
      LibrarySettingRepository settings, JsonMapper json, AuditService audit, Clock clock) {
    this.settings = settings;
    this.json = json;
    this.audit = audit;
    this.clock = clock;
  }

  public Fines fines() {
    return get("fines", Fines.class);
  }

  public Holds holds() {
    return get("holds", Holds.class);
  }

  public Membership membership() {
    return get("membership", Membership.class);
  }

  public Notifications notifications() {
    return get("notifications", Notifications.class);
  }

  /**
   * Saves a whole group (admin settings page, step 406). Values are checked first; a bad one is
   * reported on its field.
   */
  @Transactional
  public <T extends Group> T update(T group, UUID adminId) {
    try {
      group.check();
    } catch (IllegalArgumentException e) {
      throw new FieldValidationException(
          e.getMessage(), "SETTING_OUT_OF_RANGE", "This value is outside the allowed range.");
    }
    LibrarySetting row =
        settings
            .findById(group.key())
            .orElseThrow(() -> new NotFoundException("SETTING_NOT_FOUND", "Unknown setting."));
    row.change(json.writeValueAsString(group), adminId);
    audit.record(
        "settings.updated",
        adminId,
        "library_settings",
        group.key(),
        Map.of("value", json.writeValueAsString(group)));
    evictAfterCommit(group.key());
    return group;
  }

  @SuppressWarnings("unchecked")
  private <T extends Group> T get(String key, Class<T> type) {
    Cached cached = cache.get(key);
    if (cached != null && cached.loadedAt().plus(TTL).isAfter(clock.instant())) {
      return (T) cached.value();
    }
    T value = load(key, type);
    cache.put(key, new Cached(value, clock.instant()));
    return value;
  }

  private <T extends Group> T load(String key, Class<T> type) {
    Supplier<IllegalStateException> missing =
        () -> new IllegalStateException("library_settings row '" + key + "' is missing");
    LibrarySetting row = settings.findById(key).orElseThrow(missing);
    return json.readValue(row.getValue(), type);
  }

  private void evictAfterCommit(String key) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              cache.remove(key);
            }
          });
    } else {
      cache.remove(key);
    }
  }
}
