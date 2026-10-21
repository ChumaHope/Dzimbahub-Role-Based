package zw.ac.msu.dzimbahub.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Paynow's message format, in plain Java so it can be tested on its own.
 * Requests and replies are URL-encoded forms. Each one carries a "hash": SHA-512 (upper-case hex) of all the other
 * values joined together in order, followed by the integration key (as in Paynow's official SDKs).
 */
public final class PaynowProtocol {
    public static final String INITIATE_URL = "https://www.paynow.co.zw/interface/initiatetransaction";
    public static final String MOBILE_URL = "https://www.paynow.co.zw/interface/remotetransaction";
    /** Poll links must point at Paynow, so a forged link can never make our server call somewhere else. */
    public static final String TRUSTED_PREFIX = "https://www.paynow.co.zw/";

    private PaynowProtocol() {}

    public static String hash(Map<String, String> values, String integrationKey) {
        StringBuilder sb = new StringBuilder();
        values.forEach((k, v) -> { if (!"hash".equalsIgnoreCase(k)) sb.append(v == null ? "" : v); });
        sb.append(integrationKey.trim().toLowerCase(Locale.ROOT));
        try {
            byte[] d = MessageDigest.getInstance("SHA-512").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : d) hex.append(String.format("%02X", b));
            return hex.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-512 is not available", e); }
    }

    /** True if the reply carries a hash and it matches. Comparison does not stop early. */
    public static boolean verify(Map<String, String> values, String integrationKey) {
        String given = values.entrySet().stream().filter(e -> "hash".equalsIgnoreCase(e.getKey()))
                .map(Map.Entry::getValue).findFirst().orElse(null);
        if (given == null) return false;
        return MessageDigest.isEqual(given.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                hash(values, integrationKey).getBytes(StandardCharsets.UTF_8));
    }

    /** Builds a request in the field order the SDKs use and signs it. phone/method only for EcoCash / OneMoney. */
    public static Map<String, String> request(String integrationId, String integrationKey, String reference, BigDecimal amount,
                                              String additionalInfo, String returnUrl, String resultUrl, String authEmail,
                                              String phone, String method) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("resulturl", resultUrl);
        m.put("returnurl", returnUrl);
        m.put("reference", reference);
        m.put("amount", money(amount));
        m.put("id", integrationId.trim());
        m.put("additionalinfo", additionalInfo == null ? "" : additionalInfo);
        m.put("authemail", authEmail == null ? "" : authEmail.trim());
        if (phone != null) { m.put("phone", phone); m.put("method", method); }
        m.put("status", "Message");
        m.put("hash", hash(m, integrationKey));
        return m;
    }

    public static String money(BigDecimal amount) { return amount.setScale(2, RoundingMode.HALF_UP).toPlainString(); }

    public static String formEncode(Map<String, String> m) {
        StringJoiner j = new StringJoiner("&");
        m.forEach((k, v) -> j.add(URLEncoder.encode(k, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8)));
        return j.toString();
    }

    /** Reads a reply such as "status=Ok&pollurl=...&hash=..." keeping the field order (needed for the hash). */
    public static Map<String, String> parse(String body) {
        Map<String, String> m = new LinkedHashMap<>();
        if (body == null) return m;
        for (String pair : body.trim().split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            m.putIfAbsent(k.toLowerCase(Locale.ROOT), v);
        }
        return m;
    }

    /** Paynow wants local mobile numbers: "+263 77 123 4567" -> "0771234567". Returns null if it is not a Zimbabwe mobile number. */
    public static String localMobile(String phone) {
        if (phone == null) return null;
        String d = phone.replaceAll("[^0-9]", "");
        if (d.startsWith("00263")) d = d.substring(5);
        else if (d.startsWith("263")) d = d.substring(3);
        if (!d.startsWith("0")) d = "0" + d;
        return d.matches("07\\d{8}") ? d : null;
    }

    public enum Outcome { PAID, FAILED, PENDING }

    /** Paynow status words: Paid / Awaiting Delivery / Delivered = money received; Cancelled / Failed / Disputed / Refunded = not. */
    public static Outcome outcome(String status) {
        String s = status == null ? "" : status.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "paid", "awaiting delivery", "delivered" -> Outcome.PAID;
            case "cancelled", "failed", "disputed", "refunded" -> Outcome.FAILED;
            default -> Outcome.PENDING; // created, sent, ...
        };
    }
}
