package zw.ac.msu.dzimbahub.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import zw.ac.msu.dzimbahub.dto.Dtos.*;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.service.*;
import java.util.List;
import java.util.Map;

/** Everything under /api/landlord/** requires the LANDLORD role and only touches the landlord's own data. */
@RestController
@RequestMapping("/api/landlord")
public class LandlordController {
    private final ListingService listings;
    private final ViewingService viewings;
    private final MessagingService messaging;
    private final BookingService bookings;
    private final ImageService images;

    public LandlordController(ListingService listings, ViewingService viewings, MessagingService messaging, BookingService bookings, ImageService images) {
        this.listings = listings; this.viewings = viewings; this.messaging = messaging; this.bookings = bookings; this.images = images;
    }

    /** Upload one photo from the landlord's device (form field "file"). Returns {id, url}. */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImageService.Uploaded uploadImage(Authentication a, @RequestParam("file") MultipartFile file) { return images.upload(a.getName(), file); }

    @GetMapping("/properties") public List<Property> mine(Authentication a) { return listings.mine(a.getName()); }
    @PostMapping("/properties") public Property create(Authentication a, @Valid @RequestBody PropertyRequest r) { return listings.create(a.getName(), r); }
    @PutMapping("/properties/{id}") public Property update(Authentication a, @PathVariable Long id, @Valid @RequestBody PropertyRequest r) { return listings.update(a.getName(), id, r); }
    @DeleteMapping("/properties/{id}") public Map<String, String> delete(Authentication a, @PathVariable Long id) { listings.delete(a.getName(), id); return Map.of("message", "Listing removed."); }

    @GetMapping("/viewings") public List<ViewingView> viewingRequests(Authentication a) { return viewings.forLandlord(a.getName()); }
    @PostMapping("/viewings/{id}/decision") public ViewingView decide(Authentication a, @PathVariable Long id, @RequestBody ViewingDecision d) { return viewings.decide(a.getName(), id, d.approve()); }

    @GetMapping("/messages") public List<MessageView> messages(Authentication a) { return messaging.forUser(a.getName()); }
    @PostMapping("/messages/{id}/reply") public MessageView reply(Authentication a, @PathVariable Long id, @Valid @RequestBody ReplyRequest r) { return messaging.reply(a.getName(), id, r.message()); }

    @GetMapping("/bookings") public List<BookingView> bookings(Authentication a) { return bookings.forLandlord(a.getName()); }
}
