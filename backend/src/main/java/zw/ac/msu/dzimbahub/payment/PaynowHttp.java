package zw.ac.msu.dzimbahub.payment;

/** Sends a form to Paynow and returns the reply body. Replaced by a fake in tests. */
public interface PaynowHttp {
    String postForm(String url, String formBody) throws Exception;
}
