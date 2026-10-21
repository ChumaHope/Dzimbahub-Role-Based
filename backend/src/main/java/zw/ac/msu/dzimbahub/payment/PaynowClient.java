package zw.ac.msu.dzimbahub.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Map;

/** Talks to Paynow: starts a payment and checks its status. All replies are hash-checked. */
@Component
public class PaynowClient {
    private static final Logger log = LoggerFactory.getLogger(PaynowClient.class);

    public record Started(boolean ok, String error, String redirectUrl, String pollUrl, String instructions, String paynowReference) {
        static Started error(String e) { return new Started(false, e, null, null, null, null); }
    }
    public record Status(boolean valid, String status, String reference, String paynowReference, String amount, String error) {
        static Status invalid(String e) { return new Status(false, null, null, null, null, e); }
    }

    private final PaynowHttp http;
    private final String integrationId, integrationKey, authEmail;

    public PaynowClient(PaynowHttp http,
                        @Value("${app.paynow.integration-id:}") String integrationId,
                        @Value("${app.paynow.integration-key:}") String integrationKey,
                        @Value("${app.paynow.auth-email:}") String authEmail) {
        this.http = http; this.integrationId = integrationId.trim(); this.integrationKey = integrationKey.trim(); this.authEmail = authEmail.trim();
    }

    public boolean configured() { return !integrationId.isEmpty() && !integrationKey.isEmpty(); }
    public boolean hasAuthEmail() { return !authEmail.isEmpty(); }

    /** method: null for Paynow's web page, or "ecocash" / "onemoney" with a local mobile number. */
    public Started start(String reference, BigDecimal amount, String info, String returnUrl, String resultUrl, String phone, String method) {
        if (!configured()) return Started.error("Online payments are not set up yet (missing Paynow integration ID or key).");
        boolean mobile = phone != null;
        Map<String, String> req = PaynowProtocol.request(integrationId, integrationKey, reference, amount, info,
                returnUrl, resultUrl, authEmail, phone, method);
        String body;
        try {
            body = http.postForm(mobile ? PaynowProtocol.MOBILE_URL : PaynowProtocol.INITIATE_URL, PaynowProtocol.formEncode(req));
        } catch (Exception e) {
            log.error("Could not reach Paynow: {}", e.getMessage());
            return Started.error("Could not reach Paynow. Please try again in a moment.");
        }
        Map<String, String> r = PaynowProtocol.parse(body);
        if ("error".equalsIgnoreCase(r.get("status")))
            return Started.error(r.getOrDefault("error", "Paynow could not start the payment."));
        if (!PaynowProtocol.verify(r, integrationKey)) {
            log.error("Paynow reply failed the hash check for {}", reference);
            return Started.error("Paynow's reply could not be verified. Please try again.");
        }
        String poll = r.get("pollurl");
        if (poll == null || !poll.startsWith(PaynowProtocol.TRUSTED_PREFIX))
            return Started.error("Paynow did not return a valid status link.");
        return new Started(true, null, r.get("browserurl"), poll, r.get("instructions"), r.get("paynowreference"));
    }

    /** Asks Paynow for the latest status of a payment. */
    public Status poll(String pollUrl) {
        if (pollUrl == null || !pollUrl.startsWith(PaynowProtocol.TRUSTED_PREFIX)) return Status.invalid("Not a Paynow link.");
        try {
            return read(PaynowProtocol.parse(http.postForm(pollUrl, "")));
        } catch (Exception e) {
            log.warn("Could not check a Paynow payment: {}", e.getMessage());
            return Status.invalid("Could not reach Paynow.");
        }
    }

    /** Reads a status message: a poll reply, or a status update Paynow posts to our result URL. */
    public Status read(Map<String, String> fields) {
        if ("error".equalsIgnoreCase(fields.get("status"))) return Status.invalid(fields.getOrDefault("error", "Paynow returned an error."));
        if (!configured() || !PaynowProtocol.verify(fields, integrationKey)) return Status.invalid("Hash check failed.");
        return new Status(true, fields.get("status"), fields.get("reference"), fields.get("paynowreference"), fields.get("amount"), null);
    }
}
