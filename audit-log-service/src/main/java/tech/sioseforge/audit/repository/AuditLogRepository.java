package tech.sioseforge.audit.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.audit.entity.AuditLog;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
