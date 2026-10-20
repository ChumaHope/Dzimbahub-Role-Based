package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "messages")
public class Message {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long propertyId;
    @Column(nullable = false) private Long senderId;
    @Column(nullable = false) private Long recipientId;
    @Column(nullable = false, length = 1000) private String body;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected Message() {}
    public Message(Long propertyId, Long senderId, Long recipientId, String body) {
        this.propertyId = propertyId; this.senderId = senderId; this.recipientId = recipientId; this.body = body;
    }
    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getSenderId() { return senderId; }
    public Long getRecipientId() { return recipientId; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
