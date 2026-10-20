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
import zw.ac.msu.dzimbahub.security.JwtService;
import zw.ac.msu.dzimbahub.service.AuditService;
import java.util.Locale;
import java.util.Map;

/**
 * Sign-up and sign-in.
 * 1. POST /signup  creates a PENDING account. No token is issued.
 * 2. An administrator approves the request.
 * 3. POST /login   checks e-mail and password, then sends a six-digit code by text message.
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

    public AuthController(AppUserRepository users, PasswordEncoder encoder, JwtService jwt, OtpService otp, AuditService audit,
                          @Value("${app.otp.expose-code:false}") boolean exposeCode) {
        this.users = users; this.encoder = encoder; this.jwt = jwt; this.otp = otp; this.audit = audit; this.exposeCode = exposeCode;
    }

    public record SignupRequest(@NotBlank @Size(min = 2, max = 100) String fullName,
                                @NotBlank @Email @Size(max = 180) String email,
                                @NotBlank @Size(min = 8, max = 72) String password,
                                @NotBlank @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "must be a phone number such as +263771234567") String phone,
                                UserRole role) {}
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
    public record VerifyRequest(@NotBlank String challengeId, @NotBlank @Pattern(regexp = "^[0-9]{6}$", message = "must be 6 digits") String code) {}
    public record UserResponse(Long id, String fullName, String email, String role) {}
    public record AuthResponse(String token, UserResponse user) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChallengeResponse(String challengeId, String phoneHint, String devCode) {}

    private UserResponse toResponse(AppUser u) { return new UserResponse(u.getId(), u.getFullName(), u.getEmail(), u.getRole().name()); }

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
                "message", "Your request has been received. An administrator must approve it before you can sign in. You will receive a text message when it is reviewed."));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest body) {
        AppUser user = users.findByEmailIgnoreCase(body.email().trim()).orElse(null);
        if (user == null || !encoder.matches(body.password(), user.getPasswordHash()))
            return reply(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        switch (user.getStatus()) {
            case PENDING: return reply(HttpStatus.FORBIDDEN, "Your account is waiting for administrator approval. You will receive a text message once it is approved.");
            case REJECTED: return reply(HttpStatus.FORBIDDEN, "Your signup request was not approved. Please contact the administrator.");
            case SUSPENDED: return reply(HttpStatus.FORBIDDEN, "Your account has been suspended. Please contact the administrator.");
            default: break;
        }
        if (user.getPhone() == null || user.getPhone().isBlank())
            return reply(HttpStatus.FORBIDDEN, "No phone number is registered for this account. Please contact the administrator.");
        OtpService.Issued issued = otp.issue(user);
        audit.record(user.getEmail(), "LOGIN_CODE_SENT", "User", user.getId(), null);
        return ResponseEntity.ok(new ChallengeResponse(issued.challengeId(), hint(user.getPhone()), exposeCode ? issued.code() : null));
    }

    @PostMapping("/verify-otp")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyRequest body) {
        OtpService.Verification v = otp.verify(body.challengeId(), body.code());
        switch (v.outcome()) {
            case EXPIRED: return reply(HttpStatus.UNAUTHORIZED, "That code has expired. Please sign in again to get a new one.");
            case LOCKED: return reply(HttpStatus.UNAUTHORIZED, "Too many incorrect attempts. Please sign in again to get a new code.");
            case INVALID: return reply(HttpStatus.UNAUTHORIZED, "That code is not correct.");
            default: break;
        }
        AppUser user = users.findById(v.userId()).orElse(null);
        if (user == null || user.getStatus() != UserStatus.APPROVED)
            return reply(HttpStatus.FORBIDDEN, "This account cannot sign in.");
        audit.record(user.getEmail(), "LOGIN_SUCCESS", "User", user.getId(), user.getRole().name());
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
