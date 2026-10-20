package zw.ac.msu.dzimbahub.auth;

import jakarta.persistence.*;
import java.time.Instant;

/** A pending text-message code. Only a BCrypt hash of the code is stored. */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @Id @Column(length = 40) private String id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String codeHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private boolean consumed;

    protected OtpChallenge() {}
    public OtpChallenge(String id, Long userId, String codeHash, Instant expiresAt) {
        this.id = id; this.userId = userId; this.codeHash = codeHash; this.expiresAt = expiresAt;
    }
    public String getId() { return id; }
    public Long getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public int getAttempts() { return attempts; }
    public boolean isConsumed() { return consumed; }
    public void addAttempt() { attempts++; }
    public void consume() { consumed = true; }
}
