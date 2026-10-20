package zw.ac.msu.dzimbahub;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Replaces the real SMS sender in tests and remembers every message that "was sent". */
@TestConfiguration
public class TestSmsConfig {
    @Bean @Primary
    public CapturingSmsSender capturingSmsSender() { return new CapturingSmsSender(); }

    public static class CapturingSmsSender implements SmsSender {
        public record Sent(String to, String text) {}
        private final List<Sent> sent = new CopyOnWriteArrayList<>();

        @Override public void send(String toPhone, String message) { sent.add(new Sent(toPhone, message)); }
        public void clear() { sent.clear(); }
        public List<Sent> all() { return sent; }
        public Sent last() { return sent.isEmpty() ? null : sent.get(sent.size() - 1); }

        /** The six-digit code contained in the most recent message. */
        public String lastCode() {
            Matcher m = Pattern.compile("(\\d{6})").matcher(last().text());
            if (!m.find()) throw new IllegalStateException("No six-digit code in: " + last().text());
            return m.group(1);
        }
    }
}
