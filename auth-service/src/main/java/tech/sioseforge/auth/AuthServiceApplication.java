package tech.sioseforge.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

    @org.springframework.context.annotation.Bean
    public io.opentelemetry.api.trace.Tracer openTelemetryTracer(io.opentelemetry.api.OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("auth-service");
    }
}
