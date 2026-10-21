package zw.ac.msu.dzimbahub.otp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class TwilioWhatsAppOtpSenderTest {
    private static TwilioWhatsAppOtpSender sender(String contentSid) {
        return new TwilioWhatsAppOtpSender("ACtest", "token", "whatsapp:+14155238886", contentSid, "263", true, (to, msg) -> {});
    }

    @Test @DisplayName("TWA-01 numbers are sent to Twilio as whatsapp:+<international number>")
    void formatsNumbers() {
        assertThat(TwilioWhatsAppOtpSender.toTwilioWhatsApp("0771234567", "263")).isEqualTo("whatsapp:+263771234567");
        assertThat(TwilioWhatsAppOtpSender.toTwilioWhatsApp("+263771234567", "263")).isEqualTo("whatsapp:+263771234567");
    }

    @Test @DisplayName("TWA-02 without a ContentSid the text matches the sandbox template 'Your {{1}} code is {{2}}'")
    void plainBody() throws Exception {
        String form = URLDecoder.decode(sender("").formBody("whatsapp:+263771234567", "123456"), StandardCharsets.UTF_8);
        assertThat(form).contains("From=whatsapp:+14155238886").contains("To=whatsapp:+263771234567")
                .contains("Body=Your DzimbaHub code is 123456");
    }

    @Test @DisplayName("TWA-03 with a ContentSid the approved template and its variables are used")
    void templateBody() throws Exception {
        String form = URLDecoder.decode(sender("HX123").formBody("whatsapp:+263771234567", "123456"), StandardCharsets.UTF_8);
        assertThat(form).contains("ContentSid=HX123").contains("\"2\":\"123456\"").doesNotContain("Body=");
    }
}
