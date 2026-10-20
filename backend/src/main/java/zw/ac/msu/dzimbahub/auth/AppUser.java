package zw.ac.msu.dzimbahub.auth;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "app_users", uniqueConstraints = {@UniqueConstraint(name = "uk_app_users_email", columnNames = "email")})
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 100) private String fullName;
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(nullable = false, length = 100) private String passwordHash;
    @Column(length = 20) private String phone;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserRole role;
    /** Nullable in the database so that rows created before approval existed still load; see getStatus(). */
    @Enumerated(EnumType.STRING) @Column(length = 20) private UserStatus status;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    private Instant reviewedAt;
    @Column(length = 180) private String reviewedBy;

    protected AppUser() {}

    public AppUser(String fullName, String email, String passwordHash, String phone, UserRole role, UserStatus status) {
        this.fullName = fullName; this.email = email; this.passwordHash = passwordHash;
        this.phone = phone; this.role = role; this.status = status; this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getPhone() { return phone; }
    public UserRole getRole() { return role; }
    /** Accounts created before the approval workflow existed have no status and are treated as approved. */
    public UserStatus getStatus() { return status == null ? UserStatus.APPROVED : status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }

    public void changeStatus(UserStatus newStatus, String adminEmail) {
        this.status = newStatus; this.reviewedBy = adminEmail; this.reviewedAt = Instant.now();
    }
}
