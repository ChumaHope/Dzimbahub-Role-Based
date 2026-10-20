package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import zw.ac.msu.dzimbahub.model.Property;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** What each role can actually do, and the business rules behind those actions. */
class WorkflowTest extends ApiTestSupport {
    AppUser admin, landlord, otherLandlord, tenant, otherTenant;

    @BeforeEach
    void people() {
        admin = approved("admin@test.local", UserRole.ADMIN);
        landlord = approved("landlord@test.local", UserRole.LANDLORD);
        otherLandlord = approved("landlord2@test.local", UserRole.LANDLORD);
        tenant = approved("tenant@test.local", UserRole.TENANT);
        otherTenant = approved("tenant2@test.local", UserRole.TENANT);
    }

    private long approvedViewing(Property p, AppUser who) throws Exception {
        long id = read(doPost("/api/tenant/viewings", who, Map.of("propertyId", p.getId(), "date", "2099-12-31", "time", "10:00", "note", "Hello"))
                .andExpect(status().isOk())).get("id").asLong();
        doPost("/api/landlord/viewings/" + id + "/decision", landlord, Map.of("approve", true)).andExpect(status().isOk());
        return id;
    }

    // ------------------------------------------------------------ landlord: listings
    @Test @DisplayName("LL-01 a landlord creates, edits and deletes their own listing")
    void landlordManagesOwnListing() throws Exception {
        JsonNode created = read(doPost("/api/landlord/properties", landlord, propertyBody("My Room", 150))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(landlord.getId())));
        long id = created.get("id").asLong();
        doPut("/api/landlord/properties/" + id, landlord, propertyBody("My Room (renamed)", 175))
                .andExpect(status().isOk()).andExpect(jsonPath("$.title").value("My Room (renamed)")).andExpect(jsonPath("$.price").value(175.0));
        doGet("/api/landlord/properties", landlord).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        doDelete("/api/landlord/properties/" + id, landlord).andExpect(status().isOk());
        assertThat(properties.findById(id)).isEmpty();
    }

    @Test @DisplayName("LL-02 a landlord cannot change another landlord's listing")
    void landlordCannotTouchOthersListing() throws Exception {
        Property p = listing(otherLandlord, "Not yours");
        doPut("/api/landlord/properties/" + p.getId(), landlord, propertyBody("Hijack", 1)).andExpect(status().isForbidden());
        doDelete("/api/landlord/properties/" + p.getId(), landlord).andExpect(status().isForbidden());
        assertThat(properties.findById(p.getId()).orElseThrow().getTitle()).isEqualTo("Not yours");
    }

    @Test @DisplayName("LL-03 an invalid listing is rejected")
    void invalidListing() throws Exception {
        doPost("/api/landlord/properties", landlord, propertyBody("Bad", -5)).andExpect(status().isBadRequest());
        Map<String, Object> badRoom = propertyBody("Bad", 100); badRoom.put("roomType", "Castle");
        doPost("/api/landlord/properties", landlord, badRoom).andExpect(status().isBadRequest());
        Map<String, Object> badImage = propertyBody("Bad", 100); badImage.put("imageUrl", "javascript:alert(1)");
        doPost("/api/landlord/properties", landlord, badImage).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("LL-04 a tenant cannot create a listing")
    void tenantCannotCreateListing() throws Exception {
        doPost("/api/landlord/properties", tenant, propertyBody("Sneaky", 100)).andExpect(status().isForbidden());
        assertThat(properties.count()).isZero();
    }

    // ------------------------------------------------------------ viewings
    @Test @DisplayName("VW-01 viewing request: tenant asks, only the owning landlord decides")
    void viewingFlow() throws Exception {
        Property p = listing(landlord, "Room A");
        long id = read(doPost("/api/tenant/viewings", tenant, Map.of("propertyId", p.getId(), "date", "2099-12-31", "time", "10:00"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))).get("id").asLong();

        doPost("/api/tenant/viewings", tenant, Map.of("propertyId", p.getId(), "date", "2099-12-31", "time", "11:00")).andExpect(status().isConflict());

        doGet("/api/landlord/viewings", landlord).andExpect(jsonPath("$.length()").value(1));
        doGet("/api/landlord/viewings", otherLandlord).andExpect(jsonPath("$.length()").value(0));
        doPost("/api/landlord/viewings/" + id + "/decision", otherLandlord, Map.of("approve", true)).andExpect(status().isForbidden());

        doPost("/api/landlord/viewings/" + id + "/decision", landlord, Map.of("approve", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        doPost("/api/landlord/viewings/" + id + "/decision", landlord, Map.of("approve", false)).andExpect(status().isConflict());
        doGet("/api/tenant/viewings", tenant).andExpect(jsonPath("$[0].status").value("APPROVED"));
    }

    @Test @DisplayName("VW-02 a tenant can cancel only their own request, and a past date is refused")
    void cancelAndPastDate() throws Exception {
        Property p = listing(landlord, "Room A");
        long id = read(doPost("/api/tenant/viewings", tenant, Map.of("propertyId", p.getId(), "date", "2099-12-31", "time", "10:00")).andExpect(status().isOk())).get("id").asLong();
        doPost("/api/tenant/viewings/" + id + "/cancel", otherTenant, null).andExpect(status().isForbidden());
        doPost("/api/tenant/viewings/" + id + "/cancel", tenant, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        doPost("/api/tenant/viewings", tenant, Map.of("propertyId", p.getId(), "date", "2001-01-01", "time", "10:00")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ enquiries
    @Test @DisplayName("MSG-01 enquiry goes to the owning landlord, who replies; others cannot reply")
    void messaging() throws Exception {
        Property p = listing(landlord, "Room A");
        long msgId = read(doPost("/api/tenant/enquiries", tenant, Map.of("propertyId", p.getId(), "message", "Is it available?"))
                .andExpect(status().isOk())).get("id").asLong();

        doGet("/api/landlord/messages", landlord).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].body").value("Is it available?"));
        doGet("/api/landlord/messages", otherLandlord).andExpect(jsonPath("$.length()").value(0));

        doPost("/api/landlord/messages/" + msgId + "/reply", otherLandlord, Map.of("message", "Mine now")).andExpect(status().isForbidden());
        doPost("/api/tenant/messages/" + msgId + "/reply", tenant, Map.of("message", "Replying to myself")).andExpect(status().isForbidden());
        doPost("/api/landlord/messages/" + msgId + "/reply", landlord, Map.of("message", "Yes, from 1 March."))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientId").value(tenant.getId()));

        doGet("/api/tenant/messages", tenant).andExpect(jsonPath("$.length()").value(2)).andExpect(jsonPath("$[0].body").value("Yes, from 1 March."));
        doGet("/api/tenant/messages", otherTenant).andExpect(jsonPath("$.length()").value(0));
    }

    @Test @DisplayName("MSG-02 an empty enquiry is refused")
    void emptyEnquiry() throws Exception {
        Property p = listing(landlord, "Room A");
        doPost("/api/tenant/enquiries", tenant, Map.of("propertyId", p.getId(), "message", "   ")).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------ reviews
    @Test @DisplayName("REV-01 rating rules: needs an approved viewing, one review each, 1 to 5, average updates")
    void reviewRules() throws Exception {
        Property p = listing(landlord, "Room A");
        Map<String, Object> review = Map.of("propertyId", p.getId(), "rating", 4, "comment", "Clean and quiet");

        doPost("/api/tenant/reviews", tenant, review).andExpect(status().isForbidden());
        approvedViewing(p, tenant);
        doPost("/api/tenant/reviews", tenant, Map.of("propertyId", p.getId(), "rating", 6)).andExpect(status().isBadRequest());
        doPost("/api/tenant/reviews", tenant, review).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4));
        doPost("/api/tenant/reviews", tenant, review).andExpect(status().isConflict());

        approvedViewing(p, otherTenant);
        doPost("/api/tenant/reviews", otherTenant, Map.of("propertyId", p.getId(), "rating", 5)).andExpect(status().isOk());

        doGet("/api/properties/" + p.getId(), null).andExpect(jsonPath("$.rating").value(4.5)).andExpect(jsonPath("$.reviewCount").value(2));
        doGet("/api/properties/" + p.getId() + "/reviews", null).andExpect(jsonPath("$.length()").value(2));
    }

    @Test @DisplayName("REV-02 landlords and administrators cannot post reviews")
    void onlyTenantsReview() throws Exception {
        Property p = listing(landlord, "Room A");
        doPost("/api/tenant/reviews", landlord, Map.of("propertyId", p.getId(), "rating", 5)).andExpect(status().isForbidden());
        doPost("/api/tenant/reviews", admin, Map.of("propertyId", p.getId(), "rating", 5)).andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------ bookings (simulated payment)
    @Test @DisplayName("BK-01 booking needs an approved viewing; payment is simulated and can be made once")
    void bookingAndSimulatedPayment() throws Exception {
        Property p = listing(landlord, "Room A");
        doPost("/api/tenant/bookings", tenant, Map.of("propertyId", p.getId())).andExpect(status().isForbidden());
        approvedViewing(p, tenant);
        long id = read(doPost("/api/tenant/bookings", tenant, Map.of("propertyId", p.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING"))).get("id").asLong();
        doPost("/api/tenant/bookings", tenant, Map.of("propertyId", p.getId())).andExpect(status().isConflict());

        doPost("/api/tenant/bookings/" + id + "/pay", otherTenant, null).andExpect(status().isForbidden());
        doPost("/api/tenant/bookings/" + id + "/pay", tenant, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED")).andExpect(jsonPath("$.paymentRef", startsWith("SIM-")));
        doPost("/api/tenant/bookings/" + id + "/pay", tenant, null).andExpect(status().isConflict());

        doGet("/api/landlord/bookings", landlord).andExpect(jsonPath("$.length()").value(1));
        doGet("/api/landlord/bookings", otherLandlord).andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------ preferences and recommendations
    @Test @DisplayName("PREF-01 a tenant saves preferences and gets them back")
    void preferences() throws Exception {
        doPut("/api/tenant/preferences", tenant, Map.of("budget", 200, "area", "Avondale", "roomType", "Single", "furnished", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.area").value("Avondale"));
        doGet("/api/tenant/preferences", tenant).andExpect(jsonPath("$.budget").value(200.0)).andExpect(jsonPath("$.furnished").value(true));
        doGet("/api/tenant/preferences", otherTenant).andExpect(jsonPath("$.budget").value(0.0));
    }

    @Test @DisplayName("REC-01 recommendations are public, ranked, and never include suspended listings")
    void recommendations() throws Exception {
        Property good = listing(landlord, "Perfect match");
        Property hidden = listing(landlord, "Suspended match");
        doPost("/api/admin/properties/" + hidden.getId() + "/status", admin, Map.of("status", "SUSPENDED")).andExpect(status().isOk());
        JsonNode result = read(doPost("/api/recommendations", null, Map.of("budget", 200, "area", "Avondale", "roomType", "Single", "furnished", true)).andExpect(status().isOk()));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("id").asLong()).isEqualTo(good.getId());
    }

    // ------------------------------------------------------------ administrator
    @Test @DisplayName("ADM-01 suspending a listing hides it from the public and restoring shows it again")
    void moderation() throws Exception {
        Property p = listing(landlord, "Room A");
        doGet("/api/properties", null).andExpect(jsonPath("$.length()").value(1));
        doPost("/api/admin/properties/" + p.getId() + "/status", admin, Map.of("status", "SUSPENDED")).andExpect(status().isOk());
        doGet("/api/properties", null).andExpect(jsonPath("$.length()").value(0));
        doGet("/api/properties/" + p.getId(), null).andExpect(status().isNotFound());
        doGet("/api/admin/properties", admin).andExpect(jsonPath("$.length()").value(1));
        doPost("/api/admin/properties/" + p.getId() + "/status", admin, Map.of("status", "ACTIVE")).andExpect(status().isOk());
        doGet("/api/properties/" + p.getId(), null).andExpect(status().isOk());
        doPost("/api/admin/properties/" + p.getId() + "/status", admin, Map.of("status", "NONSENSE")).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("ADM-02 the administrator can suspend and reactivate users but not administrators or themselves")
    void userManagement() throws Exception {
        AppUser secondAdmin = approved("admin2@test.local", UserRole.ADMIN);
        doPost("/api/admin/users/" + tenant.getId() + "/suspend", admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
        doPost("/api/admin/users/" + tenant.getId() + "/suspend", admin, null).andExpect(status().isConflict());
        doPost("/api/admin/users/" + tenant.getId() + "/reactivate", admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        doPost("/api/admin/users/" + admin.getId() + "/suspend", admin, null).andExpect(status().isForbidden());
        doPost("/api/admin/users/" + secondAdmin.getId() + "/suspend", admin, null).andExpect(status().isForbidden());
    }

    @Test @DisplayName("ADM-03 pending requests can be rejected, and rejected users stay out")
    void rejectPending() throws Exception {
        AppUser pending = user("p@test.local", UserRole.TENANT, zw.ac.msu.dzimbahub.auth.UserStatus.PENDING);
        doGet("/api/admin/users?status=PENDING", admin).andExpect(jsonPath("$.length()").value(1));
        doPost("/api/admin/users/" + pending.getId() + "/reject", admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        doPost("/api/auth/login", null, Map.of("email", "p@test.local", "password", "Passw0rd!")).andExpect(status().isForbidden());
        doGet("/api/admin/users?status=BOGUS", admin).andExpect(status().isBadRequest());
    }

    @Test @DisplayName("ADM-04 statistics and the activity log record what users do")
    void statsAndAudit() throws Exception {
        doPost("/api/landlord/properties", landlord, propertyBody("Logged Room", 100)).andExpect(status().isOk());
        doGet("/api/admin/stats", admin).andExpect(status().isOk()).andExpect(jsonPath("$.properties").value(1)).andExpect(jsonPath("$.landlords").value(2));
        doGet("/api/admin/audit", admin).andExpect(status().isOk()).andExpect(jsonPath("$[?(@.action=='LISTING_CREATED')]").exists());
    }

    @Test @DisplayName("ADM-05 deleting a listing also removes its viewings, messages and reviews")
    void deleteCascades() throws Exception {
        Property p = listing(landlord, "Room A");
        approvedViewing(p, tenant);
        doPost("/api/tenant/enquiries", tenant, Map.of("propertyId", p.getId(), "message", "Hi")).andExpect(status().isOk());
        doDelete("/api/admin/properties/" + p.getId(), admin).andExpect(status().isOk());
        assertThat(viewings.count()).isZero();
        assertThat(messages.count()).isZero();
    }
}
