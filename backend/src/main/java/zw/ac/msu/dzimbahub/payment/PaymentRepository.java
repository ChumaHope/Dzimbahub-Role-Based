package zw.ac.msu.dzimbahub.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByReference(String reference);
    List<Payment> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
}
