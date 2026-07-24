package tech.sioseforge.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.notification.entity.Notification;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
