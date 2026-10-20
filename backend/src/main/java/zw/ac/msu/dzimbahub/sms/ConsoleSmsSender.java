package zw.ac.msu.dzimbahub.sms;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Development sender: writes the text message to the server log instead of sending it. */
@Component
@ConditionalOnProperty(name = "app.sms.provider", havingValue = "console", matchIfMissing = true)
public class ConsoleSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(ConsoleSmsSender.class);
    @Override public void send(String toPhone, String message) {
        log.warn("[DEV SMS] to {}: {}", toPhone, message);
    }
}
