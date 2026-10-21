package zw.ac.msu.dzimbahub.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.msu.dzimbahub.otp.OtpSender;
import zw.ac.msu.dzimbahub.totp.SecretCipher;
import zw.ac.msu.dzimbahub.totp.Totp;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * The second sign-in step. Two ways to prove it is really the user:
 *  - an authenticator app (Google Authenticator etc.), set up by scanning a QR code at first sign-in
 *  - a six-digit code we send through OtpSender (SMS, WhatsApp, or the server log when SMS_PROVIDER=console)
 */
@Service
public class OtpService {
    public enum Outcome { OK, INVALID, EXPIRED, LOCKED }
    public enum FallbackOutcome { SENT, INVALID, EXPIRED, ALREADY_SENT, NO_PHONE }
    public record Issued(String challengeId, String code, OtpSender.Channel channel) {}
    /** For TOTP_SETUP the plain secret and QR link are returned once so the user can add it to their app. */
    public record Started(String challengeId, String method, String otpauthUri, String secret) {}
    public record Fallback(FallbackOutcome outcome, Issued issued, AppUser user) {}
    public record Verification(Outcome outcome, Long userId, String method) {}

    private static final int SETUP_TTL_SECONDS = 600; // installing the app takes a few minutes

    private final OtpChallengeRepository challenges;
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final OtpSender sender;
    private final SecretCipher cipher;
    private final String issuer;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository challenges, AppUserRepository users, PasswordEncoder encoder, OtpSender sender,
                      SecretCipher cipher,
                      @Value("${app.totp.issuer:DzimbaHub}") String issuer,
                      @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts) {
        this.challenges = challenges; this.users = users; this.encoder = encoder; this.sender = sender; this.cipher = cipher;
        this.issuer = issuer; this.ttlSeconds = ttlSeconds; this.maxAttempts = maxAttempts;
    }

    private String newCode() { return String.format("%06d", random.nextInt(1_000_000)); }

    /** Sends a code through OtpSender (the original way). */
    @Transactional
    public Issued issue(AppUser user) {
        String code = newCode();
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID().toString(), user.getId(),
                encoder.encode(code), Instant.now().plusSeconds(ttlSeconds));
        challenge.setMethod("CODE");
        challenges.save(challenge);
        // If delivery fails this throws OtpDeliveryException and the transaction rolls the challenge back.
        OtpSender.Channel channel = sender.sendCode(user.getPhone(), code, Math.max(1, ttlSeconds / 60));
        return new Issued(challenge.getId(), code, channel);
    }

    /** Authenticator sign-in: asks for the app's code, or starts the one-time setup if the user has no app yet. */
    @Transactional
    public Started startAuthenticator(AppUser user) {
        String id = UUID.randomUUID().toString();
        if (user.isTotpEnabled()) {
            OtpChallenge c = new OtpChallenge(id, user.getId(), "-", Instant.now().plusSeconds(ttlSeconds));
            c.setMethod("TOTP");
            challenges.save(c);
            return new Started(id, "TOTP", null, null);
        }
        String secret = Totp.newSecret();
        OtpChallenge c = new OtpChallenge(id, user.getId(), "-", Instant.now().plusSeconds(Math.max(ttlSeconds, SETUP_TTL_SECONDS)));
        c.setMethod("TOTP_SETUP");
        c.setSetupSecret(cipher.encrypt(secret));
        challenges.save(c);
        return new Started(id, "TOTP_SETUP", Totp.otpauthUri(issuer, user.getEmail(), secret), secret);
    }

    /** "Get a code another way": turns an authenticator challenge into a sent code. */
    @Transactional
    public Fallback fallback(String challengeId) {
        OtpChallenge c = challenges.findById(challengeId).orElse(null);
        if (c == null || c.isConsumed() || c.getAttempts() >= maxAttempts) return new Fallback(FallbackOutcome.INVALID, null, null);
        if (Instant.now().isAfter(c.getExpiresAt())) return new Fallback(FallbackOutcome.EXPIRED, null, null);
        if ("CODE".equals(c.getMethod())) return new Fallback(FallbackOutcome.ALREADY_SENT, null, null);
        AppUser user = users.findById(c.getUserId()).orElse(null);
        if (user == null) return new Fallback(FallbackOutcome.INVALID, null, null);
        if (user.getPhone() == null || user.getPhone().isBlank()) return new Fallback(FallbackOutcome.NO_PHONE, null, user);
        String code = newCode();
        c.switchToCode(encoder.encode(code), Instant.now().plusSeconds(ttlSeconds));
        challenges.save(c);
        OtpSender.Channel channel = sender.sendCode(user.getPhone(), code, Math.max(1, ttlSeconds / 60));
        return new Fallback(FallbackOutcome.SENT, new Issued(c.getId(), code, channel), user);
    }

    /** Never throws for a bad code, so that the failed-attempt counter is always saved. */
    @Transactional
    public Verification verify(String challengeId, String code) {
        OtpChallenge c = challenges.findById(challengeId).orElse(null);
        if (c == null || c.isConsumed()) return new Verification(Outcome.INVALID, null, null);
        String method = c.getMethod();
        if (Instant.now().isAfter(c.getExpiresAt())) return new Verification(Outcome.EXPIRED, null, method);
        if (c.getAttempts() >= maxAttempts) return new Verification(Outcome.LOCKED, null, method);
        c.addAttempt();

        boolean ok;
        switch (method) {
            case "TOTP" -> {
                AppUser user = users.findById(c.getUserId()).orElse(null);
                long step = user == null || !user.isTotpEnabled() ? -1
                        : Totp.matchStep(cipher.decrypt(user.getTotpSecret()), code, Totp.currentStep(), user.getTotpLastStep());
                ok = step >= 0;
                if (ok) { user.useTotpStep(step); users.save(user); }
            }
            case "TOTP_SETUP" -> {
                AppUser user = users.findById(c.getUserId()).orElse(null);
                long step = user == null || c.getSetupSecret() == null ? -1
                        : Totp.matchStep(cipher.decrypt(c.getSetupSecret()), code, Totp.currentStep(), -1);
                ok = step >= 0;
                if (ok) { user.enableTotp(c.getSetupSecret(), step); users.save(user); }
            }
            default -> ok = encoder.matches(code, c.getCodeHash());
        }
        if (!ok) {
            challenges.save(c);
            return new Verification(c.getAttempts() >= maxAttempts ? Outcome.LOCKED : Outcome.INVALID, null, method);
        }
        c.consume();
        challenges.save(c);
        return new Verification(Outcome.OK, c.getUserId(), method);
    }
}
