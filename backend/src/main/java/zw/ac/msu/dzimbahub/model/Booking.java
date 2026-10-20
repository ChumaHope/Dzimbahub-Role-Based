package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;

/** A booking that is confirmed through the SIMULATED payment workflow (no real money moves). */
@Entity
@Table(name = "bookings")
public class Booking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long propertyId;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private double amount;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private BookingStatus status = BookingStatus.PENDING;
    @Column(length = 40) private String paymentRef;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected Booking() {}
    public Booking(Long propertyId, Long tenantId, double amount) { this.propertyId = propertyId; this.tenantId = tenantId; this.amount = amount; }
    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getTenantId() { return tenantId; }
    public double getAmount() { return amount; }
    public BookingStatus getStatus() { return status; }
    public String getPaymentRef() { return paymentRef; }
    public Instant getCreatedAt() { return createdAt; }
    public void confirm(String ref) { this.status = BookingStatus.CONFIRMED; this.paymentRef = ref; }
}
