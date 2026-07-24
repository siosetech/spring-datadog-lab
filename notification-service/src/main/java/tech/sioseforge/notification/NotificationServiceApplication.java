package tech.sioseforge.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableKafka
@EnableAsync
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public io.opentelemetry.api.trace.Tracer openTelemetryTracer(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("notification-service");
    }
}
