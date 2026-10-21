package zw.ac.msu.dzimbahub;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Role permission matrix. Every endpoint is called as an anonymous visitor, a tenant, a landlord and an
 * administrator. The request body is empty ({}), so an allowed role may receive 400/404, but never 401/403.
 */
class RoleAccessMatrixTest extends ApiTestSupport {
    enum Allowed { PUBLIC, ANY_SIGNED_IN, TENANT, LANDLORD, ADMIN }

    static Stream<Arguments> matrix() {
        return Stream.of(
                // public
                Arguments.of("GET", "/api/properties", Allowed.PUBLIC),
                Arguments.of("GET", "/api/properties/1", Allowed.PUBLIC),
                Arguments.of("GET", "/api/properties/1/reviews", Allowed.PUBLIC),
                Arguments.of("POST", "/api/recommendations", Allowed.PUBLIC),
                Arguments.of("GET", "/api/health", Allowed.PUBLIC),
                Arguments.of("GET", "/api/images/1", Allowed.PUBLIC),
                // any signed-in user, including the catch-all (unknown paths are NOT open)
                Arguments.of("GET", "/api/auth/me", Allowed.ANY_SIGNED_IN),
                Arguments.of("GET", "/api/unknown/path", Allowed.ANY_SIGNED_IN),
                // tenant only
                Arguments.of("POST", "/api/tenant/viewings", Allowed.TENANT),
                Arguments.of("GET", "/api/tenant/viewings", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/viewings/1/cancel", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/enquiries", Allowed.TENANT),
                Arguments.of("GET", "/api/tenant/messages", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/messages/1/reply", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/reviews", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/bookings", Allowed.TENANT),
                Arguments.of("GET", "/api/tenant/bookings", Allowed.TENANT),
                Arguments.of("POST", "/api/tenant/bookings/1/pay", Allowed.TENANT),
                Arguments.of("GET", "/api/tenant/preferences", Allowed.TENANT),
                Arguments.of("PUT", "/api/tenant/preferences", Allowed.TENANT),
                // landlord only
                Arguments.of("GET", "/api/landlord/properties", Allowed.LANDLORD),
                Arguments.of("POST", "/api/landlord/properties", Allowed.LANDLORD),
                Arguments.of("POST", "/api/landlord/images", Allowed.LANDLORD),
                Arguments.of("PUT", "/api/landlord/properties/1", Allowed.LANDLORD),
                Arguments.of("DELETE", "/api/landlord/properties/1", Allowed.LANDLORD),
                Arguments.of("GET", "/api/landlord/viewings", Allowed.LANDLORD),
                Arguments.of("POST", "/api/landlord/viewings/1/decision", Allowed.LANDLORD),
                Arguments.of("GET", "/api/landlord/messages", Allowed.LANDLORD),
                Arguments.of("POST", "/api/landlord/messages/1/reply", Allowed.LANDLORD),
                Arguments.of("GET", "/api/landlord/bookings", Allowed.LANDLORD),
                // administrator only
                Arguments.of("GET", "/api/admin/stats", Allowed.ADMIN),
                Arguments.of("GET", "/api/admin/audit", Allowed.ADMIN),
                Arguments.of("GET", "/api/admin/users", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/users/1/approve", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/users/1/reject", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/users/1/suspend", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/users/1/reactivate", Allowed.ADMIN),
                Arguments.of("GET", "/api/admin/properties", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/properties/1/status", Allowed.ADMIN),
                Arguments.of("GET", "/api/admin/properties/pending", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/properties/1/approve", Allowed.ADMIN),
                Arguments.of("POST", "/api/admin/properties/1/reject", Allowed.ADMIN),
                Arguments.of("DELETE", "/api/admin/properties/1", Allowed.ADMIN));
    }

    private int call(String method, String path, AppUser as) throws Exception {
        MockHttpServletRequestBuilder b = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), path)
                .contentType(MediaType.APPLICATION_JSON).content("{}");
        if (as != null) b.header("Authorization", bearer(as));
        return mvc.perform(b).andReturn().getResponse().getStatus();
    }

    @ParameterizedTest(name = "{0} {1} (allowed: {2})")
    @MethodSource("matrix")
    @DisplayName("ROLE-MATRIX each endpoint allows only the intended role")
    void endpointAccess(String method, String path, Allowed allowed) throws Exception {
        AppUser tenant = approved("tenant@test.local", UserRole.TENANT);
        AppUser landlord = approved("landlord@test.local", UserRole.LANDLORD);
        AppUser admin = approved("admin@test.local", UserRole.ADMIN);

        record Actor(String label, AppUser user, UserRole role) {}
        Actor[] actors = {new Actor("anonymous", null, null), new Actor("tenant", tenant, UserRole.TENANT),
                new Actor("landlord", landlord, UserRole.LANDLORD), new Actor("admin", admin, UserRole.ADMIN)};

        for (Actor actor : actors) {
            int code = call(method, path, actor.user());
            boolean permitted = switch (allowed) {
                case PUBLIC -> true;
                case ANY_SIGNED_IN -> actor.user() != null;
                case TENANT -> actor.role() == UserRole.TENANT;
                case LANDLORD -> actor.role() == UserRole.LANDLORD;
                case ADMIN -> actor.role() == UserRole.ADMIN;
            };
            String what = actor.label() + " " + method + " " + path + " -> " + code;
            if (permitted) {
                assertThat(code).as(what).isNotIn(401, 403).isLessThan(500);
            } else if (actor.user() == null) {
                assertThat(code).as(what).isEqualTo(401);
            } else {
                assertThat(code).as(what).isEqualTo(403);
            }
        }
    }
}
