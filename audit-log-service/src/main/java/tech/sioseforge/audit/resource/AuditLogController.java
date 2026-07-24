package tech.sioseforge.audit.resource;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.sioseforge.audit.entity.AuditLog;
import tech.sioseforge.audit.repository.AuditLogRepository;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/audits")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping
    public AuditLog recordAudit(@RequestBody AuditRequest request) {
        AuditLog log = new AuditLog(request.tenantId(), request.action(), request.details(), Instant.now());
        return auditLogRepository.save(log);
    }

    @org.springframework.web.bind.annotation.GetMapping
    public java.util.List<AuditLog> getRecentLogs() {
        return auditLogRepository.findAll();
    }

    public record AuditRequest(String tenantId, String action, String details) {}
}
