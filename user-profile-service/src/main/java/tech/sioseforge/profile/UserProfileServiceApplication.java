package tech.sioseforge.profile;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class UserProfileServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(UserProfileServiceApplication.class, args);
    }

    @Bean
    public Tracer openTelemetryTracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer("user-profile-service");
    }
}
