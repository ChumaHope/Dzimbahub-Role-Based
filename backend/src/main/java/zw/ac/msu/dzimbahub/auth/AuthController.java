package zw.ac.msu.dzimbahub.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.otp.OtpDeliveryException;
import zw.ac.msu.dzimbahub.security.JwtService;
import zw.ac.msu.dzimbahub.service.AuditService;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Sign-up and sign-in.
 * 1. POST /signup  creates a PENDING account. No token is issued.
 * 2. An administrator approves the request.
 * 3. POST /login   checks e-mail and password and starts the second step:
 *      AUTH_SECOND_FACTOR=totp (default): the code from an authenticator app such as Google Authenticator.
 *          At the first sign-in the response carries a QR link so the user can add DzimbaHub to the app.
 *      AUTH_SECOND_FACTOR=code: a six-digit code sent by SMS / WhatsApp / server log.
 *    POST /send-code  fallback ("get a code another way"): sends a code instead of using the app.
 * 4. POST /verify-otp checks the code and only then issues the JWT.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final OtpService otp;
    private final AuditService audit;
    private final boolean exposeCode;
    private final boolean useAuthenticator;
    private final boolean allowFallback;
    private final int maxFailedLogins;
    private final long lockMinutes;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, OtpService otp, AuditService audit,
                          @Value("${app.otp.expose-code:false}") boolean exposeCode,
                          @Value("${app.auth.second-factor:totp}") String secondFactor,
                          @Value("${app.auth.allow-code-fallback:true}") boolean allowFallback,
                          @Value("${app.auth.max-failed-logins:5}") int maxFailedLogins,
                          @Value("${app.auth.lock-minutes:15}") long lockMinutes) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.otp = otp; this.audit = audit; this.exposeCode = exposeCode;
        this.useAuthenticator = !"code".equalsIgnoreCase(secondFactor.trim());
        this.allowFallback = allowFallback;
        this.maxFailedLogins = Math.max(1, maxFailedLogins);
        this.lockMinutes = Math.max(1, lockMinutes);
    }

    // ---------- failed sign-in attempts
    private ResponseEntity<Map<String, String>> lockedReply(AppUser u) {
        long mins = Math.max(1, (Duration.between(Instant.now(), u.getLockedUntil()).getSeconds() + 59) / 60);
        return reply(HttpStatus.TOO_MANY_REQUESTS, "Too many failed sign-in attempts. Please try again in " + mins
                + (mins == 1 ? " minute" : " minutes") + ", or ask the administrator to unlock your account.");
    }

    /** Counts a wrong password or code. Returns the reply to send if the account just got locked, otherwise the tries left text. */
    private String countFailure(AppUser u, String what) {
        int left = u.registerFailedLogin(maxFailedLogins, lockMinutes, Instant.now());
        users.save(u);
        audit.record(u.getEmail(), left == 0 ? "ACCOUNT_LOCKED" : "LOGIN_FAILED", "User", u.getId(), what);
        if (left == 0) return null;
        return left <= 2 ? " " + left + (left == 1 ? " attempt" : " attempts") + " left before sign-in is locked for " + lockMinutes + " minutes." : "";
    }

    public record SignupRequest(@NotBlank @Size(min = 2, max = 100) String fullName,
                                @NotBlank @Email @Size(max = 180) String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "must be a phone number such as +263771234567") String phone,
                                UserRole role) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record VerifyRequest(@NotBlank String challengeId, @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be 6 digits") String code) {}
    public record FallbackRequest(@NotBlank String challengeId) {}
    public record ForgotRequest(@NotBlank @Email String email) {}
    public record ResetRequest(@NotBlank String challengeId,
                               @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be 6 digits") String code,
                               @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword,
                                        @NotBlank @Size(min = 8, max = 72, message = "must be 8 to 72 characters") String newPassword) {}
    public record UserResponse(Long id, String fullName, String email, String role, boolean authenticatorEnabled) {}
    public record AuthResponse(String token, UserResponse user) {}
    /**
     * method: TOTP (type the app's code), TOTP_SETUP (scan otpauthUri / type secret into the app first) or CODE (a code was sent).
     * channel: for CODE, how it was sent (SMS / WHATSAPP). allowFallback: whether "get a code another way" is offered.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChallengeResponse(String challengeId, String method, String phoneHint, String channel, String devCode,
                                    String otpauthUri, String secret, String account, Boolean allowFallback) {}

    private UserResponse toResponse(AppUser u) {
        return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name(), u.isTotpEnabled());
    }

    private static ResponseEntity<Map<String, String>> reply(HttpStatus s, String message) {
        return ResponseEntity.status(s).body(Map.of("message", message));
    }

    private static String hint(String phone) {
        return phone.length() <= 4 ? "****" : "•••••" + phone.substring(phone.length() - 3);
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@Valid @RequestBody SignupRequest body) {
        String email = body.email().trim().toLowerCase(Locale.ROOT);
        UserRole role = body.role() == null ? UserRole.TENANT : body.role();
        if (role == UserRole.ADMIN)
            return reply(HttpStatus.BAD_REQUEST, "Administrator accounts can only be created by the system owner.");
        if (users.existsByEmailIgnoreCase(email))
            return reply(HttpStatus.CONFLICT, "An account with this email already exists.");
        AppUser user = users.save(new AppUser(body.fullName().trim(), email, encoder.encode(body.password()),
                body.phone().trim(), role, UserStatus.PENDING));
        audit.record(email, "SIGNUP_REQUESTED", "User", user.getId(), role.name());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "PENDING",
                "message", "Your request has been sent. Please wait for admin approval."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body) {
        AppUser user = users.findByEmailIgnoreCase(body.email().trim()).orElse(null);
        if (user == null) return reply(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        if (user.isLocked(Instant.now())) return lockedReply(user);
        if (!encoder.matches(body.password(), user.getPasswordHash())) {
            String left = countFailure(user, "wrong password");
            return left == null ? lockedReply(user) : reply(HttpStatus.UNAUTHORIZED, "Email or password is incorrect." + left);
        }
        switch (user.getStatus()) {
            case PENDING: return reply(HttpStatus.FORBIDDEN, "Your account has not been approved yet. Please wait for admin approval.");
            case REJECTED: return reply(HttpStatus.FORBIDDEN, "Your signup request was not approved. Please contact the administrator.");
            case SUSPENDED: return reply(HttpStatus.FORBIDDEN, "Your account has been suspended. Please contact the administrator.");
            default: break;
        }
        if (useAuthenticator) {
            OtpService.Started started = otp.startAuthenticator(user);
            audit.record(user.getEmail(), "TOTP".equals(started.method()) ? "LOGIN_APP_CODE_REQUESTED" : "AUTHENTICATOR_SETUP_STARTED",
                    "User", user.getId(), null);
            return ResponseEntity.ok(new ChallengeResponse(started.challengeId(), started.method(), null, null, null,
                    started.otpauthUri(), started.secret(), started.secret() == null ? null : user.getEmail(), allowFallback));
        }
        if (user.getPhone() == null || user.getPhone().isBlank())
            return reply(HttpStatus.FORBIDDEN, "No phone number is registered for this account. Please contact the administrator.");
        OtpService.Issued issued;
        try {
            issued = otp.issue(user);
        } catch (OtpDeliveryException e) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        audit.record(user.getEmail(), "LOGIN_CODE_SENT", "User", user.getId(), issued.channel().name());
        return ResponseEntity.ok(new ChallengeResponse(issued.challengeId(), "CODE", hint(user.getPhone()),
                issued.channel().name(), exposeCode ? issued.code() : null, null, null, null, false));
    }

    /** Fallback for users who cannot use their authenticator app right now. */
    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@Valid @RequestBody FallbackRequest body) {
        if (!allowFallback)
            return reply(HttpStatus.FORBIDDEN, "Please use your authenticator app. If you lost your phone, ask the administrator to reset your authenticator.");
        Long owner = otp.userOf(body.challengeId());
        AppUser locked = owner == null ? null : users.findById(owner).filter(u -> u.isLocked(Instant.now())).orElse(null);
        if (locked != null) return lockedReply(locked);
        OtpService.Fallback f;
        try {
            f = otp.fallback(body.challengeId());
        } catch (OtpDeliveryException e) {
            return reply(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        switch (f.outcome()) {
            case EXPIRED: return reply(HttpStatus.UNAUTHORIZED, "This sign-in has expired. Please sign in again.");
            case INVALID: return reply(HttpStatus.UNAUTHORIZED, "This sign-in is no longer valid. Please sign in again.");
            case ALREADY_SENT: return reply(HttpStatus.CONFLICT, "A code has already been sent for this sign-in. To get a new one, sign in again.");
            case NO_PHONE: return reply(HttpStatus.FORBIDDEN, "No phone number is registered for this account. Please contact the administrator.");
            default: break;
        }
        OtpService.Issued issued = f.issued();
        audit.record(f.user().getEmail(), "LOGIN_FALLBACK_CODE_SENT", "User", f.user().getId(), issued.channel().name());
        return ResponseEntity.ok(new ChallengeResponse(issued.challengeId(), "CODE", hint(f.user().getPhone()),
                issued.channel().name(), exposeCode ? issued.code() : null, null, null, null, false));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest body) {
        Long owner = otp.userOf(body.challengeId());
        AppUser account = owner == null ? null : users.findById(owner).orElse(null);
        if (account != null && account.isLocked(Instant.now())) { otp.cancel(body.challengeId()); return lockedReply(account); }

        OtpService.Verification v = otp.verify(body.challengeId(), body.code());
        if (v.outcome() != OtpService.Outcome.OK && v.userId() != null && account != null) {
            String left = countFailure(account, "wrong code");
            if (left == null) { otp.cancel(body.challengeId()); return lockedReply(account); }
            if (v.outcome() == OtpService.Outcome.INVALID)
                return reply(HttpStatus.UNAUTHORIZED, ("CODE".equals(v.method()) ? "That code is not correct."
                        : "That code is not correct. Use the newest 6-digit code for DzimbaHub in your authenticator app, and check that your phone sets its time automatically.") + left);
        }
        switch (v.outcome()) {
            case EXPIRED: return reply(HttpStatus.UNAUTHORIZED, "That code has expired. Please sign in again to get a new one.");
            case LOCKED: return reply(HttpStatus.UNAUTHORIZED, "Too many incorrect attempts. Please sign in again to get a new code.");
            case INVALID: return reply(HttpStatus.UNAUTHORIZED, "CODE".equals(v.method()) ? "That code is not correct."
                    : "That code is not correct. Use the newest 6-digit code for DzimbaHub in your authenticator app, and check that your phone sets its time automatically.");
            default: break;
        }
        AppUser user = users.findById(v.userId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.APPROVED)
            return reply(HttpStatus.FORBIDDEN, "This account cannot sign in.");
        user.clearFailedLogins();
        users.save(user);
        if ("TOTP_SETUP".equals(v.method())) audit.record(user.getEmail(), "AUTHENTICATOR_ENABLED", "User", user.getId(), null);
        audit.record(user.getEmail(), "LOGIN_SUCCESS", "User", user.getId(), user.getRole().name() + " via " + v.method());
        return ResponseEntity.ok(new AuthResponse(jwt.generate(user.getEmail(), user.getRole().name()), toResponse(user)));
    }

    // ---------------------------------------------------------------- forgot / reset / change password

    /**
     * Step 1 of "Forgot password". Users with an authenticator app type its code; others get a code sent
     * (SMS / WhatsApp / server log). The reply looks the same for unknown e-mails, so it cannot be used to find accounts.
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@Valid @RequestBody ForgotRequest body) {
        AppUser user = users.findByEmailIgnoreCase(body.email().trim()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.APPROVED)
            return ResponseEntity.ok(new ChallengeResponse(java.util.UUID.randomUUID().toString(), "CODE", "your registered phone",
                    "SMS", null, null, null, null, false));
        if (user.isLocked(Instant.now())) return lockedReply(user);
        if (user.isTotpEnabled()) {
            OtpService.Started s = otp.startResetWithApp(user);
            audit.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "User", user.getId(), "authenticator");
            return ResponseEntity.ok(new ChallengeResponse(s.challengeId(), "TOTP", null, null, null, null, null, null, allowFallback));
        }
        if (user.getPhone() == null || user.getPhone().isBlank())
            return reply(HttpStatus.FORBIDDEN, "No phone number is registered for this account. Please ask the administrator for help.");
        OtpService.Issued issued;
        try { issued = otp.issue(user, OtpService.RESET); }
        catch (OtpDeliveryException e) { return reply(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage()); }
        audit.record(user.getEmail(), "PASSWORD_RESET_REQUESTED", "User", user.getId(), issued.channel().name());
        return ResponseEntity.ok(new ChallengeResponse(issued.challengeId(), "CODE", hint(user.getPhone()),
                issued.channel().name(), exposeCode ? issued.code() : null, null, null, null, false));
    }

    /** Step 2 of "Forgot password": the code plus the new password. The user then signs in normally. */
    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@Valid @RequestBody ResetRequest body) {
        Long owner = otp.userOf(body.challengeId());
        AppUser account = owner == null ? null : users.findById(owner).orElse(null);
        if (account != null && account.isLocked(Instant.now())) { otp.cancel(body.challengeId()); return lockedReply(account); }
        OtpService.Verification v = otp.verify(body.challengeId(), body.code(), OtpService.RESET);
        if (v.outcome() != OtpService.Outcome.OK) {
            String left = "";
            if (v.userId() != null && account != null) {
                left = countFailure(account, "wrong password-reset code");
                if (left == null) { otp.cancel(body.challengeId()); return lockedReply(account); }
            }
            return reply(HttpStatus.UNAUTHORIZED, switch (v.outcome()) {
                case EXPIRED -> "That code has expired. Please start again.";
                case LOCKED -> "Too many incorrect attempts. Please start again.";
                default -> "That code is not correct." + left;
            });
        }
        AppUser user = users.findById(v.userId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.APPROVED) return reply(HttpStatus.FORBIDDEN, "This account cannot be changed.");
        user.changePassword(encoder.encode(body.newPassword()));
        users.save(user);
        audit.record(user.getEmail(), "PASSWORD_RESET", "User", user.getId(), "via " + v.method());
        return ResponseEntity.ok(Map.of("message", "Your password has been changed. You can now sign in with your new password."));
    }

    /** Signed-in users change their password. Other sessions are signed out; this one gets a fresh token. */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(Authentication authentication, @Valid @RequestBody ChangePasswordRequest body) {
        if (authentication == null) return reply(HttpStatus.UNAUTHORIZED, "Please sign in.");
        AppUser user = users.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (user == null) return reply(HttpStatus.UNAUTHORIZED, "Please sign in.");
        if (user.isLocked(Instant.now())) return lockedReply(user);
        if (!encoder.matches(body.currentPassword(), user.getPasswordHash())) {
            String left = countFailure(user, "wrong current password");
            return left == null ? lockedReply(user) : reply(HttpStatus.BAD_REQUEST, "Your current password is not correct." + left);
        }
        if (encoder.matches(body.newPassword(), user.getPasswordHash()))
            return reply(HttpStatus.BAD_REQUEST, "The new password must be different from the current one.");
        user.changePassword(encoder.encode(body.newPassword()));
        users.save(user);
        audit.record(user.getEmail(), "PASSWORD_CHANGED", "User", user.getId(), null);
        // A token issued in this same second still counts as "after" the change (see JwtAuthenticationFilter).
        return ResponseEntity.ok(new AuthResponse(jwt.generate(user.getEmail(), user.getRole().name()), toResponse(user)));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        if (authentication == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in.");
        AppUser user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account not found."));
        return toResponse(user);
    }
}
