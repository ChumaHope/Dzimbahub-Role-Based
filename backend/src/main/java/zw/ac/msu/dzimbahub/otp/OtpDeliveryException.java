package zw.ac.msu.dzimbahub.otp;

/** Raised when a sign-in code could not be delivered on any channel. */
public class OtpDeliveryException extends RuntimeException {
    public OtpDeliveryException(String message) { super(message); }
}
