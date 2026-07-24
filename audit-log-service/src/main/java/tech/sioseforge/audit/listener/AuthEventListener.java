package tech.sioseforge.audit.listener;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tech.sioseforge.audit.entity.AuditLog;
import tech.sioseforge.audit.event.UserRegisteredEvent;
import tech.sioseforge.audit.repository.AuditLogRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;


@Component
public class AuthEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuthEventListener.class);
    private final AuditLogRepository repository;
    private final Tracer tracer;
    private final Counter eventProcessedCounter;

    public AuthEventListener(AuditLogRepository repository, Tracer tracer, MeterRegistry registry) {
        this.repository = repository;
        this.tracer = tracer;
        this.eventProcessedCounter = Counter.builder("audit.event.processed")
                .description("Number of auth events processed")
                .tag("service", "audit-log-service")
                .register(registry);
    }

    @KafkaListener(topics = "auth-events", groupId = "audit-log-group")
    public void handleUserRegisteredEvent(UserRegisteredEvent event) {
        Span span = tracer.spanBuilder("consume_auth_event")
                .setParent(io.opentelemetry.context.Context.current())
                .startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Received UserRegisteredEvent for user: {}", event.username());
            
            AuditLog auditLog = new AuditLog(
                "SYSTEM", 
                "USER_REGISTERED", 
                "Principal: " + event.username() + ", Email: " + event.email(), 
                java.time.Instant.now()
            );
            
            repository.save(auditLog);
            eventProcessedCounter.increment();
            span.addEvent("Audit log saved to database");
        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
