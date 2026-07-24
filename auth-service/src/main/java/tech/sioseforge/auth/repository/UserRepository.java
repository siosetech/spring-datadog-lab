package tech.sioseforge.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tech.sioseforge.auth.domain.entity.User;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findBySsoId(String ssoId);
}
