package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.RequestStatus;
import zw.ac.msu.dzimbahub.model.Viewing;
import java.util.Collection;
import java.util.List;

public interface ViewingRepository extends JpaRepository<Viewing, Long> {
    List<Viewing> findByTenantIdOrderByCreatedAtDesc(Long tenantId);
    List<Viewing> findByPropertyIdInOrderByCreatedAtDesc(Collection<Long> propertyIds);
    boolean existsByPropertyIdAndTenantIdAndStatus(Long propertyId, Long tenantId, RequestStatus status);
    void deleteByPropertyId(Long propertyId);
}
