package zw.ac.msu.dzimbahub.payment;

/** PENDING = sent to Paynow, waiting for the customer; PAID = money received; FAILED = cancelled, declined or could not start. */
public enum PaymentStatus { PENDING, PAID, FAILED }
