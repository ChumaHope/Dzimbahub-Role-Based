package zw.ac.msu.dzimbahub.otp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Sends the sign-in code on WhatsApp through Twilio (works with the free Twilio WhatsApp Sandbox,
 * no Meta business verification needed). Enable with OTP_CHANNEL=twilio-whatsapp.
 *
 * Uses the same TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN as SMS.
 * TWILIO_WHATSAPP_FROM defaults to the sandbox number whatsapp:+14155238886.
 * If TWILIO_WHATSAPP_CONTENT_SID is set, the approved template "Your {{1}} code is {{2}}" is used;
 * otherwise the same text is sent as a normal message.
 * Each tester must first send "join <sandbox-word>" to the sandbox number (again every 3 days).
 */
@Component
@ConditionalOnProperty(name = "app.otp.channel", havingValue = "twilio-whatsapp")
public class TwilioWhatsAppOtpSender implements OtpSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioWhatsAppOtpSender.class);
    private final String accountSid, authToken, from, contentSid, defaultCountryCode;
    private final boolean fallbackToSms;
    private final SmsSender sms;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TwilioWhatsAppOtpSender(@Value("${app.sms.twilio.account-sid:}") String accountSid,
                                   @Value("${app.sms.twilio.auth-token:}") String authToken,
                                   @Value("${app.twilio.whatsapp.from:whatsapp:+14155238886}") String from,
                                   @Value("${app.twilio.whatsapp.content-sid:}") String contentSid,
                                   @Value("${app.whatsapp.default-country-code:263}") String defaultCountryCode,
                                   @Value("${app.whatsapp.fallback-to-sms:true}") boolean fallbackToSms,
                                   SmsSender sms) {
        if (accountSid.isBlank() || authToken.isBlank())
            throw new IllegalStateException("TWILIO_ACCOUNT_SID and TWILIO_AUTH_TOKEN must be set when OTP_CHANNEL=twilio-whatsapp.");
        this.accountSid = accountSid; this.authToken = authToken;
        this.from = from.startsWith("whatsapp:") ? from : "whatsapp:" + from;
        this.contentSid = contentSid; this.defaultCountryCode = defaultCountryCode;
        this.fallbackToSms = fallbackToSms; this.sms = sms;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    /** "0771234567" -> "whatsapp:+263771234567" */
    static String toTwilioWhatsApp(String phone, String defaultCountryCode) {
        return "whatsapp:+" + WhatsAppOtpSender.toWhatsAppNumber(phone, defaultCountryCode);
    }

    /** Matches the sandbox's pre-approved "Verification Codes" template: "Your {{1}} code is {{2}}". */
    static String templateText(String code) { return "Your DzimbaHub code is " + code; }

    String formBody(String to, String code) throws Exception {
        String form = "To=" + enc(to) + "&From=" + enc(from);
        if (!contentSid.isBlank()) {
            String vars = json.writeValueAsString(Map.of("1", "DzimbaHub", "2", code));
            return form + "&ContentSid=" + enc(contentSid) + "&ContentVariables=" + enc(vars);
        }
        return form + "&Body=" + enc(templateText(code));
    }

    @Override public Channel sendCode(String phone, String code, long ttlMinutes) {
        String to = toTwilioWhatsApp(phone, defaultCountryCode);
        try {
            String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(formBody(to, code))).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() < 300) return Channel.WHATSAPP;
            log.error("Twilio rejected the WhatsApp code (HTTP {}): {}", res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Sending the WhatsApp code was interrupted.");
        } catch (Exception e) {
            log.error("Could not reach Twilio for WhatsApp: {}", e.getMessage());
        }
        if (!fallbackToSms) throw new OtpDeliveryException("We could not send your code by WhatsApp. Please try again shortly.");
        log.warn("Falling back to SMS for the sign-in code.");
        sms.send(phone, OtpSender.smsText(code, ttlMinutes));
        return Channel.SMS;
    }
}
