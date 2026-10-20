package zw.ac.msu.dzimbahub.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.msu.dzimbahub.dto.Dtos.*;
import zw.ac.msu.dzimbahub.service.*;
import java.util.List;

/** Everything under /api/tenant/** requires the TENANT role (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/tenant")
public class TenantController {
    private final ViewingService viewings;
    private final MessagingService messaging;
    private final ReviewService reviews;
    private final BookingService bookings;
    private final PreferenceService preferences;

    public TenantController(ViewingService viewings, MessagingService messaging, ReviewService reviews,
                            BookingService bookings, PreferenceService preferences) {
        this.viewings = viewings; this.messaging = messaging; this.reviews = reviews; this.bookings = bookings; this.preferences = preferences;
    }

    @PostMapping("/viewings") public ViewingView requestViewing(Authentication a, @Valid @RequestBody ViewingRequest r) { return viewings.request(a.getName(), r); }
    @GetMapping("/viewings") public List<ViewingView> myViewings(Authentication a) { return viewings.forTenant(a.getName()); }
    @PostMapping("/viewings/{id}/cancel") public ViewingView cancel(Authentication a, @PathVariable Long id) { return viewings.cancel(a.getName(), id); }

    @PostMapping("/enquiries") public MessageView enquire(Authentication a, @Valid @RequestBody EnquiryRequest r) { return messaging.enquire(a.getName(), r); }
    @GetMapping("/messages") public List<MessageView> messages(Authentication a) { return messaging.forUser(a.getName()); }
    @PostMapping("/messages/{id}/reply") public MessageView reply(Authentication a, @PathVariable Long id, @Valid @RequestBody ReplyRequest r) { return messaging.reply(a.getName(), id, r.message()); }

    @PostMapping("/reviews") public ReviewView review(Authentication a, @Valid @RequestBody ReviewRequest r) { return reviews.create(a.getName(), r); }

    @PostMapping("/bookings") public BookingView book(Authentication a, @Valid @RequestBody BookingRequest r) { return bookings.create(a.getName(), r.propertyId()); }
    @GetMapping("/bookings") public List<BookingView> myBookings(Authentication a) { return bookings.forTenant(a.getName()); }
    @PostMapping("/bookings/{id}/pay") public BookingView pay(Authentication a, @PathVariable Long id) { return bookings.pay(a.getName(), id); }

    @GetMapping("/preferences") public PreferenceDto prefs(Authentication a) { return preferences.get(a.getName()); }
    @PutMapping("/preferences") public PreferenceDto savePrefs(Authentication a, @Valid @RequestBody PreferenceDto d) { return preferences.save(a.getName(), d); }
}
