package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.model.Booking;
import zw.ac.msu.dzimbahub.model.BookingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.BookingRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;

/**
 * When a booking is paid the home becomes RESERVED: it is no longer offered to other tenants
 * (no new viewings, bookings or payments, and it leaves the recommendations) until the landlord makes it available again.
 */
@Service
public class ReservationService {
    public static final String TAKEN = "This home has already been reserved by another tenant.";

    private final PropertyRepository properties;
    private final BookingRepository bookings;
    private final AuditService audit;

    public ReservationService(PropertyRepository properties, BookingRepository bookings, AuditService audit) {
        this.properties = properties; this.bookings = bookings; this.audit = audit;
    }

    /** True if the home is reserved for a different booking. */
    public boolean takenByOther(Long propertyId, Long bookingId) {
        return properties.findById(propertyId)
                .filter(p -> p.isReserved() && (bookingId == null || !bookingId.equals(p.getReservedBookingId()))).isPresent();
    }

    /** Throws 409 if someone else already reserved the home. */
    public void requireAvailable(Long propertyId, Long bookingId) {
        if (takenByOther(propertyId, bookingId)) throw new ResponseStatusException(HttpStatus.CONFLICT, TAKEN);
    }

    /**
     * Confirms a paid booking and reserves the home. Returns false (booking left PENDING) if another tenant's booking
     * was confirmed first; the payment is then kept on record for the administrator to refund.
     */
    @Transactional
    public boolean confirmAndReserve(Booking b, String paymentRef, String actor) {
        if (b.getStatus() != BookingStatus.PENDING) return true;
        Property p = properties.findById(b.getPropertyId()).orElse(null);
        if (p != null && p.isReserved() && !b.getId().equals(p.getReservedBookingId())) {
            audit.record(actor, "PAID_BUT_ALREADY_RESERVED", "Booking", b.getId(), paymentRef + " - refund needed");
            return false;
        }
        b.confirm(paymentRef);
        bookings.save(b);
        if (p != null) {
            p.reserve(b.getId());
            properties.save(p);
            audit.record(actor, "HOME_RESERVED", "Property", p.getId(), p.getTitle() + " by booking " + b.getId());
        }
        return true;
    }

    /** Landlord (or administrator) makes the home available again, e.g. when the tenant has moved out. */
    @Transactional
    public Property release(Property p, String actor) {
        if (!p.isReserved()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This home is not reserved.");
        p.release();
        audit.record(actor, "HOME_AVAILABLE_AGAIN", "Property", p.getId(), p.getTitle());
        return properties.save(p);
    }
}
