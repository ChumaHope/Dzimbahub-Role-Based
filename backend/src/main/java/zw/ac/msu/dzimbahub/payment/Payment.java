package zw.ac.msu.dzimbahub.payment;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** One attempt to pay for a booking through Paynow. A booking may have several attempts; one PAID attempt confirms it. */
@Entity
@Table(name = "payments", indexes = {@Index(name = "ix_payments_reference", columnList = "reference", unique = true)})
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long bookingId;
    @Column(nullable = false) private Long tenantId;
    /** ECOCASH, ONEMONEY or WEB (Paynow's own page: cards and other options). */
    @Column(nullable = false, length = 20) private String method;
    @Column(length = 20) private String phone;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal amount;
    @Column(length = 60) private String reference;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentStatus status = PaymentStatus.PENDING;
    /** Paynow's own status word (e.g. Sent, Paid, Cancelled) for display. */
    @Column(length = 40) private String providerStatus;
    @Column(length = 60) private String paynowReference;
    @Column(length = 500) private String pollUrl;
    @Column(length = 500) private String redirectUrl;
    @Column(length = 500) private String message;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant updatedAt;

    protected Payment() {}
    public Payment(Long bookingId, Long tenantId, String method, String phone, BigDecimal amount) {
        this.bookingId = bookingId; this.tenantId = tenantId; this.method = method; this.phone = phone; this.amount = amount;
    }

    public Long getId() { return id; }
    public Long getBookingId() { return bookingId; }
    public Long getTenantId() { return tenantId; }
    public String getMethod() { return method; }
    public String getPhone() { return phone; }
    public BigDecimal getAmount() { return amount; }
    public String getReference() { return reference; }
    public PaymentStatus getStatus() { return status; }
    public String getProviderStatus() { return providerStatus; }
    public String getPaynowReference() { return paynowReference; }
    public String getPollUrl() { return pollUrl; }
    public String getRedirectUrl() { return redirectUrl; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setReference(String r) { reference = r; }
    public void sent(String pollUrl, String redirectUrl, String instructions, String paynowReference) {
        this.pollUrl = pollUrl; this.redirectUrl = redirectUrl; this.message = cut(instructions);
        if (paynowReference != null && !paynowReference.isBlank()) this.paynowReference = paynowReference;
        this.providerStatus = "Sent"; touch();
    }
    public void progress(String providerStatus, String paynowReference) {
        this.providerStatus = cut40(providerStatus);
        if (paynowReference != null && !paynowReference.isBlank()) this.paynowReference = paynowReference;
        touch();
    }
    public void paid(String providerStatus, String paynowReference) {
        progress(providerStatus, paynowReference); this.status = PaymentStatus.PAID; this.message = "Payment received.";
    }
    public void failed(String providerStatus, String message) {
        this.providerStatus = cut40(providerStatus); this.status = PaymentStatus.FAILED; this.message = cut(message); touch();
    }
    private void touch() { updatedAt = Instant.now(); }
    private static String cut(String s) { return s == null ? null : s.length() > 500 ? s.substring(0, 500) : s; }
    private static String cut40(String s) { return s == null ? null : s.length() > 40 ? s.substring(0, 40) : s; }
}
