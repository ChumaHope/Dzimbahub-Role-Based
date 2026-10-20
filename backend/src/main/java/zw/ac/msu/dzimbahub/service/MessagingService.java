package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.EnquiryRequest;
import zw.ac.msu.dzimbahub.dto.Dtos.MessageView;
import zw.ac.msu.dzimbahub.model.Message;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.MessageRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import java.util.List;

/** Tenant to landlord enquiries and the replies between them. */
@Service
public class MessagingService {
    private final MessageRepository messages;
    private final PropertyRepository properties;
    private final CurrentUsers current;
    private final AuditService audit;

    public MessagingService(MessageRepository messages, PropertyRepository properties, CurrentUsers current, AuditService audit) {
        this.messages = messages; this.properties = properties; this.current = current; this.audit = audit;
    }

    MessageView view(Message m, Long viewerId) {
        String title = properties.findById(m.getPropertyId()).map(Property::getTitle).orElse("Removed listing");
        return new MessageView(m.getId(), m.getPropertyId(), title, m.getSenderId(), current.nameOf(m.getSenderId()),
                m.getRecipientId(), current.nameOf(m.getRecipientId()), m.getBody(), m.getCreatedAt(), m.getSenderId().equals(viewerId));
    }

    @Transactional
    public MessageView enquire(String tenantEmail, EnquiryRequest r) {
        AppUser tenant = current.require(tenantEmail);
        Property p = properties.findById(r.propertyId()).filter(Property::isActive).orElseThrow(ListingService::notFound);
        if (p.getOwnerId() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This listing has no registered landlord to receive messages.");
        Message m = messages.save(new Message(p.getId(), tenant.getId(), p.getOwnerId(), r.message().trim()));
        audit.record(tenantEmail, "ENQUIRY_SENT", "Message", m.getId(), p.getTitle());
        return view(m, tenant.getId());
    }

    public List<MessageView> forUser(String email) {
        AppUser me = current.require(email);
        return messages.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(me.getId(), me.getId()).stream().map(m -> view(m, me.getId())).toList();
    }

    /** Reply to a message that was sent to the signed-in user. */
    @Transactional
    public MessageView reply(String email, Long messageId, String text) {
        AppUser me = current.require(email);
        Message original = messages.findById(messageId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found."));
        if (!original.getRecipientId().equals(me.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only reply to messages sent to you.");
        Message m = messages.save(new Message(original.getPropertyId(), me.getId(), original.getSenderId(), text.trim()));
        audit.record(email, "MESSAGE_REPLIED", "Message", m.getId(), null);
        return view(m, me.getId());
    }
}
