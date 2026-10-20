package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.Property;
import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {
    List<Property> findAllByOrderByRatingDesc();
    List<Property> findByOwnerIdOrderByIdDesc(Long ownerId);
    List<Property> findAllByOrderByIdDesc();
}
