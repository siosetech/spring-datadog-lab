package tech.sioseforge.profile.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.sioseforge.profile.view.ProfileResponseVO;

import java.time.Instant;
import java.util.Map;

@Service
public class DefaultProfileService {

    private static final Logger log = LoggerFactory.getLogger(DefaultProfileService.class);
    
    private final Tracer tracer;
    private final Counter statsFetchedCounter;

    public DefaultProfileService(Tracer tracer, MeterRegistry registry) {
        this.tracer = tracer;
        this.statsFetchedCounter = Counter.builder("profile.stats.fetched")
                .description("Number of times profile stats were fetched")
                .tag("service", "user-profile-service")
                .register(registry);
    }

    public Map<String, Object> getProfileStats() {
        Span span = tracer.spanBuilder("db_fetch_profile_stats").startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Fetching profile statistics from mock database...");
            
            // Simulate DB latency to demonstrate APM tracing delays
            Thread.sleep(150);
            
            statsFetchedCounter.increment();
            span.addEvent("Successfully fetched mock stats");
            
            return Map.of(
                "totalUsers", 42,
                "activeSessions", 12,
                "status", "HEALTHY"
            );
        } catch (InterruptedException e) {
            span.recordException(e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted during DB fetch", e);
        } finally {
            span.end();
        }
    }

    public ProfileResponseVO getProfileByUserId(Long userId) {
        Span span = tracer.spanBuilder("db_fetch_profile").startSpan();
        try (var scope = span.makeCurrent()) {
            log.info("Fetching profile for userId={}", userId);
            return new ProfileResponseVO(
                userId,
                "user-" + userId,
                "ACTIVE",
                Instant.now().toString()
            );
        } finally {
            span.end();
        }
    }
}
