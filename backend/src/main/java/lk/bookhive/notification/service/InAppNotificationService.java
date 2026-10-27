package lk.bookhive.notification.service;

import java.util.UUID;
import lk.bookhive.common.exception.NotFoundException;
import lk.bookhive.common.time.LibraryTime;
import lk.bookhive.notification.domain.InAppNotification;
import lk.bookhive.notification.repository.InAppNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading the bell (NOTIF-3, step 312): a user's messages, the unread count, marking read. */
@Service
public class InAppNotificationService {

  private final InAppNotificationRepository notifications;
  private final LibraryTime time;

  public InAppNotificationService(InAppNotificationRepository notifications, LibraryTime time) {
    this.notifications = notifications;
    this.time = time;
  }

  @Transactional(readOnly = true)
  public Page<InAppNotification> list(UUID userId, int page, int size) {
    return notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
  }

  @Transactional(readOnly = true)
  public long unreadCount(UUID userId) {
    return notifications.countByUserIdAndReadAtIsNull(userId);
  }

  /** Someone else's notification: not found, so ids can't be probed. */
  @Transactional
  public void markRead(UUID userId, UUID notificationId) {
    notifications
        .findByIdAndUserId(notificationId, userId)
        .orElseThrow(
            () -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found."))
        .markRead(time.now());
  }

  @Transactional
  public int markAllRead(UUID userId) {
    return notifications.markAllRead(userId, time.now());
  }
}
