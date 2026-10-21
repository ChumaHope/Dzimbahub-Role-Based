package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import zw.ac.msu.dzimbahub.totp.SecretCipher;
import zw.ac.msu.dzimbahub.totp.Totp;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sign-in with an authenticator app (Google Authenticator), with a sent code as the fallback. */
@TestPropertySource(properties = {"app.auth.second-factor=totp", "app.auth.allow-code-fallback=true"})
class TotpFlowTest extends ApiTestSupport {
    @Autowired SecretCipher cipher;

    private Map<String, Object> login(String email) { return Map.of("email", email, "password", "Passw0rd!"); }

    private AppUser withAuthenticator(String email, String secret) {
        AppUser u = approved(email, UserRole.TENANT);
        u.enableTotp(cipher.encrypt(secret), Totp.currentStep() - 5);
        return users.save(u);
    }

    @Test @DisplayName("TOTP-01 first sign-in shows a QR link; the app's code finishes setup and signs in")
    void firstSignInSetsUpAuthenticator() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        JsonNode c = read(doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("TOTP_SETUP")).andExpect(jsonPath("$.allowFallback").value(true)));
        String secret = c.get("secret").asText();
        assertThat(c.get("otpauthUri").asText()).startsWith("otpauth://totp/").contains("secret=" + secret).contains("issuer=DzimbaHub");
        assertThat(sms.all()).isEmpty();

        doPost("/api/auth/verify-otp", null, Map.of("challengeId", c.get("challengeId").asText(), "code", "000000"))
                .andExpect(status().isUnauthorized());
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", c.get("challengeId").asText(), "code", Totp.codeAt(secret, Totp.currentStep())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").exists()).andExpect(jsonPath("$.user.authenticatorEnabled").value(true));

        AppUser saved = users.findByEmailIgnoreCase("tenant@test.local").orElseThrow();
        assertThat(saved.isTotpEnabled()).isTrue();
        assertThat(saved.getTotpSecret()).isNotEqualTo(secret);          // stored encrypted
        assertThat(cipher.decrypt(saved.getTotpSecret())).isEqualTo(secret);
    }

    @Test @DisplayName("TOTP-02 later sign-ins ask for the app's code, and the same code cannot be used twice")
    void laterSignInUsesAppCode() throws Exception {
        String secret = Totp.newSecret();
        withAuthenticator("tenant@test.local", secret);
        String code = Totp.codeAt(secret, Totp.currentStep());

        JsonNode c = read(doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("TOTP")).andExpect(jsonPath("$.otpauthUri").doesNotExist()));
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", c.get("challengeId").asText(), "code", code)).andExpect(status().isOk());

        JsonNode again = read(doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(status().isOk()));
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", again.get("challengeId").asText(), "code", code)).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("TOTP-03 'get a code another way' sends a code through the fallback channel")
    void fallbackCode() throws Exception {
        withAuthenticator("tenant@test.local", Totp.newSecret());
        JsonNode c = read(doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(status().isOk()));
        String id = c.get("challengeId").asText();
        doPost("/api/auth/send-code", null, Map.of("challengeId", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("CODE")).andExpect(jsonPath("$.channel").value("SMS"));
        assertThat(sms.all()).hasSize(1);
        doPost("/api/auth/send-code", null, Map.of("challengeId", id)).andExpect(status().isConflict());
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", sms.lastCode())).andExpect(status().isOk());
        doPost("/api/auth/send-code", null, Map.of("challengeId", "no-such-sign-in")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("TOTP-04 the administrator can reset a lost authenticator; the user sets it up again")
    void adminReset() throws Exception {
        AppUser admin = approved("admin@test.local", UserRole.ADMIN);
        AppUser landlord = approved("landlord@test.local", UserRole.LANDLORD);
        AppUser tenant = withAuthenticator("tenant@test.local", Totp.newSecret());

        doPost("/api/admin/users/" + tenant.getId() + "/reset-authenticator", landlord, null).andExpect(status().isForbidden());
        doPost("/api/admin/users/" + tenant.getId() + "/reset-authenticator", admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.authenticator").value(false));
        doPost("/api/admin/users/" + tenant.getId() + "/reset-authenticator", admin, null).andExpect(status().isConflict());
        doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(jsonPath("$.method").value("TOTP_SETUP"));
    }

    @Test @DisplayName("TOTP-05 a pending account cannot start authenticator setup")
    void pendingCannotSetUp() throws Exception {
        user("pending@test.local", UserRole.TENANT, zw.ac.msu.dzimbahub.auth.UserStatus.PENDING);
        doPost("/api/auth/login", null, login("pending@test.local")).andExpect(status().isForbidden());
    }
}
