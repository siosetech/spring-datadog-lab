package tech.sioseforge.dashboard.service;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tech.sioseforge.dashboard.client.AuditLogClient;
import tech.sioseforge.dashboard.client.UserProfileClient;

import java.util.Map;

@Service
public class AggregationService {

    private static final Logger log = LoggerFactory.getLogger(AggregationService.class);
    private final UserProfileClient userProfileClient;
    private final AuditLogClient auditLogClient;

    public AggregationService(UserProfileClient userProfileClient, AuditLogClient auditLogClient) {
        this.userProfileClient = userProfileClient;
        this.auditLogClient = auditLogClient;
    }

    @CircuitBreaker(name = "userProfileService", fallbackMethod = "fallbackProfileStats")
    @Retry(name = "userProfileService")
    public Map<String, Object> getProfileStats() {
        log.info("Fetching profile stats from user-profile-service...");
        return userProfileClient.getProfileStats();
    }

    public Map<String, Object> fallbackProfileStats(Exception e) {
        log.warn("Fallback triggered for user-profile-service due to: {}", e.getMessage());
        return Map.of(
                "totalProfiles", "N/A",
                "activeProfiles", "N/A",
                "service", "user-profile-service (fallback)",
                "status", "DOWN"
        );
    }

    @CircuitBreaker(name = "auditLogService", fallbackMethod = "fallbackAuditStats")
    @Retry(name = "auditLogService")
    public Map<String, Object> getAuditStats() {
        log.info("Fetching audit stats from audit-log-service...");
        java.util.List<Object> logs = auditLogClient.getRecentLogs();
        return Map.of(
            "totalAuditLogs", logs.size(),
            "service", "audit-log-service",
            "status", "UP"
        );
    }

    public Map<String, Object> fallbackAuditStats(Exception e) {
        log.warn("Fallback triggered for audit-log-service due to: {}", e.getMessage());
        return Map.of(
                "totalAuditLogs", "N/A",
                "service", "audit-log-service (fallback)",
                "status", "DOWN"
        );
    }
}
