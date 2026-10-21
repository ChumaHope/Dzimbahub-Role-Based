package zw.ac.msu.dzimbahub.otp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import zw.ac.msu.dzimbahub.sms.SmsSender;

/** Default: sends the code as a normal text message through the configured SmsSender. */
@Component
@ConditionalOnProperty(name = "app.otp.channel", havingValue = "sms", matchIfMissing = true)
public class SmsOtpSender implements OtpSender {
    private final SmsSender sms;
    public SmsOtpSender(SmsSender sms) { this.sms = sms; }

    @Override public Channel sendCode(String phone, String code, long ttlMinutes) {
        sms.send(phone, OtpSender.smsText(code, ttlMinutes));
        return Channel.SMS;
    }
}
