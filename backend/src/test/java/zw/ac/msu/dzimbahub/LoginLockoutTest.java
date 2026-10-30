package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** After 5 wrong passwords or codes in a row, sign-in is locked for 15 minutes (test settings use the defaults). */
class LoginLockoutTest extends ApiTestSupport {
    private Map<String, Object> wrong(String email) { return Map.of("email", email, "password", "not-the-password"); }
    private Map<String, Object> right(String email) { return Map.of("email", email, "password", "Passw0rd!"); }

    @Test @DisplayName("LOCK-01 wrong passwords count down, then lock sign-in even with the right password")
    void wrongPasswordsLock() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        for (int i = 0; i < 2; i++) doPost("/api/auth/login", null, wrong("tenant@test.local")).andExpect(status().isUnauthorized());
        doPost("/api/auth/login", null, wrong("tenant@test.local")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("2 attempts left")));
        doPost("/api/auth/login", null, wrong("tenant@test.local")).andExpect(jsonPath("$.message", containsString("1 attempt left")));
        doPost("/api/auth/login", null, wrong("tenant@test.local")).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Too many failed sign-in attempts")));
        doPost("/api/auth/login", null, right("tenant@test.local")).andExpect(status().isTooManyRequests());
        assertThat(sms.all()).isEmpty();
    }

    @Test @DisplayName("LOCK-02 wrong codes count too, and a lock stops the current code from being accepted")
    void wrongCodesLock() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        for (int round = 0; round < 2; round++) {
            String id = read(doPost("/api/auth/login", null, right("tenant@test.local")).andExpect(status().isOk())).get("challengeId").asText();
            String good = sms.lastCode(), bad = good.equals("000000") ? "111111" : "000000";
            int tries = round == 0 ? 2 : 3;
            for (int i = 0; i < tries; i++) doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", bad));
            if (round == 1) doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", good)).andExpect(status().isTooManyRequests());
        }
        doPost("/api/auth/login", null, right("tenant@test.local")).andExpect(status().isTooManyRequests());
    }

    @Test @DisplayName("LOCK-03 the administrator can unlock; a successful sign-in resets the count")
    void adminUnlocks() throws Exception {
        AppUser admin = approved("admin@test.local", UserRole.ADMIN);
        AppUser tenant = approved("tenant@test.local", UserRole.TENANT);
        for (int i = 0; i < 5; i++) doPost("/api/auth/login", null, wrong("tenant@test.local"));
        doGet("/api/admin/users", admin).andExpect(jsonPath("$[?(@.email=='tenant@test.local')].lockedUntil").isNotEmpty());
        doPost("/api/admin/users/" + tenant.getId() + "/unlock", tenant, null).andExpect(status().isForbidden());
        doPost("/api/admin/users/" + tenant.getId() + "/unlock", admin, null).andExpect(status().isOk());

        JsonNode c = read(doPost("/api/auth/login", null, right("tenant@test.local")).andExpect(status().isOk()));
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", c.get("challengeId").asText(), "code", sms.lastCode())).andExpect(status().isOk());
        for (int i = 0; i < 4; i++) doPost("/api/auth/login", null, wrong("tenant@test.local")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("LOCK-04 an unknown e-mail gets the same plain message")
    void unknownEmail() throws Exception {
        doPost("/api/auth/login", null, wrong("nobody@test.local")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Email or password is incorrect."));
    }
}
