package zw.ac.msu.dzimbahub.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.ac.msu.dzimbahub.model.Message;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {
    List<Message> findBySenderIdOrRecipientIdOrderByCreatedAtDesc(Long senderId, Long recipientId);
    void deleteByPropertyId(Long propertyId);
}
