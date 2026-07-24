package tech.sioseforge.notification.listener;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tech.sioseforge.notification.event.UserRegisteredEvent;

@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);
    private final Tracer tracer;

    public AuthEventListener(Tracer tracer) {
        this.tracer = tracer;
    }

    @KafkaListener(topics = "auth-events", groupId = "notification-group")
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        // Child of Kafka consumer observation (parent comes from producer traceparent header)
        Span span = tracer.spanBuilder("consume_auth_event")
                .setParent(io.opentelemetry.context.Context.current())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Received UserRegisteredEvent for user: {}. Sending welcome email to: {}", event.username(), event.email());
            
            // simulate sending email
            simulateDelay(200);
            span.addEvent("Welcome email sent");
            log.info("Welcome email successfully sent to {}", event.email());
        } catch (Exception e) {
            span.recordException(e);
            throw e;
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
