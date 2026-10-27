package lk.bookhive.notification.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lk.bookhive.notification.domain.NotificationPreference;
import lk.bookhive.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository
    extends JpaRepository<NotificationPreference, UUID> {

  List<NotificationPreference> findByUserId(UUID userId);

  Optional<NotificationPreference> findByUserIdAndType(UUID userId, NotificationType type);
}
