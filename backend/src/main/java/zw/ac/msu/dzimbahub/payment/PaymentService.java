package zw.ac.msu.dzimbahub.payment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.model.Booking;
import zw.ac.msu.dzimbahub.model.BookingStatus;
import zw.ac.msu.dzimbahub.repository.BookingRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import zw.ac.msu.dzimbahub.service.AuditService;
import zw.ac.msu.dzimbahub.service.CurrentUsers;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Booking payments through Paynow (PAYMENT_PROVIDER=paynow).
 * 1. The tenant starts a payment: EcoCash / OneMoney (a PIN prompt on their phone) or Paynow's web page.
 * 2. Paynow tells us the result by posting to /api/payments/paynow/result, and the dashboard also asks ("polls").
 * 3. When Paynow says Paid, and the amount matches, the booking is confirmed with Paynow's reference.
 */
@Service
public class PaymentService {
    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    public static final List<String> METHODS = List.of("ECOCASH", "ONEMONEY", "WEB");

    public record PaymentView(Long id, Long bookingId, String method, String phone, BigDecimal amount, String reference,
                              String status, String providerStatus, String paynowReference, String message, String redirectUrl,
                              java.time.Instant createdAt) {}
    public record Config(String provider, List<String> methods, boolean testMode) {}

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final PaynowClient paynow;
    private final CurrentUsers current;
    private final AuditService audit;
    private final boolean enabled;
    private final String publicApiUrl, frontendUrl;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, PropertyRepository properties, PaynowClient paynow,
                          CurrentUsers current, AuditService audit,
                          @Value("${app.payments.provider:simulated}") String provider,
                          @Value("${app.public-api-url:http://localhost:8080}") String publicApiUrl,
                          @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.payments = payments; this.bookings = bookings; this.properties = properties; this.paynow = paynow;
        this.current = current; this.audit = audit;
        this.enabled = "paynow".equalsIgnoreCase(provider.trim());
        this.publicApiUrl = trimSlash(publicApiUrl); this.frontendUrl = trimSlash(frontendUrl);
        if (enabled && !paynow.configured())
            log.error("PAYMENT_PROVIDER=paynow but PAYNOW_INTEGRATION_ID / PAYNOW_INTEGRATION_KEY are missing. Payments will be refused.");
    }

    private static String trimSlash(String s) { s = s.trim(); return s.endsWith("/") ? s.substring(0, s.length() - 1) : s; }

    public boolean paynowEnabled() { return enabled; }
    public Config config() { return new Config(enabled ? "PAYNOW" : "SIMULATED", enabled ? METHODS : List.of(), enabled && paynow.hasAuthEmail()); }

    static PaymentView view(Payment p) {
        return new PaymentView(p.getId(), p.getBookingId(), p.getMethod(), p.getPhone(), p.getAmount(), p.getReference(),
                p.getStatus().name(), p.getProviderStatus(), p.getPaynowReference(), p.getMessage(),
                p.getStatus() == PaymentStatus.PENDING ? p.getRedirectUrl() : null, p.getCreatedAt());
    }

    @Transactional
    public PaymentView start(String tenantEmail, Long bookingId, String method, String phone) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.CONFLICT, "Online payments are not switched on.");
        AppUser tenant = current.require(tenantEmail);
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!b.getTenantId().equals(tenant.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay for your own bookings.");
        if (b.getStatus() != BookingStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already paid.");
        String m = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if (!METHODS.contains(m))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose EcoCash, OneMoney or Paynow (card and other options).");
        String local = null;
        if (!m.equals("WEB")) {
            local = PaynowProtocol.localMobile(phone);
            if (local == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your " + (m.equals("ECOCASH") ? "EcoCash" : "OneMoney") + " number, e.g. 0771234567.");
        }

        BigDecimal amount = BigDecimal.valueOf(b.getAmount());
        Payment p = payments.save(new Payment(b.getId(), tenant.getId(), m, local, amount));
        p.setReference("DZH-B" + b.getId() + "-P" + p.getId());
        String title = properties.findById(b.getPropertyId()).map(x -> x.getTitle()).orElse("property");
        String info = "DzimbaHub booking " + b.getId() + " - " + title;
        if (info.length() > 100) info = info.substring(0, 100);

        PaynowClient.Started s = paynow.start(p.getReference(), amount, info,
                frontendUrl + "/dashboard?payment=" + p.getId(), publicApiUrl + "/api/payments/paynow/result",
                local, local == null ? null : m.toLowerCase(Locale.ROOT));
        if (!s.ok()) {
            p.failed("Error", s.error());
            audit.record(tenantEmail, "PAYMENT_NOT_STARTED", "Payment", p.getId(), s.error());
        } else {
            p.sent(s.pollUrl(), s.redirectUrl(), s.instructions(), s.paynowReference());
            audit.record(tenantEmail, "PAYMENT_STARTED", "Payment", p.getId(), m + " " + p.getReference());
        }
        return view(payments.save(p));
    }

    public List<PaymentView> forTenant(String tenantEmail) {
        return payments.findByTenantIdOrderByCreatedAtDesc(current.require(tenantEmail).getId()).stream().map(PaymentService::view).toList();
    }

    /** The dashboard asks for the latest status; we ask Paynow if it is still pending. */
    @Transactional
    public PaymentView check(String tenantEmail, Long paymentId) {
        AppUser tenant = current.require(tenantEmail);
        Payment p = payments.findById(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found."));
        if (!p.getTenantId().equals(tenant.getId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This is not your payment.");
        if (p.getStatus() == PaymentStatus.PENDING && p.getPollUrl() != null) {
            PaynowClient.Status st = paynow.poll(p.getPollUrl());
            if (st.valid()) apply(p, st, tenantEmail);
        }
        return view(p);
    }

    /** Paynow's status update (result URL). Only a correctly signed message for one of our references is accepted. */
    @Transactional
    public boolean handleResult(Map<String, String> fields) {
        PaynowClient.Status st = paynow.read(fields);
        if (!st.valid()) { log.warn("Rejected a Paynow status update: {}", st.error()); return false; }
        Payment p = st.reference() == null ? null : payments.findByReference(st.reference()).orElse(null);
        if (p == null) { log.warn("Paynow status update for an unknown reference {}", st.reference()); return false; }
        apply(p, st, "paynow");
        return true;
    }

    private void apply(Payment p, PaynowClient.Status st, String actor) {
        if (p.getStatus() != PaymentStatus.PENDING) return; // already final; repeated messages change nothing
        switch (PaynowProtocol.outcome(st.status())) {
            case PAID -> {
                if (!amountMatches(p.getAmount(), st.amount())) {
                    p.failed(st.status(), "Paynow reported a different amount (" + st.amount() + "). Please contact the administrator.");
                    audit.record(actor, "PAYMENT_AMOUNT_MISMATCH", "Payment", p.getId(), p.getReference() + " " + st.amount());
                    break;
                }
                p.paid(st.status(), st.paynowReference());
                bookings.findById(p.getBookingId()).filter(b -> b.getStatus() == BookingStatus.PENDING).ifPresent(b -> {
                    b.confirm("PN-" + (p.getPaynowReference() == null ? p.getReference() : p.getPaynowReference()));
                    bookings.save(b);
                });
                audit.record(actor, "PAYMENT_RECEIVED", "Payment", p.getId(), p.getReference() + " " + p.getAmount());
            }
            case FAILED -> {
                p.failed(st.status(), "The payment was " + st.status().toLowerCase(Locale.ROOT) + ". You can try again.");
                audit.record(actor, "PAYMENT_FAILED", "Payment", p.getId(), p.getReference() + " " + st.status());
            }
            default -> p.progress(st.status(), st.paynowReference());
        }
        payments.save(p);
    }

    static boolean amountMatches(BigDecimal expected, String reported) {
        if (reported == null || reported.isBlank()) return true; // older replies may leave it out; the hash already protects the message
        try { return new BigDecimal(reported.trim()).compareTo(expected) == 0; } catch (NumberFormatException e) { return false; }
    }
}
