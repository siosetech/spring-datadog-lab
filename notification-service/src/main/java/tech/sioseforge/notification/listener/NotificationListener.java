package tech.sioseforge.notification.listener;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import tech.sioseforge.notification.event.UserRegisteredEvent;

@Service
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final Tracer tracer;

    public NotificationListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @KafkaListener(topics = "user-registered-topic", groupId = "notification-group")
    public void handleUserRegistered(UserRegisteredEvent event) {
        Span span = tracer.spanBuilder("handle_user_registered_event")
                .setParent(io.opentelemetry.context.Context.current())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Received UserRegisteredEvent: Sending welcome email to {} ({})", event.username(), event.email());
            
            // Simulate sending email
            simulateDelay(100);
            
            span.addEvent("Welcome email sent successfully");
            log.info("Welcome email sent to {}", event.email());
        } catch (Exception e) {
            span.recordException(e);
            log.error("Failed to process event", e);
        } finally {
            span.end();
        }
    }

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
