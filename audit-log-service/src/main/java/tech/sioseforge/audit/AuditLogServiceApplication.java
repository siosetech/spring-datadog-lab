package tech.sioseforge.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuditLogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuditLogServiceApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public io.opentelemetry.api.trace.Tracer openTelemetryTracer(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("audit-log-service");
    }
}
