package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "viewings")
public class Viewing {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long propertyId;
    @Column(nullable = false) private Long tenantId;
    @Column(nullable = false) private LocalDate viewDate;
    @Column(nullable = false) private LocalTime viewTime;
    @Column(length = 500) private String note;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private RequestStatus status = RequestStatus.PENDING;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected Viewing() {}
    public Viewing(Long propertyId, Long tenantId, LocalDate viewDate, LocalTime viewTime, String note) {
        this.propertyId = propertyId; this.tenantId = tenantId; this.viewDate = viewDate; this.viewTime = viewTime; this.note = note;
    }
    public Long getId() { return id; }
    public Long getPropertyId() { return propertyId; }
    public Long getTenantId() { return tenantId; }
    public LocalDate getViewDate() { return viewDate; }
    public LocalTime getViewTime() { return viewTime; }
    public String getNote() { return note; }
    public RequestStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public void setStatus(RequestStatus status) { this.status = status; }
}
