package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.BookingView;
import zw.ac.msu.dzimbahub.model.Booking;
import zw.ac.msu.dzimbahub.model.BookingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.model.RequestStatus;
import zw.ac.msu.dzimbahub.repository.BookingRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import zw.ac.msu.dzimbahub.repository.ViewingRepository;
import java.security.SecureRandom;
import java.util.List;

/** Booking confirmation through a SIMULATED payment: no real payment provider is contacted. */
@Service
public class BookingService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final BookingRepository bookings;
    private final PropertyRepository properties;
    private final ViewingRepository viewings;
    private final CurrentUsers current;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();

    public BookingService(BookingRepository bookings, PropertyRepository properties, ViewingRepository viewings,
                          CurrentUsers current, AuditService audit) {
        this.bookings = bookings; this.properties = properties; this.viewings = viewings; this.current = current; this.audit = audit;
    }

    BookingView view(Booking b) {
        String title = properties.findById(b.getPropertyId()).map(Property::getTitle).orElse("Removed listing");
        return new BookingView(b.getId(), b.getPropertyId(), title, b.getTenantId(), current.nameOf(b.getTenantId()),
                b.getAmount(), b.getStatus().name(), b.getPaymentRef(), b.getCreatedAt());
    }

    public List<BookingView> forTenant(String email) {
        return bookings.findByTenantIdOrderByCreatedAtDesc(current.require(email).getId()).stream().map(this::view).toList();
    }

    public List<BookingView> forLandlord(String email) {
        AppUser landlord = current.require(email);
        List<Long> ids = properties.findByOwnerIdOrderByIdDesc(landlord.getId()).stream().map(Property::getId).toList();
        if (ids.isEmpty()) return List.of();
        return bookings.findByPropertyIdInOrderByCreatedAtDesc(ids).stream().map(this::view).toList();
    }

    @Transactional
    public BookingView create(String tenantEmail, Long propertyId) {
        AppUser tenant = current.require(tenantEmail);
        Property p = properties.findById(propertyId).filter(Property::isActive).orElseThrow(ListingService::notFound);
        if (!viewings.existsByPropertyIdAndTenantIdAndStatus(p.getId(), tenant.getId(), RequestStatus.APPROVED))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can book a property after the landlord has approved your viewing request.");
        if (bookings.existsByPropertyIdAndTenantId(p.getId(), tenant.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a booking for this property.");
        Booking b = bookings.save(new Booking(p.getId(), tenant.getId(), p.getPrice()));
        audit.record(tenantEmail, "BOOKING_CREATED", "Booking", b.getId(), p.getTitle());
        return view(b);
    }

    @Transactional
    public BookingView pay(String tenantEmail, Long id) {
        AppUser tenant = current.require(tenantEmail);
        Booking b = bookings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found."));
        if (!b.getTenantId().equals(tenant.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only pay for your own bookings.");
        if (b.getStatus() != BookingStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This booking is already confirmed.");
        StringBuilder ref = new StringBuilder("SIM-");
        for (int i = 0; i < 8; i++) ref.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        b.confirm(ref.toString());
        bookings.save(b);
        audit.record(tenantEmail, "SIMULATED_PAYMENT", "Booking", id, ref.toString());
        return view(b);
    }
}
