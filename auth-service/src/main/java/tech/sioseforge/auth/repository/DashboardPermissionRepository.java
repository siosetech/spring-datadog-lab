package tech.sioseforge.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.auth.domain.entity.DashboardPermission;
import java.util.List;

public interface DashboardPermissionRepository extends JpaRepository<DashboardPermission, Long> {
    List<DashboardPermission> findByUserId(Long userId);
}
