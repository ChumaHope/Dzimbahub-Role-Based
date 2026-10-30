package zw.ac.msu.dzimbahub;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import zw.ac.msu.dzimbahub.model.Property;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A paid booking reserves the home; nobody else can take it until the landlord makes it available again. */
class ReservationTest extends ApiTestSupport {
    AppUser admin, landlord, otherLandlord, tenant, otherTenant;
    Property home;

    @BeforeEach
    void setUp() {
        admin = approved("admin@test.local", UserRole.ADMIN);
        landlord = approved("landlord@test.local", UserRole.LANDLORD);
        otherLandlord = approved("landlord2@test.local", UserRole.LANDLORD);
        tenant = approved("tenant@test.local", UserRole.TENANT);
        otherTenant = approved("tenant2@test.local", UserRole.TENANT);
        home = listing(landlord, "Sunrise Lodge");
    }

    private long approvedViewing(AppUser who) throws Exception {
        long id = read(doPost("/api/tenant/viewings", who, Map.of("propertyId", home.getId(), "date", "2099-12-31", "time", "10:00"))
                .andExpect(status().isOk())).get("id").asLong();
        doPost("/api/landlord/viewings/" + id + "/decision", landlord, Map.of("approve", true)).andExpect(status().isOk());
        return id;
    }

    private long booking(AppUser who) throws Exception {
        return read(doPost("/api/tenant/bookings", who, Map.of("propertyId", home.getId())).andExpect(status().isOk())).get("id").asLong();
    }

    @Test @DisplayName("RES-01 paying reserves the home; others can no longer view, book or pay for it")
    void paymentReservesHome() throws Exception {
        approvedViewing(tenant); approvedViewing(otherTenant);
        long mine = booking(tenant), theirs = booking(otherTenant);
        doGet("/api/properties/" + home.getId(), null).andExpect(jsonPath("$.reserved").value(false));

        doPost("/api/tenant/bookings/" + mine + "/pay", tenant, null).andExpect(status().isOk());
        doGet("/api/properties/" + home.getId(), null).andExpect(jsonPath("$.reserved").value(true));

        doPost("/api/tenant/bookings/" + theirs + "/pay", otherTenant, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This home has already been reserved by another tenant."));
        AppUser third = approved("tenant3@test.local", UserRole.TENANT);
        doPost("/api/tenant/viewings", third, Map.of("propertyId", home.getId(), "date", "2099-12-31", "time", "10:00")).andExpect(status().isConflict());

        // still visible (marked reserved) in Find Houses, but never recommended
        doGet("/api/properties", null).andExpect(jsonPath("$[0].reserved").value(true));
        doPost("/api/recommendations", null, Map.of("budget", 200, "area", "Avondale", "roomType", "Single", "furnished", true))
                .andExpect(status().isOk()).andExpect(jsonPath("$.results.length()").value(0));
        doGet("/api/admin/stats", admin).andExpect(jsonPath("$.reservedProperties").value(1));
    }

    @Test @DisplayName("RES-02 the landlord can make the home available again; other landlords and tenants cannot")
    void makeAvailableAgain() throws Exception {
        approvedViewing(tenant);
        doPost("/api/tenant/bookings/" + booking(tenant) + "/pay", tenant, null).andExpect(status().isOk());

        doPost("/api/landlord/properties/" + home.getId() + "/make-available", otherLandlord, null).andExpect(status().isForbidden());
        doPost("/api/landlord/properties/" + home.getId() + "/make-available", tenant, null).andExpect(status().isForbidden());
        doPost("/api/landlord/properties/" + home.getId() + "/make-available", landlord, null)
                .andExpect(status().isOk()).andExpect(jsonPath("$.reserved").value(false));
        doPost("/api/landlord/properties/" + home.getId() + "/make-available", landlord, null).andExpect(status().isConflict());
        assertThat(properties.findById(home.getId()).orElseThrow().isReserved()).isFalse();
        doPost("/api/tenant/viewings", otherTenant, Map.of("propertyId", home.getId(), "date", "2099-12-31", "time", "10:00")).andExpect(status().isOk());
    }
}
