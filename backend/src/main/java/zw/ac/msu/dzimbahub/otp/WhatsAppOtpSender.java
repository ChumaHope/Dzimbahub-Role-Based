package zw.ac.msu.dzimbahub.otp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Sends the sign-in code through the WhatsApp Business Cloud API (Meta) using an approved
 * AUTHENTICATION template with a "copy code" button. Enable with OTP_CHANNEL=whatsapp.
 *
 * Required settings: WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_ACCESS_TOKEN, WHATSAPP_TEMPLATE_NAME.
 * If WHATSAPP_FALLBACK_TO_SMS=true (default) and WhatsApp fails, the code is sent by SMS instead.
 */
@Component
@ConditionalOnProperty(name = "app.otp.channel", havingValue = "whatsapp")
public class WhatsAppOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(WhatsAppOtpSender.class);
    private final String phoneNumberId, accessToken, templateName, languageCode, apiVersion, defaultCountryCode;
    private final boolean fallbackToSms;
    private final SmsSender sms;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public WhatsAppOtpSender(@Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                             @Value("${app.whatsapp.access-token:}") String accessToken,
                             @Value("${app.whatsapp.template-name:}") String templateName,
                             @Value("${app.whatsapp.language-code:en}") String languageCode,
                             @Value("${app.whatsapp.api-version:v26.0}") String apiVersion,
                             @Value("${app.whatsapp.default-country-code:263}") String defaultCountryCode,
                             @Value("${app.whatsapp.fallback-to-sms:true}") boolean fallbackToSms,
                             SmsSender sms) {
        if (phoneNumberId.isBlank() || accessToken.isBlank() || templateName.isBlank())
            throw new IllegalStateException("WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_ACCESS_TOKEN and WHATSAPP_TEMPLATE_NAME must be set when OTP_CHANNEL=whatsapp.");
        this.phoneNumberId = phoneNumberId; this.accessToken = accessToken; this.templateName = templateName;
        this.languageCode = languageCode; this.apiVersion = apiVersion; this.defaultCountryCode = defaultCountryCode;
        this.fallbackToSms = fallbackToSms; this.sms = sms;
    }

    /**
     * WhatsApp wants the number in international format with digits only.
     * "+263 77 123 4567" -> "263771234567"; local "0771234567" -> "263771234567".
     */
    static String toWhatsAppNumber(String phone, String defaultCountryCode) {
        String digits = phone == null ? "" : phone.replaceAll("[^0-9]", "");
        if (phone != null && phone.trim().startsWith("+")) return digits;
        if (digits.startsWith("00")) return digits.substring(2);
        if (digits.startsWith("0")) return defaultCountryCode + digits.substring(1);
        return digits;
    }

    /** The request body for an authentication template: the code goes in the body AND the copy-code button. */
    static Map<String, Object> templatePayload(String to, String templateName, String languageCode, String code) {
        List<Map<String, String>> codeParam = List.of(Map.of("type", "text", "text", code));
        return Map.of(
                "messaging_product", "whatsapp",
                "recipient_type", "individual",
                "to", to,
                "type", "template",
                "template", Map.of(
                        "name", templateName,
                        "language", Map.of("code", languageCode),
                        "components", List.of(
                                Map.of("type", "body", "parameters", codeParam),
                                Map.of("type", "button", "sub_type", "url", "index", "0", "parameters", codeParam))));
    }

    @Override public Channel sendCode(String phone, String code, long ttlMinutes) {
        String to = toWhatsAppNumber(phone, defaultCountryCode);
        try {
            String body = json.writeValueAsString(templatePayload(to, templateName, languageCode, code));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 300) return Channel.WHATSAPP;
            log.error("WhatsApp rejected the sign-in code for {} (HTTP {}): {}", mask(to), res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sending the WhatsApp code was interrupted.");
        } catch (Exception e) {
            log.error("Could not reach WhatsApp: {}", e.getMessage());
        }
        if (!fallbackToSms) throw new OtpDeliveryException("We could not send your code by WhatsApp. Please try again shortly.");
        log.warn("Falling back to SMS for {}", mask(to));
        sms.send(phone, OtpSender.smsText(code, ttlMinutes));
        return Channel.SMS;
    }

    private static String mask(String n) { return n.length() <= 3 ? "***" : "***" + n.substring(n.length() - 3); }
}
