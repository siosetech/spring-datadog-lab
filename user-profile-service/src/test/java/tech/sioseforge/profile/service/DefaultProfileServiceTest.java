package tech.sioseforge.profile.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.GlobalOpenTelemetry;
import org.junit.jupiter.api.Test;
import tech.sioseforge.profile.view.ProfileResponseVO;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultProfileServiceTest {

    private final DefaultProfileService profileService = new DefaultProfileService(
        GlobalOpenTelemetry.getTracer("test-tracer"),
        new SimpleMeterRegistry()
    );

    @Test
    void shouldReturnProfileByUserId() {
        ProfileResponseVO profile = profileService.getProfileByUserId(42L);

        assertThat(profile.userId()).isEqualTo(42L);
        assertThat(profile.username()).isEqualTo("user-42");
        assertThat(profile.status()).isEqualTo("ACTIVE");
        assertThat(profile.lastUpdatedAt()).isNotBlank();
    }

    @Test
    void shouldReturnProfileStats() {
        Map<String, Object> stats = profileService.getProfileStats();

        assertThat(stats).containsKeys("totalUsers", "activeSessions", "status");
        assertThat(stats.get("status")).isEqualTo("HEALTHY");
    }
}
