package tech.sioseforge.notification.resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.notification.entity.Notification;
import tech.sioseforge.notification.repository.NotificationRepository;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);
    private final NotificationRepository notificationRepository;

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @PostMapping("/send")
    public Notification sendNotification(@RequestBody NotificationRequest request) {
        Notification notification = new Notification(request.recipient(), request.message(), "PENDING", Instant.now());
        Notification saved = notificationRepository.save(notification);
        
        processAsync(saved);
        
        return saved;
    }

    @Async
    protected CompletableFuture<Void> processAsync(Notification notification) {
        log.info("Sending notification asynchronously to {}", notification.getRecipient());
        // Simulating delay
        try { Thread.sleep(500); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        
        notification.setStatus("SENT");
        notificationRepository.save(notification);
        log.info("Notification sent successfully");
        return CompletableFuture.completedFuture(null);
    }

    public record NotificationRequest(String recipient, String message) {}
}
