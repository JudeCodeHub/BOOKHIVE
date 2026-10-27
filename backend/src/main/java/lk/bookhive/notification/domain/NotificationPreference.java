package lk.bookhive.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import lk.bookhive.common.domain.BaseEntity;

/**
 * A member's channel choice for one optional notification type (table {@code
 * notification_preferences}, NOTIF-5, step 313). A channel the type doesn't use can't be switched
 * on here: the choices only ever narrow the type's defaults.
 */
@Entity
@Table(name = "notification_preferences")
public class NotificationPreference extends BaseEntity {

  @Column(nullable = false, updatable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40, updatable = false)
  private NotificationType type;

  @Column(nullable = false)
  private boolean email;

  @Column(nullable = false)
  private boolean sms;

  @Column(nullable = false)
  private boolean inApp;

  protected NotificationPreference() {
    // for JPA
  }

  public NotificationPreference(UUID userId, NotificationType type) {
    if (!type.optional()) {
      throw new IllegalArgumentException(type + " can't be switched off");
    }
    this.userId = userId;
    this.type = type;
    set(type.defaultChannels());
  }

  /** Keeps only the channels the type actually uses. */
  public void set(Set<Channel> wanted) {
    Set<Channel> allowed = type.defaultChannels();
    this.email = wanted.contains(Channel.EMAIL) && allowed.contains(Channel.EMAIL);
    this.sms = wanted.contains(Channel.SMS) && allowed.contains(Channel.SMS);
    this.inApp = wanted.contains(Channel.IN_APP) && allowed.contains(Channel.IN_APP);
  }

  public Set<Channel> channels() {
    Set<Channel> channels = EnumSet.noneOf(Channel.class);
    if (email) {
      channels.add(Channel.EMAIL);
    }
    if (sms) {
      channels.add(Channel.SMS);
    }
    if (inApp) {
      channels.add(Channel.IN_APP);
    }
    return channels;
  }

  public UUID getUserId() {
    return userId;
  }

  public NotificationType getType() {
    return type;
  }
}
