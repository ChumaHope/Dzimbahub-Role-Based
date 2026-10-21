package zw.ac.msu.dzimbahub.totp;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;

/**
 * Time-based one-time passwords (RFC 6238), the method used by Google Authenticator,
 * Microsoft Authenticator and Authy: HMAC-SHA1, 30-second steps, 6 digits.
 * Plain Java with no Spring, so it can be tested on its own.
 */
public final class Totp {
    public static final int PERIOD_SECONDS = 30;
    public static final int DIGITS = 6;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    /** A new random 160-bit secret, Base32 encoded (32 characters), as authenticator apps expect. */
    public static String newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    public static long stepAt(long epochSeconds) { return Math.floorDiv(epochSeconds, PERIOD_SECONDS); }
    public static long currentStep() { return stepAt(System.currentTimeMillis() / 1000); }

    /** The 6-digit code for a given 30-second step. */
    public static String codeAt(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(unbase32(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0F;
            int bin = ((h[o] & 0x7F) << 24) | ((h[o + 1] & 0xFF) << 16) | ((h[o + 2] & 0xFF) << 8) | (h[o + 3] & 0xFF);
            return String.format("%0" + DIGITS + "d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    /**
     * Returns the step the code belongs to, allowing one step either side for small clock differences,
     * or -1 if it does not match. Steps at or before {@code lastUsedStep} are refused so a code cannot be reused.
     */
    public static long matchStep(String base32Secret, String code, long nowStep, long lastUsedStep) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) return -1;
        for (long s = nowStep - 1; s <= nowStep + 1; s++) {
            if (s <= lastUsedStep) continue;
            if (constantTimeEquals(codeAt(base32Secret, s), code)) return s;
        }
        return -1;
    }

    /** The link encoded in the QR code. Apps show it as "issuer (account)". */
    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = enc(issuer) + ":" + enc(account);
        return "otpauth://totp/" + label + "?secret=" + base32Secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + PERIOD_SECONDS;
    }

    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8).replace("+", "%20"); }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    public static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF); bits += 8;
            while (bits >= 5) { sb.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31)); bits -= 5; }
        }
        if (bits > 0) sb.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    public static byte[] unbase32(String s) {
        String clean = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(clean.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char c : clean.toCharArray()) {
            int v = ALPHABET.indexOf(c);
            if (v < 0) throw new IllegalArgumentException("Not a Base32 secret");
            buffer = (buffer << 5) | v; bits += 5;
            if (bits >= 8) { out.put((byte) ((buffer >> (bits - 8)) & 0xFF)); bits -= 8; }
        }
        return out.array();
    }
}
