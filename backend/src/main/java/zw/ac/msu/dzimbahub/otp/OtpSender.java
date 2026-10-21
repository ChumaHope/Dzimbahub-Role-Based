package zw.ac.msu.dzimbahub.otp;

/**
 * Delivers a sign-in code to the user's phone.
 * Choose the channel with OTP_CHANNEL: sms (default) or whatsapp.
 */
public interface OtpSender {
    enum Channel { SMS, WHATSAPP }

    /**
     * Sends the code and returns the channel that actually delivered it
     * (WhatsApp may fall back to SMS). Throws {@link OtpDeliveryException} if nothing could be sent.
     */
    Channel sendCode(String phone, String code, long ttlMinutes);

    static String smsText(String code, long ttlMinutes) {
        return "Your DzimbaHub verification code is " + code
                + ". It expires in " + ttlMinutes + " minutes. Do not share it.";
    }
}
