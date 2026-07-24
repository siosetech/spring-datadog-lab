package tech.sioseforge.audit.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String action;
    private String details;
    private Instant timestamp;

    public AuditLog() {}

    public AuditLog(String tenantId, String action, String details, Instant timestamp) {
        this.tenantId = tenantId;
        this.action = action;
        this.details = details;
        this.timestamp = timestamp;
    }

    // Getters and Setters omitted for brevity but standard
    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public Instant getTimestamp() { return timestamp; }
}
