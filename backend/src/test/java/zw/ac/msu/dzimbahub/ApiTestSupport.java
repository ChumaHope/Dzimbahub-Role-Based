package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import zw.ac.msu.dzimbahub.TestSmsConfig.CapturingSmsSender;
import zw.ac.msu.dzimbahub.auth.*;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.*;
import zw.ac.msu.dzimbahub.security.JwtService;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSmsConfig.class)
public abstract class ApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected AppUserRepository users;
    @Autowired protected PropertyRepository properties;
    @Autowired protected ViewingRepository viewings;
    @Autowired protected MessageRepository messages;
    @Autowired protected ReviewRepository reviews;
    @Autowired protected BookingRepository bookings;
    @Autowired protected AuditLogRepository auditLogs;
    @Autowired protected PreferenceRepository preferences;
    @Autowired protected OtpChallengeRepository otps;
    @Autowired protected PropertyImageRepository propertyImages;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtService jwt;
    @Autowired protected CapturingSmsSender sms;

    @BeforeEach
    void cleanDatabase() {
        otps.deleteAll(); preferences.deleteAll(); bookings.deleteAll(); reviews.deleteAll(); messages.deleteAll();
        viewings.deleteAll(); properties.deleteAll(); propertyImages.deleteAll(); auditLogs.deleteAll(); users.deleteAll(); sms.clear();
    }

    // ---------- fixtures
    protected AppUser user(String email, UserRole role, UserStatus status) {
        String phone = "+26377" + String.format("%07d", Math.abs(email.hashCode()) % 10_000_000);
        return users.save(new AppUser(role.name() + " " + email, email, encoder.encode("Passw0rd!"), phone, role, status));
    }
    protected AppUser approved(String email, UserRole role) { return user(email, role, UserStatus.APPROVED); }
    protected String bearer(AppUser u) { return "Bearer " + jwt.generate(u.getEmail(), u.getRole().name()); }

    protected Property listing(AppUser owner, String title) {
        Property p = new Property();
        p.setTitle(title); p.setArea("Avondale"); p.setCity("Harare"); p.setAddress("1 Test Road"); p.setDescription("Test");
        p.setPrice(200); p.setRoomType("Single"); p.setBedrooms(1); p.setBathrooms(1); p.setFurnished(true);
        p.setLatitude(-17.8); p.setLongitude(31.0); p.setImageUrl("https://example.com/a.jpg");
        p.setOwnerId(owner.getId()); p.setLandlordName(owner.getFullName());
        return properties.save(p);
    }

    protected Map<String, Object> propertyBody(String title, double price) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title); m.put("area", "Avondale"); m.put("city", "Harare"); m.put("address", "1 Test Road");
        m.put("description", "A test room"); m.put("price", price); m.put("roomType", "Single");
        m.put("bedrooms", 1); m.put("bathrooms", 1); m.put("furnished", true);
        m.put("latitude", -17.8); m.put("longitude", 31.0); m.put("imageUrl", ""); m.put("amenities", "WiFi,Security");
        return m;
    }

    // ---------- HTTP helpers
    private MockHttpServletRequestBuilder with(MockHttpServletRequestBuilder b, AppUser as, Object body) throws Exception {
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (as != null) b.header("Authorization", bearer(as));
        return b;
    }
    protected ResultActions doGet(String url, AppUser as) throws Exception { return mvc.perform(with(MockMvcRequestBuilders.get(url), as, null)); }
    protected ResultActions doPost(String url, AppUser as, Object body) throws Exception { return mvc.perform(with(MockMvcRequestBuilders.post(url), as, body == null ? Map.of() : body)); }
    protected ResultActions doPut(String url, AppUser as, Object body) throws Exception { return mvc.perform(with(MockMvcRequestBuilders.put(url), as, body)); }
    protected ResultActions doDelete(String url, AppUser as) throws Exception { return mvc.perform(with(MockMvcRequestBuilders.delete(url), as, null)); }

    protected JsonNode read(ResultActions r) throws Exception {
        MvcResult res = r.andReturn();
        return json.readTree(res.getResponse().getContentAsString());
    }
}
