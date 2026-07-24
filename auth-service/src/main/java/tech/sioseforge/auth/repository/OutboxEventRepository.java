package tech.sioseforge.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.auth.domain.entity.OutboxEvent;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
}
