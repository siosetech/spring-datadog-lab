package tech.sioseforge.audit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.audit.repository.AuditLogRepository;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditLogRepository repository;

    public AuditController(AuditLogRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/stats")
    public Map<String, Object> getAuditStats() {
        long count = repository.count();
        return Map.of(
                "totalAuditLogs", count,
                "service", "audit-log-service"
        );
    }
}
