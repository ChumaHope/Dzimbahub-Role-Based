package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Admin approval of landlord listings, and photos uploaded from the landlord's device. */
class ListingApprovalTest extends ApiTestSupport {
    /** The smallest valid PNG file (1x1 pixel). */
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R',
            0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0, 0x1F, 0x15, (byte) 0xC4, (byte) 0x89};

    AppUser admin, landlord, otherLandlord, tenant;

    @BeforeEach
    void people() {
        admin = approved("admin@test.local", UserRole.ADMIN);
        landlord = approved("landlord@test.local", UserRole.LANDLORD);
        otherLandlord = approved("landlord2@test.local", UserRole.LANDLORD);
        tenant = approved("tenant@test.local", UserRole.TENANT);
    }

    private ResultActions upload(AppUser as, byte[] bytes, String name) throws Exception {
        return mvc.perform(multipart("/api/landlord/images").file(new MockMultipartFile("file", name, "image/png", bytes))
                .header("Authorization", bearer(as)));
    }

    private String uploadedUrl(AppUser as) throws Exception {
        return read(upload(as, PNG, "room.png").andExpect(status().isOk())).get("url").asText();
    }

    @Test @DisplayName("LST-01 a new listing waits for the administrator and is hidden from the public until approved")
    void newListingNeedsApproval() throws Exception {
        long id = read(doPost("/api/landlord/properties", landlord, propertyBody("New Room", 150))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("PENDING"))).get("id").asLong();
        doGet("/api/properties", null).andExpect(jsonPath("$.length()").value(0));
        doGet("/api/properties/" + id, null).andExpect(status().isNotFound());
        doPost("/api/tenant/viewings", tenant, Map.of("propertyId", id, "date", "2099-12-31", "time", "10:00")).andExpect(status().isNotFound());
        doGet("/api/admin/properties/pending", admin).andExpect(jsonPath("$.length()").value(1));
        doGet("/api/admin/stats", admin).andExpect(jsonPath("$.pendingListings").value(1));

        sms.clear();
        doPost("/api/admin/properties/" + id + "/approve", admin, null).andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("ACTIVE"));
        doGet("/api/properties/" + id, null).andExpect(status().isOk());
        assertThat(sms.last().text()).contains("approved");
        doGet("/api/admin/properties/pending", admin).andExpect(jsonPath("$.length()").value(0));
    }

    @Test @DisplayName("LST-02 a rejection needs a reason, the landlord sees it, and editing resubmits the listing")
    void rejectAndResubmit() throws Exception {
        long id = read(doPost("/api/landlord/properties", landlord, propertyBody("Dark Room", 150)).andExpect(status().isOk())).get("id").asLong();
        doPost("/api/admin/properties/" + id + "/reject", admin, Map.of("reason", " ")).andExpect(status().isBadRequest());
        doPost("/api/admin/properties/" + id + "/reject", admin, Map.of("reason", "Photos are too dark"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("REJECTED"));
        assertThat(sms.last().text()).contains("Photos are too dark");
        doGet("/api/landlord/properties", landlord).andExpect(jsonPath("$[0].reviewNote").value("Photos are too dark"));
        doGet("/api/properties", null).andExpect(jsonPath("$.length()").value(0));

        JsonNode resubmitted = read(doPut("/api/landlord/properties/" + id, landlord, propertyBody("Bright Room", 150))
                .andExpect(status().isOk()).andExpect(jsonPath("$.listingStatus").value("PENDING")));
        assertThat(resubmitted.path("reviewNote").isMissingNode() || resubmitted.get("reviewNote").isNull()).isTrue();
    }

    @Test @DisplayName("LST-03 editing an approved listing hides it again until re-approved; a suspended listing stays suspended")
    void editingNeedsReapproval() throws Exception {
        long id = read(doPost("/api/landlord/properties", landlord, propertyBody("Room", 150)).andExpect(status().isOk())).get("id").asLong();
        doPost("/api/admin/properties/" + id + "/approve", admin, null).andExpect(status().isOk());
        doPut("/api/landlord/properties/" + id, landlord, propertyBody("Room (cheaper)", 120))
                .andExpect(jsonPath("$.listingStatus").value("PENDING"));
        doGet("/api/properties", null).andExpect(jsonPath("$.length()").value(0));

        doPost("/api/admin/properties/" + id + "/status", admin, Map.of("status", "SUSPENDED")).andExpect(status().isOk());
        doPut("/api/landlord/properties/" + id, landlord, propertyBody("Room again", 120))
                .andExpect(jsonPath("$.listingStatus").value("SUSPENDED"));
    }

    @Test @DisplayName("LST-04 landlords cannot approve listings")
    void onlyAdminApproves() throws Exception {
        long id = read(doPost("/api/landlord/properties", landlord, propertyBody("Room", 150)).andExpect(status().isOk())).get("id").asLong();
        doPost("/api/admin/properties/" + id + "/approve", landlord, null).andExpect(status().isForbidden());
        doPost("/api/admin/properties/" + id + "/approve", tenant, null).andExpect(status().isForbidden());
    }

    @Test @DisplayName("IMG-01 a landlord uploads a photo from their device; anyone can view it")
    void uploadAndView() throws Exception {
        String url = uploadedUrl(landlord);
        assertThat(url).matches("/api/images/\\d+");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(url))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(content().bytes(PNG));
    }

    @Test @DisplayName("IMG-02 only real photos are accepted, and only landlords can upload")
    void uploadRules() throws Exception {
        upload(landlord, "<script>alert(1)</script>".getBytes(), "evil.png").andExpect(status().isBadRequest());
        upload(landlord, new byte[0], "empty.png").andExpect(status().isBadRequest());
        upload(tenant, PNG, "room.png").andExpect(status().isForbidden());
        assertThat(propertyImages.count()).isZero();
    }

    @Test @DisplayName("IMG-03 uploaded photos become the listing gallery; the first is the cover")
    void photosOnListing() throws Exception {
        String a = uploadedUrl(landlord), b = uploadedUrl(landlord);
        Map<String, Object> body = propertyBody("Photo Room", 150); body.put("photos", List.of(a, b));
        JsonNode created = read(doPost("/api/landlord/properties", landlord, body).andExpect(status().isOk()));
        assertThat(created.get("imageUrl").asText()).isEqualTo(a);
        assertThat(created.get("photos")).hasSize(2);

        // removing a photo from the listing deletes the stored file
        body.put("photos", List.of(b));
        doPut("/api/landlord/properties/" + created.get("id").asLong(), landlord, body).andExpect(jsonPath("$.imageUrl").value(b));
        assertThat(propertyImages.count()).isEqualTo(1);

        // deleting the listing deletes its photos
        doDelete("/api/landlord/properties/" + created.get("id").asLong(), landlord).andExpect(status().isOk());
        assertThat(propertyImages.count()).isZero();
    }

    @Test @DisplayName("IMG-04 a landlord cannot attach another landlord's photo")
    void cannotUseOthersPhoto() throws Exception {
        String theirs = uploadedUrl(otherLandlord);
        Map<String, Object> body = propertyBody("Stolen", 150); body.put("photos", List.of(theirs));
        doPost("/api/landlord/properties", landlord, body).andExpect(status().isForbidden());
        Map<String, Object> bad = propertyBody("Bad", 150); bad.put("photos", List.of("javascript:alert(1)"));
        doPost("/api/landlord/properties", landlord, bad).andExpect(status().isBadRequest());
    }
}
