package zw.ac.msu.dzimbahub.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(UserRole role);
    List<AppUser> findAllByOrderByCreatedAtDesc();
    List<AppUser> findByStatusOrderByCreatedAtAsc(UserStatus status);
    long countByStatus(UserStatus status);
    long countByRole(UserRole role);
}
