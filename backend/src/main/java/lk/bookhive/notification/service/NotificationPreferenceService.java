package lk.bookhive.notification.service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.notification.domain.Channel;
import lk.bookhive.notification.domain.NotificationPreference;
import lk.bookhive.notification.domain.NotificationType;
import lk.bookhive.notification.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which channels a member gets each notification type on (NOTIF-5, step 313). Types that can't be
 * switched off (security, receipts) always use their defaults; for the others the member's choice
 * applies, or the defaults when they never changed it.
 */
@Service
public class NotificationPreferenceService {

  private final NotificationPreferenceRepository preferences;

  public NotificationPreferenceService(NotificationPreferenceRepository preferences) {
    this.preferences = preferences;
  }

  /** The channels to use for this member and type right now. */
  @Transactional(readOnly = true)
  public Set<Channel> channelsFor(UUID userId, NotificationType type) {
    if (!type.optional()) {
      return type.defaultChannels();
    }
    return preferences
        .findByUserIdAndType(userId, type)
        .map(NotificationPreference::channels)
        .orElse(type.defaultChannels());
  }

  /** Every optional type with the member's current choice, in a stable order. */
  @Transactional(readOnly = true)
  public Map<NotificationType, Set<Channel>> forMember(UUID userId) {
    Map<NotificationType, Set<Channel>> stored = new LinkedHashMap<>();
    preferences.findByUserId(userId).forEach(p -> stored.put(p.getType(), p.channels()));
    Map<NotificationType, Set<Channel>> all = new LinkedHashMap<>();
    for (NotificationType type : NotificationType.values()) {
      if (type.optional()) {
        all.put(type, stored.getOrDefault(type, type.defaultChannels()));
      }
    }
    return all;
  }

  /**
   * Saves the member's choices. Types that can't be switched off, and channels a type doesn't use,
   * are ignored rather than refused (the page only offers what's possible anyway).
   */
  @Transactional
  public Map<NotificationType, Set<Channel>> update(
      UUID userId, Map<NotificationType, Set<Channel>> choices) {
    choices.forEach(
        (type, channels) -> {
          if (!type.optional()) {
            return;
          }
          NotificationPreference preference =
              preferences
                  .findByUserIdAndType(userId, type)
                  .orElseGet(() -> preferences.save(new NotificationPreference(userId, type)));
          preference.set(channels == null ? EnumSet.noneOf(Channel.class) : channels);
        });
    return forMember(userId);
  }
}
