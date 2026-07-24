package tech.sioseforge.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.auth.domain.entity.Dashboard;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {
}
