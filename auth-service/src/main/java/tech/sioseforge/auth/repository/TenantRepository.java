package tech.sioseforge.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.auth.domain.entity.Tenant;
import java.util.Optional;

public interface TenantRepository extends JpaRepository<Tenant, Long> {
    Optional<Tenant> findByDomain(String domain);
}
