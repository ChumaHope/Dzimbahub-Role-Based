package zw.ac.msu.dzimbahub;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.UserRole;
import zw.ac.msu.dzimbahub.model.Booking;
import zw.ac.msu.dzimbahub.model.BookingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.payment.PaymentRepository;
import zw.ac.msu.dzimbahub.payment.PaynowHttp;
import zw.ac.msu.dzimbahub.payment.PaynowProtocol;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paynow payments, against a fake Paynow that signs its replies exactly like the real one. */
@TestPropertySource(properties = {"app.payments.provider=paynow", "app.paynow.integration-id=1201",
        "app.paynow.integration-key=" + PaymentFlowTest.KEY, "app.paynow.auth-email=merchant@test.local",
        "app.public-api-url=https://api.test", "app.frontend-url=https://web.test"})
@Import(PaymentFlowTest.FakePaynowConfig.class)
class PaymentFlowTest extends ApiTestSupport {
    static final String KEY = "3e9fed89-60e1-4ce5-ab6e-6b1eb2d4f977";
    static final String POLL = "https://www.paynow.co.zw/Interface/CheckPayment/?guid=test";

    /** Pretends to be Paynow. pollStatus decides what a status check returns. */
    static class FakePaynow implements PaynowHttp {
        final List<Map<String, String>> requests = new ArrayList<>();
        volatile String pollStatus = "Sent";
        volatile String pollAmount = "270.00";
        String lastReference;

        static String signed(Map<String, String> m) { m.put("hash", PaynowProtocol.hash(m, KEY)); return PaynowProtocol.formEncode(m); }

        @Override public String postForm(String url, String body) {
            Map<String, String> req = PaynowProtocol.parse(body);
            requests.add(req);
            Map<String, String> r = new LinkedHashMap<>();
            if (url.equals(PaynowProtocol.MOBILE_URL)) {
                if (!PaynowProtocol.verify(req, KEY)) return "status=Error&error=Invalid+hash";
                if ("0774444444".equals(req.get("phone"))) return "status=Error&error=Insufficient+balance";
                lastReference = req.get("reference");
                r.put("status", "Ok"); r.put("instructions", "Enter your PIN on your phone"); r.put("paynowreference", "990001"); r.put("pollurl", POLL);
                return signed(r);
            }
            if (url.equals(PaynowProtocol.INITIATE_URL)) {
                lastReference = req.get("reference");
                r.put("status", "Ok"); r.put("browserurl", "https://www.paynow.co.zw/Payment/ConfirmPayment/1"); r.put("pollurl", POLL);
                return signed(r);
            }
            if (url.equals(POLL)) {
                r.put("reference", lastReference); r.put("paynowreference", "990001"); r.put("amount", pollAmount);
                r.put("status", pollStatus); r.put("pollurl", POLL);
                return signed(r);
            }
            throw new IllegalStateException("Unexpected URL " + url);
        }
    }

    @TestConfiguration
    static class FakePaynowConfig {
        @Bean @Primary FakePaynow fakePaynow() { return new FakePaynow(); }
    }

    @Autowired FakePaynow paynow;
    @Autowired PaymentRepository payments;

    AppUser tenant, otherTenant, landlord;
    Booking booking;

    @BeforeEach
    void setUp() {
        payments.deleteAll();
        paynow.requests.clear(); paynow.pollStatus = "Sent"; paynow.pollAmount = "270.00";
        tenant = approved("tenant@test.local", UserRole.TENANT);
        otherTenant = approved("tenant2@test.local", UserRole.TENANT);
        landlord = approved("landlord@test.local", UserRole.LANDLORD);
        Property p = listing(landlord, "Sunrise Lodge");
        p.setPrice(270); properties.save(p);
        booking = bookings.save(new Booking(p.getId(), tenant.getId(), 270));
    }

    private JsonNode pay(String method, String phone) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(); body.put("method", method); if (phone != null) body.put("phone", phone);
        return read(doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", tenant, body).andExpect(status().isOk()));
    }

    @Test @DisplayName("PAY-01 EcoCash: a signed request is sent, and a Paid status confirms the booking")
    void ecocashPaid() throws Exception {
        doGet("/api/payments/config", null).andExpect(jsonPath("$.provider").value("PAYNOW"));
        JsonNode p = pay("ECOCASH", "+263 77 111 1111");
        assertThat(p.get("status").asText()).isEqualTo("PENDING");
        assertThat(p.get("message").asText()).contains("PIN");

        Map<String, String> sent = paynow.requests.get(0);
        assertThat(sent.get("phone")).isEqualTo("0771111111");
        assertThat(sent.get("method")).isEqualTo("ecocash");
        assertThat(sent.get("amount")).isEqualTo("270.00");
        assertThat(sent.get("authemail")).isEqualTo("merchant@test.local");
        assertThat(sent.get("resulturl")).isEqualTo("https://api.test/api/payments/paynow/result");
        assertThat(PaynowProtocol.verify(sent, KEY)).isTrue();

        long id = p.get("id").asLong();
        doPost("/api/tenant/payments/" + id + "/check", tenant, null).andExpect(jsonPath("$.status").value("PENDING"));
        doPost("/api/tenant/payments/" + id + "/check", otherTenant, null).andExpect(status().isForbidden());
        paynow.pollStatus = "Paid";
        doPost("/api/tenant/payments/" + id + "/check", tenant, null).andExpect(jsonPath("$.status").value("PAID"));

        Booking b = bookings.findById(booking.getId()).orElseThrow();
        assertThat(b.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
        assertThat(b.getPaymentRef()).isEqualTo("PN-990001");
        doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", tenant, Map.of("method", "ECOCASH", "phone", "0771111111")).andExpect(status().isConflict());
    }

    @Test @DisplayName("PAY-02 Paynow's signed status update confirms the booking; a forged one is rejected")
    void resultUrl() throws Exception {
        pay("ONEMONEY", "0771111111");
        String ref = paynow.lastReference;

        Map<String, String> forged = new LinkedHashMap<>();
        forged.put("reference", ref); forged.put("paynowreference", "1"); forged.put("amount", "270.00"); forged.put("status", "Paid");
        forged.put("pollurl", POLL); forged.put("hash", "ABC");
        mvc.perform(post("/api/payments/paynow/result").contentType(MediaType.APPLICATION_FORM_URLENCODED).content(PaynowProtocol.formEncode(forged)))
                .andExpect(status().isBadRequest());
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);

        Map<String, String> real = new LinkedHashMap<>();
        real.put("reference", ref); real.put("paynowreference", "990001"); real.put("amount", "270.00"); real.put("status", "Paid"); real.put("pollurl", POLL);
        mvc.perform(post("/api/payments/paynow/result").contentType(MediaType.APPLICATION_FORM_URLENCODED).content(FakePaynow.signed(real)))
                .andExpect(status().isOk());
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test @DisplayName("PAY-03 a cancelled or declined payment leaves the booking unpaid and can be retried")
    void failures() throws Exception {
        JsonNode broke = pay("ECOCASH", "0774444444");
        assertThat(broke.get("status").asText()).isEqualTo("FAILED");
        assertThat(broke.get("message").asText()).contains("Insufficient balance");

        long id = pay("ECOCASH", "0773333333").get("id").asLong();
        paynow.pollStatus = "Cancelled";
        doPost("/api/tenant/payments/" + id + "/check", tenant, null).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
        doGet("/api/tenant/payments", tenant).andExpect(jsonPath("$.length()").value(2));
    }

    @Test @DisplayName("PAY-04 a Paid status with the wrong amount does not confirm the booking")
    void amountMismatch() throws Exception {
        long id = pay("ECOCASH", "0771111111").get("id").asLong();
        paynow.pollStatus = "Paid"; paynow.pollAmount = "1.00";
        doPost("/api/tenant/payments/" + id + "/check", tenant, null).andExpect(jsonPath("$.status").value("FAILED"));
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.PENDING);
    }

    @Test @DisplayName("PAY-05 card/web payments return Paynow's page; inputs and ownership are checked; simulated pay is off")
    void webAndValidation() throws Exception {
        JsonNode web = pay("WEB", null);
        assertThat(web.get("redirectUrl").asText()).startsWith("https://www.paynow.co.zw/");
        assertThat(paynow.requests.get(0).get("returnurl")).isEqualTo("https://web.test/dashboard?payment=" + web.get("id").asLong());

        doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", tenant, Map.of("method", "ECOCASH", "phone", "12345")).andExpect(status().isBadRequest());
        doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", tenant, Map.of("method", "BITCOIN")).andExpect(status().isBadRequest());
        doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", otherTenant, Map.of("method", "WEB")).andExpect(status().isForbidden());
        doPost("/api/tenant/bookings/" + booking.getId() + "/paynow", landlord, Map.of("method", "WEB")).andExpect(status().isForbidden());
        doPost("/api/tenant/bookings/" + booking.getId() + "/pay", tenant, null).andExpect(status().isConflict());
    }
}
