package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reviews", uniqueConstraints = @UniqueConstraint(name = "uk_review_property_tenant", columnNames = {"propertyId", "tenantId"}))
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long propertyId;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private int rating;
    @Column(name = "review_text", length = 1000) private String comment;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected Review() {}
    public Review(Long propertyId, Long tenantId, int rating, String comment) {
        this.propertyId = propertyId; this.tenantId = tenantId; this.rating = rating; this.comment = comment;
    }
    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getTenantId() { return tenantId; }
    public int getRating() { return rating; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
