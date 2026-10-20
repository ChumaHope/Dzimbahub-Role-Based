package zw.ac.msu.dzimbahub.sms;

public interface SmsSender {
    void send(String toPhone, String message);
}
