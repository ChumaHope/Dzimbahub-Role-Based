package zw.ac.msu.dzimbahub.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Sends real text messages through Twilio's REST API. Enable with SMS_PROVIDER=twilio. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "twilio")
public class TwilioSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(TwilioSmsSender.class);
    private final String accountSid, authToken, from;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public TwilioSmsSender(@Value("${app.sms.twilio.account-sid}") String accountSid,
                           @Value("${app.sms.twilio.auth-token}") String authToken,
                           @Value("${app.sms.twilio.from}") String from) {
        if (accountSid.isBlank() || authToken.isBlank() || from.isBlank())
            throw new IllegalStateException("TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM must be set when SMS_PROVIDER=twilio.");
        this.accountSid = accountSid; this.authToken = authToken; this.from = from;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    @Override public void send(String toPhone, String message) {
        try {
            String form = "To=" + enc(toPhone) + "&From=" + enc(from) + "&Body=" + enc(message);
            String basic = Base64.getEncoder().encodeToString((accountSid + ":" + authToken).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.twilio.com/2010-04-01/Accounts/" + accountSid + "/Messages.json"))
                    .timeout(Duration.ofSeconds(15)).header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 300) log.error("Twilio rejected the message (HTTP {}): {}", res.statusCode(), res.body());
        } catch (Exception e) {
            log.error("Could not send SMS through Twilio: {}", e.getMessage());
        }
    }
}
