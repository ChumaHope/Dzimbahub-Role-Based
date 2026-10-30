package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.Booking;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<Booking> findByPropertyIdInOrderByCreatedAtDesc(Collection<Long> propertyIds);
    boolean existsByPropertyIdAndTenantId(Long propertyId, Long tenantId);
    void deleteByPropertyId(Long propertyId);
    Optional<Booking> findFirstByPropertyIdAndStatusOrderByCreatedAtDesc(Long propertyId, zw.ac.msu.dzimbahub.model.BookingStatus status);
}
