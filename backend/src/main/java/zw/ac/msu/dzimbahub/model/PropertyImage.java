package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A photo uploaded by a landlord from their phone or computer. The bytes are kept in the database
 * (Render's disk is wiped on every deploy, the database is not). Photos are resized in the browser
 * before upload, so each one is usually 150-400 KB.
 */
@Entity
@Table(name = "property_images")
public class PropertyImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long ownerId;
    @Column(nullable = false, length = 40) private String contentType;
    @Column(nullable = false) private int sizeBytes;
    /** No @Lob: on PostgreSQL a plain byte[] becomes a bytea column. */
    @Column(nullable = false, length = 6_000_000) private byte[] data;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected PropertyImage() {}
    public PropertyImage(Long ownerId, String contentType, byte[] data) {
        this.ownerId = ownerId; this.contentType = contentType; this.data = data; this.sizeBytes = data.length;
    }
    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public byte[] getData() { return data; }
    public Instant getCreatedAt() { return createdAt; }
}
