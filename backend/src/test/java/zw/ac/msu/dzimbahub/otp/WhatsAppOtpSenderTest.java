package zw.ac.msu.dzimbahub.otp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WhatsAppOtpSenderTest {

    @Test @DisplayName("WA-01 phone numbers are converted to WhatsApp's digits-only international format")
    void normalisesNumbers() {
        assertThat(WhatsAppOtpSender.toWhatsAppNumber("+263771234567", "263")).isEqualTo("263771234567");
        assertThat(WhatsAppOtpSender.toWhatsAppNumber("+263 77 123 4567", "263")).isEqualTo("263771234567");
        assertThat(WhatsAppOtpSender.toWhatsAppNumber("0771234567", "263")).isEqualTo("263771234567");
        assertThat(WhatsAppOtpSender.toWhatsAppNumber("00263771234567", "263")).isEqualTo("263771234567");
        assertThat(WhatsAppOtpSender.toWhatsAppNumber("263771234567", "263")).isEqualTo("263771234567");
    }

    @Test @DisplayName("WA-02 the authentication template carries the code in the body and the copy-code button")
    void buildsTemplatePayload() throws Exception {
        String body = new ObjectMapper().writeValueAsString(
                WhatsAppOtpSender.templatePayload("263771234567", "dzimbahub_login_code", "en", "123456"));
        JsonNode n = new ObjectMapper().readTree(body);
        assertThat(n.path("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(n.path("to").asText()).isEqualTo("263771234567");
        assertThat(n.path("template").path("name").asText()).isEqualTo("dzimbahub_login_code");
        JsonNode components = n.path("template").path("components");
        assertThat(components.get(0).path("parameters").get(0).path("text").asText()).isEqualTo("123456");
        assertThat(components.get(1).path("sub_type").asText()).isEqualTo("url");
        assertThat(components.get(1).path("index").asText()).isEqualTo("0");
        assertThat(components.get(1).path("parameters").get(0).path("text").asText()).isEqualTo("123456");
    }
}
