package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "audit_logs")
public class AuditLog {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(length = 180) private String actor;
    @Column(nullable = false, length = 60) private String action;
    @Column(length = 40) private String targetType;
    @Column(length = 40) private String targetId;
    @Column(length = 500) private String detail;
    @Column(nullable = false) private Instant createdAt = Instant.now();

    protected AuditLog() {}
    public AuditLog(String actor, String action, String targetType, String targetId, String detail) {
        this.actor = actor; this.action = action; this.targetType = targetType; this.targetId = targetId; this.detail = detail;
    }
    public Long getId() { return id; }
    public String getActor() { return actor; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public String getTargetId() { return targetId; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
