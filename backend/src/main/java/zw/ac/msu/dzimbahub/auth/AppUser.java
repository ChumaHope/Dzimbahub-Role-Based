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
    /** Authenticator app secret, encrypted (see SecretCipher). Null until the user sets up the app. */
    @Column(length = 200) private String totpSecret;
    private Instant totpEnabledAt;
    /** Last 30-second step whose code was accepted, so the same code cannot be used twice. Wrapper type: existing rows hold null. */
    private Long totpLastStep;
    /** Wrong passwords / codes in a row. Wrapper types because existing rows hold null. */
    private Integer failedLogins;
    /** While set and in the future, sign-in is refused. */
    private Instant lockedUntil;
    /** Sign-in tokens issued before this moment stop working (see JwtAuthenticationFilter). */
    private Instant passwordChangedAt;

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

    public String getTotpSecret() { return totpSecret; }
    public Instant getTotpEnabledAt() { return totpEnabledAt; }
    public boolean isTotpEnabled() { return totpSecret != null && !totpSecret.isBlank(); }
    public long getTotpLastStep() { return totpLastStep == null ? -1 : totpLastStep; }
    public void enableTotp(String encryptedSecret, long acceptedStep) {
        this.totpSecret = encryptedSecret; this.totpEnabledAt = Instant.now(); this.totpLastStep = acceptedStep;
    }
    public void useTotpStep(long step) { this.totpLastStep = step; }
    public void resetTotp() { this.totpSecret = null; this.totpEnabledAt = null; this.totpLastStep = null; }

    public boolean isLocked(Instant now) { return lockedUntil != null && now.isBefore(lockedUntil); }
    public Instant getLockedUntil() { return lockedUntil; }
    /**
     * Counts one wrong password or code. Returns how many tries are left; 0 means sign-in is now locked
     * for {@code lockMinutes}. A lock that has run out starts a fresh count.
     */
    public int registerFailedLogin(int maxTries, long lockMinutes, Instant now) {
        if (lockedUntil != null && !now.isBefore(lockedUntil)) { lockedUntil = null; failedLogins = 0; }
        int n = (failedLogins == null ? 0 : failedLogins) + 1;
        if (n >= maxTries) { lockedUntil = now.plusSeconds(lockMinutes * 60); failedLogins = 0; return 0; }
        failedLogins = n;
        return maxTries - n;
    }
    public void clearFailedLogins() { failedLogins = 0; lockedUntil = null; }
    public Instant getPasswordChangedAt() { return passwordChangedAt; }
    /** Sets a new (already hashed) password and signs out every existing session. */
    public void changePassword(String newHash) {
        this.passwordHash = newHash; this.passwordChangedAt = Instant.now(); clearFailedLogins();
    }

    public void changeStatus(UserStatus newStatus, String adminEmail) {
        this.status = newStatus; this.reviewedBy = adminEmail; this.reviewedAt = Instant.now();
    }
}
