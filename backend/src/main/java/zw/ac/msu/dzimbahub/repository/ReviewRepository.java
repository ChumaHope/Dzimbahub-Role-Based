package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.Review;
import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    List<Review> findByPropertyIdOrderByCreatedAtDesc(Long propertyId);
    boolean existsByPropertyIdAndTenantId(Long propertyId, Long tenantId);
    void deleteByPropertyId(Long propertyId);
}
