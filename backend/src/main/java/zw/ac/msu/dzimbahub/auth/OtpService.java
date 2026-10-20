package zw.ac.msu.dzimbahub.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/** Issues and verifies six-digit codes that are delivered to the user's phone by text message. */
@Service
public class OtpService {
    public enum Outcome { OK, INVALID, EXPIRED, LOCKED }
    public record Issued(String challengeId, String code) {}
    public record Verification(Outcome outcome, Long userId) {}

    private final OtpChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final SmsSender sms;
    private final long ttlSeconds;
    private final int maxAttempts;
    private final SecureRandom random = new SecureRandom();

    public OtpService(OtpChallengeRepository challenges, PasswordEncoder encoder, SmsSender sms,
                      @Value("${app.otp.ttl-seconds:300}") long ttlSeconds,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts) {
        this.challenges = challenges; this.encoder = encoder; this.sms = sms;
        this.ttlSeconds = ttlSeconds; this.maxAttempts = maxAttempts;
    }

    @Transactional
    public Issued issue(AppUser user) {
        String code = String.format("%06d", random.nextInt(1_000_000));
        OtpChallenge challenge = new OtpChallenge(UUID.randomUUID().toString(), user.getId(),
                encoder.encode(code), Instant.now().plusSeconds(ttlSeconds));
        challenges.save(challenge);
        sms.send(user.getPhone(), "Your DzimbaHub verification code is " + code
                + ". It expires in " + Math.max(1, ttlSeconds / 60) + " minutes. Do not share it.");
        return new Issued(challenge.getId(), code);
    }

    /** Never throws for a bad code, so that the failed-attempt counter is always saved. */
    @Transactional
    public Verification verify(String challengeId, String code) {
        OtpChallenge c = challenges.findById(challengeId).orElse(null);
        if (c == null || c.isConsumed()) return new Verification(Outcome.INVALID, null);
        if (Instant.now().isAfter(c.getExpiresAt())) return new Verification(Outcome.EXPIRED, null);
        if (c.getAttempts() >= maxAttempts) return new Verification(Outcome.LOCKED, null);
        c.addAttempt();
        if (!encoder.matches(code, c.getCodeHash())) {
            challenges.save(c);
            return new Verification(c.getAttempts() >= maxAttempts ? Outcome.LOCKED : Outcome.INVALID, null);
        }
        c.consume();
        challenges.save(c);
        return new Verification(Outcome.OK, c.getUserId());
    }
}
