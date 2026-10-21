package zw.ac.msu.dzimbahub.auth;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One sign-in attempt waiting for its second step. The method says how the user proves it is them:
 * CODE       - a code we sent (SMS / WhatsApp / server log); only its BCrypt hash is stored
 * TOTP       - the 6-digit code from their authenticator app
 * TOTP_SETUP - first sign-in: they scan a new secret into the app and type its code
 */
@Entity
@Table(name = "otp_challenges")
public class OtpChallenge {
    @Id @Column(length = 40) private String id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 100) private String codeHash;
    @Column(nullable = false) private Instant expiresAt;
    @Column(nullable = false) private int attempts;
    @Column(nullable = false) private boolean consumed;
    @Column(length = 20) private String method;
    /** Only for TOTP_SETUP: the new secret, encrypted, until the user confirms it. */
    @Column(length = 200) private String setupSecret;

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
    public String getMethod() { return method == null ? "CODE" : method; }
    public String getSetupSecret() { return setupSecret; }
    public void setMethod(String m) { method = m; }
    public void setSetupSecret(String s) { setupSecret = s; }
    /** Switches an authenticator challenge to a sent code (the fallback). Failed attempts are kept. */
    public void switchToCode(String newCodeHash, Instant newExpiry) { method = "CODE"; codeHash = newCodeHash; expiresAt = newExpiry; setupSecret = null; }
}
