package zw.ac.msu.dzimbahub.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class PaymentController {
    private final PaymentService payments;
    public PaymentController(PaymentService payments) { this.payments = payments; }

    public record PayRequest(@NotBlank String method, @Size(max = 20) String phone) {}

    /** Public: tells the website whether to show Paynow or the simulated payment button. */
    @GetMapping("/api/payments/config")
    public PaymentService.Config config() { return payments.config(); }

    /** Tenant: start paying for a booking. */
    @PostMapping("/api/tenant/bookings/{id}/paynow")
    public PaymentService.PaymentView start(Authentication a, @PathVariable Long id, @Valid @RequestBody PayRequest r) {
        return payments.start(a.getName(), id, r.method(), r.phone());
    }

    @GetMapping("/api/tenant/payments")
    public List<PaymentService.PaymentView> mine(Authentication a) { return payments.forTenant(a.getName()); }

    @PostMapping("/api/tenant/payments/{id}/check")
    public PaymentService.PaymentView check(Authentication a, @PathVariable Long id) { return payments.check(a.getName(), id); }

    /** Public: Paynow posts status updates here (form-encoded). The hash proves the message came from Paynow. */
    @PostMapping(value = "/api/payments/paynow/result", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> result(@RequestBody String body) {
        Map<String, String> fields = new LinkedHashMap<>(PaynowProtocol.parse(body));
        boolean ok = payments.handleResult(fields);
        return ok ? ResponseEntity.ok("OK") : ResponseEntity.badRequest().body("Rejected");
    }
}
