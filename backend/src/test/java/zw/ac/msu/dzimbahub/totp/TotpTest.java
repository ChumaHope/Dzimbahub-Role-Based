package zw.ac.msu.dzimbahub.totp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class TotpTest {
    @Test @DisplayName("TOTP-RFC codes match the RFC 6238 SHA-1 test values")
    void rfc6238Vectors() {
        String secret = Totp.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertThat(secret).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.codeAt(secret, Totp.stepAt(59L))).isEqualTo("287082");
        assertThat(Totp.codeAt(secret, Totp.stepAt(1111111109L))).isEqualTo("081804");
        assertThat(Totp.codeAt(secret, Totp.stepAt(1234567890L))).isEqualTo("005924");
        assertThat(Totp.codeAt(secret, Totp.stepAt(2000000000L))).isEqualTo("279037");
    }

    @Test @DisplayName("TOTP-WINDOW one step of clock difference is allowed, older or reused steps are not")
    void window() {
        String s = Totp.newSecret();
        long now = 1_000_000;
        assertThat(Totp.matchStep(s, Totp.codeAt(s, now - 1), now, -1)).isEqualTo(now - 1);
        assertThat(Totp.matchStep(s, Totp.codeAt(s, now + 1), now, -1)).isEqualTo(now + 1);
        assertThat(Totp.matchStep(s, Totp.codeAt(s, now), now, now)).isEqualTo(-1);
        assertThat(Totp.matchStep(s, "12345", now, -1)).isEqualTo(-1);
    }
}
