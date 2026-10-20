package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import zw.ac.msu.dzimbahub.auth.*;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Signup request, administrator approval, and sign-in with a text-message code. */
class AuthApprovalFlowTest extends ApiTestSupport {

    private Map<String, Object> signupBody(String email, String role) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", "Test Person"); m.put("email", email); m.put("password", "Passw0rd!");
        m.put("phone", "+263771234567"); if (role != null) m.put("role", role);
        return m;
    }
    private Map<String, Object> login(String email) { return Map.of("email", email, "password", "Passw0rd!"); }

    @Test @DisplayName("AUTH-01 signup creates a PENDING account and issues no token")
    void signupCreatesPendingAccount() throws Exception {
        doPost("/api/auth/signup", null, signupBody("new@test.local", "TENANT"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.token").doesNotExist());
        assertThat(users.findByEmailIgnoreCase("new@test.local").orElseThrow().getStatus()).isEqualTo(UserStatus.PENDING);
    }

    @Test @DisplayName("AUTH-02 a pending user cannot sign in")
    void pendingUserCannotSignIn() throws Exception {
        doPost("/api/auth/signup", null, signupBody("pending@test.local", "LANDLORD")).andExpect(status().isCreated());
        doPost("/api/auth/login", null, login("pending@test.local"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message", containsString("approval")));
        assertThat(sms.all()).isEmpty();
    }

    @Test @DisplayName("AUTH-03 the public signup cannot create an administrator")
    void adminSignupBlocked() throws Exception {
        doPost("/api/auth/signup", null, signupBody("evil@test.local", "ADMIN")).andExpect(status().isBadRequest());
        assertThat(users.existsByEmailIgnoreCase("evil@test.local")).isFalse();
    }

    @Test @DisplayName("AUTH-04 duplicate e-mail and invalid phone are rejected")
    void duplicateAndInvalidInput() throws Exception {
        doPost("/api/auth/signup", null, signupBody("dup@test.local", "TENANT")).andExpect(status().isCreated());
        doPost("/api/auth/signup", null, signupBody("DUP@test.local", "TENANT")).andExpect(status().isConflict());
        Map<String, Object> bad = signupBody("badphone@test.local", "TENANT"); bad.put("phone", "abc");
        doPost("/api/auth/signup", null, bad).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("AUTH-05 an approved user signs in with password then a text-message code")
    void approvedUserSignsInWithCode() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        JsonNode challenge = read(doPost("/api/auth/login", null, login("tenant@test.local"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.devCode").doesNotExist()));
        assertThat(sms.all()).hasSize(1);
        String code = sms.lastCode();
        JsonNode result = read(doPost("/api/auth/verify-otp", null, Map.of("challengeId", challenge.get("challengeId").asText(), "code", code))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("TENANT")));
        String token = result.get("token").asText();
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("tenant@test.local"));
    }

    @Test @DisplayName("AUTH-06 a wrong password is rejected and sends no code")
    void wrongPassword() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        doPost("/api/auth/login", null, Map.of("email", "tenant@test.local", "password", "wrong-password")).andExpect(status().isUnauthorized());
        assertThat(sms.all()).isEmpty();
    }

    @Test @DisplayName("AUTH-07 a wrong code is rejected and the challenge locks after the allowed attempts")
    void wrongCodeLocksChallenge() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        String id = read(doPost("/api/auth/login", null, login("tenant@test.local")).andExpect(status().isOk())).get("challengeId").asText();
        String good = sms.lastCode();
        String wrong = good.equals("000000") ? "111111" : "000000";
        for (int i = 0; i < 3; i++) doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", wrong)).andExpect(status().isUnauthorized());
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", good))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message", containsString("Too many")));
    }

    @Test @DisplayName("AUTH-08 a code can be used only once")
    void codeCannotBeReused() throws Exception {
        approved("tenant@test.local", UserRole.TENANT);
        String id = read(doPost("/api/auth/login", null, login("tenant@test.local"))).get("challengeId").asText();
        String code = sms.lastCode();
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", code)).andExpect(status().isOk());
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", id, "code", code)).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("AUTH-09 an unknown challenge is rejected")
    void unknownChallenge() throws Exception {
        doPost("/api/auth/verify-otp", null, Map.of("challengeId", "does-not-exist", "code", "123456")).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("AUTH-10 rejected and suspended users cannot sign in")
    void rejectedAndSuspended() throws Exception {
        user("rejected@test.local", UserRole.TENANT, UserStatus.REJECTED);
        user("suspended@test.local", UserRole.LANDLORD, UserStatus.SUSPENDED);
        doPost("/api/auth/login", null, login("rejected@test.local")).andExpect(status().isForbidden());
        doPost("/api/auth/login", null, login("suspended@test.local")).andExpect(status().isForbidden());
        assertThat(sms.all()).isEmpty();
    }

    @Test @DisplayName("AUTH-11 suspending a user stops their existing token immediately")
    void suspendedTokenStopsWorking() throws Exception {
        AppUser tenant = approved("tenant@test.local", UserRole.TENANT);
        doGet("/api/auth/me", tenant).andExpect(status().isOk());
        tenant.changeStatus(UserStatus.SUSPENDED, "test");
        users.save(tenant);
        doGet("/api/auth/me", tenant).andExpect(status().isUnauthorized());
    }

    @Test @DisplayName("AUTH-12 administrator approval enables sign-in and notifies the user by text message")
    void adminApprovalEnablesSignIn() throws Exception {
        AppUser admin = approved("admin@test.local", UserRole.ADMIN);
        doPost("/api/auth/signup", null, signupBody("applicant@test.local", "TENANT")).andExpect(status().isCreated());
        AppUser applicant = users.findByEmailIgnoreCase("applicant@test.local").orElseThrow();
        doPost("/api/auth/login", null, login("applicant@test.local")).andExpect(status().isForbidden());

        doPost("/api/admin/users/" + applicant.getId() + "/approve", admin, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        assertThat(sms.last().text()).contains("approved");

        doPost("/api/auth/login", null, login("applicant@test.local")).andExpect(status().isOk());
    }

    @Test @DisplayName("AUTH-13 a missing or tampered token is rejected")
    void badTokens() throws Exception {
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/auth/me").header("Authorization", "Bearer abc.def.ghi")).andExpect(status().isUnauthorized());
        mvc.perform(MockMvcRequestBuilders.get("/api/tenant/viewings").header("Authorization", "Bearer abc.def.ghi")
                .contentType(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
