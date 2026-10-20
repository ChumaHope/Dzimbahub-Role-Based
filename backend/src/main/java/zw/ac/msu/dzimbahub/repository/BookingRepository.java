package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.Booking;
import java.util.Collection;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<Booking> findByPropertyIdInOrderByCreatedAtDesc(Collection<Long> propertyIds);
    boolean existsByPropertyIdAndTenantId(Long propertyId, Long tenantId);
    void deleteByPropertyId(Long propertyId);
}
