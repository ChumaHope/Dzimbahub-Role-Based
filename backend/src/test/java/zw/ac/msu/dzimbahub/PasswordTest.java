package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import zw.ac.msu.dzimbahub.totp.SecretCipher;
import zw.ac.msu.dzimbahub.totp.Totp;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Changing a password while signed in, and resetting a forgotten one. (Test settings: sign-in uses sent codes.) */
class PasswordTest extends ApiTestSupport {
    @Autowired SecretCipher cipher;

    private Map<String, Object> login(String email, String password) { return Map.of("email", email, "password", password); }

    @Test @DisplayName("PW-01 a signed-in user changes their password; old sessions stop working")
    void changePassword() throws Exception {
        AppUser tenant = approved("tenant@test.local", UserRole.TENANT);
        String oldToken = bearer(tenant);
        Thread.sleep(1100); // tokens carry whole seconds

        doPost("/api/auth/change-password", tenant, Map.of("currentPassword", "wrong-one", "newPassword", "NewPassw0rd!"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Your current password is not correct."));
        doPost("/api/auth/change-password", tenant, Map.of("currentPassword", "Passw0rd!", "newPassword", "Passw0rd!"))
                .andExpect(status().isBadRequest());
        doPost("/api/auth/change-password", tenant, Map.of("currentPassword", "Passw0rd!", "newPassword", "short"))
                .andExpect(status().isBadRequest());
        JsonNode r = read(doPost("/api/auth/change-password", tenant, Map.of("currentPassword", "Passw0rd!", "newPassword", "NewPassw0rd!"))
                .andExpect(status().isOk()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + r.get("token").asText()))
                .andExpect(status().isOk());
        doPost("/api/auth/login", null, login("tenant@test.local", "Passw0rd!")).andExpect(status().isUnauthorized());
        doPost("/api/auth/login", null, login("tenant@test.local", "NewPassw0rd!")).andExpect(status().isOk());
        doPost("/api/auth/change-password", null, Map.of("currentPassword", "x", "newPassword", "NewPassw0rd!")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("PW-02 forgot password: a sent code resets it; the reset code cannot be used to sign in")
    void resetWithSentCode() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        JsonNode c = read(doPost("/api/auth/forgot-password", null, Map.of("email", "tenant@test.local"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.method").value("CODE")));
        String id = c.get("challengeId").asText(), code = sms.lastCode();

        doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", code)).andExpect(status().isUnauthorized());
        doPost("/api/auth/reset-password", null, Map.of("challengeId", id, "code", code.equals("000000") ? "111111" : "000000", "newPassword", "Reset#12345"))
                .andExpect(status().isUnauthorized());
        doPost("/api/auth/reset-password", null, Map.of("challengeId", id, "code", code, "newPassword", "Reset#12345")).andExpect(status().isOk());
        doPost("/api/auth/reset-password", null, Map.of("challengeId", id, "code", code, "newPassword", "Again#12345")).andExpect(status().isUnauthorized());

        doPost("/api/auth/login", null, login("tenant@test.local", "Passw0rd!")).andExpect(status().isUnauthorized());
        doPost("/api/auth/login", null, login("tenant@test.local", "Reset#12345")).andExpect(status().isOk());
    }

    @Test @DisplayName("PW-03 unknown e-mails get the same reply and nothing is sent; sign-in codes cannot reset a password")
    void noLeaksAndNoCrossUse() throws Exception {
        doPost("/api/auth/forgot-password", null, Map.of("email", "nobody@test.local")).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("CODE"));
        assertThat(sms.all()).isEmpty();

        approved("tenant@test.local", UserRole.TENANT);
        String loginId = read(doPost("/api/auth/login", null, login("tenant@test.local", "Passw0rd!"))).get("challengeId").asText();
        doPost("/api/auth/reset-password", null, Map.of("challengeId", loginId, "code", sms.lastCode(), "newPassword", "Hijack#12345"))
                .andExpect(status().isUnauthorized());
        doPost("/api/auth/login", null, login("tenant@test.local", "Passw0rd!")).andExpect(status().isOk());
    }

    @Test @DisplayName("PW-04 users with an authenticator app reset with the app's code")
    void resetWithAuthenticator() throws Exception {
        AppUser u = approved("tenant@test.local", UserRole.TENANT);
        String secret = Totp.newSecret();
        u.enableTotp(cipher.encrypt(secret), Totp.currentStep() - 5);
        users.save(u);
        JsonNode c = read(doPost("/api/auth/forgot-password", null, Map.of("email", "tenant@test.local"))
                .andExpect(jsonPath("$.method").value("TOTP")));
        assertThat(sms.all()).isEmpty();
        doPost("/api/auth/reset-password", null, Map.of("challengeId", c.get("challengeId").asText(),
                "code", Totp.codeAt(secret, Totp.currentStep()), "newPassword", "Reset#12345")).andExpect(status().isOk());
        doPost("/api/auth/login", null, login("tenant@test.local", "Reset#12345")).andExpect(status().isOk());
    }
}
