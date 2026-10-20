package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.PropertyRequest;
import zw.ac.msu.dzimbahub.model.ListingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.*;
import java.util.List;

@Service
public class ListingService {
    static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=900&q=80";

    private final PropertyRepository properties;
    private final ViewingRepository viewings;
    private final MessageRepository messages;
    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final CurrentUsers current;
    private final AuditService audit;

    public ListingService(PropertyRepository properties, ViewingRepository viewings, MessageRepository messages,
                          ReviewRepository reviews, BookingRepository bookings, CurrentUsers current, AuditService audit) {
        this.properties = properties; this.viewings = viewings; this.messages = messages;
        this.reviews = reviews; this.bookings = bookings; this.current = current; this.audit = audit;
    }

    static ResponseStatusException notFound() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Property not found."); }

    // ---- public
    public List<Property> publicList() { return properties.findAllByOrderByRatingDesc().stream().filter(Property::isActive).toList(); }
    public Property publicOne(Long id) {
        Property p = properties.findById(id).orElseThrow(ListingService::notFound);
        if (!p.isActive()) throw notFound();
        return p;
    }

    // ---- landlord
    public List<Property> mine(String email) { return properties.findByOwnerIdOrderByIdDesc(current.require(email).getId()); }

    @Transactional
    public Property create(String email, PropertyRequest r) {
        AppUser owner = current.require(email);
        Property p = new Property();
        apply(p, r);
        p.setOwnerId(owner.getId());
        p.setLandlordName(owner.getFullName());
        p = properties.save(p);
        audit.record(email, "LISTING_CREATED", "Property", p.getId(), p.getTitle());
        return p;
    }

    @Transactional
    public Property update(String email, Long id, PropertyRequest r) {
        Property p = ownedBy(email, id);
        apply(p, r);
        p = properties.save(p);
        audit.record(email, "LISTING_UPDATED", "Property", p.getId(), p.getTitle());
        return p;
    }

    @Transactional
    public void delete(String email, Long id) {
        Property p = ownedBy(email, id);
        remove(p, email);
    }

    private Property ownedBy(String email, Long id) {
        AppUser me = current.require(email);
        Property p = properties.findById(id).orElseThrow(ListingService::notFound);
        if (p.getOwnerId() == null || !p.getOwnerId().equals(me.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only change your own listings.");
        return p;
    }

    private void apply(Property p, PropertyRequest r) {
        p.setTitle(r.title().trim()); p.setArea(r.area().trim()); p.setCity(r.city().trim()); p.setAddress(r.address().trim());
        p.setDescription(r.description() == null ? "" : r.description().trim());
        p.setPrice(r.price()); p.setRoomType(r.roomType()); p.setBedrooms(r.bedrooms()); p.setBathrooms(r.bathrooms());
        p.setFurnished(r.furnished()); p.setLatitude(r.latitude()); p.setLongitude(r.longitude());
        p.setImageUrl(r.imageUrl() == null || r.imageUrl().isBlank() ? DEFAULT_IMAGE : r.imageUrl().trim());
        p.setAmenitiesCsv(r.amenities() == null ? "" : r.amenities().trim());
    }

    // ---- administrator
    public List<Property> all() { return properties.findAllByOrderByIdDesc(); }

    @Transactional
    public Property setStatus(String adminEmail, Long id, ListingStatus status) {
        Property p = properties.findById(id).orElseThrow(ListingService::notFound);
        p.setListingStatus(status);
        p = properties.save(p);
        audit.record(adminEmail, status == ListingStatus.SUSPENDED ? "LISTING_SUSPENDED" : "LISTING_RESTORED", "Property", id, p.getTitle());
        return p;
    }

    @Transactional
    public void adminDelete(String adminEmail, Long id) {
        remove(properties.findById(id).orElseThrow(ListingService::notFound), adminEmail);
    }

    private void remove(Property p, String actor) {
        Long id = p.getId();
        viewings.deleteByPropertyId(id); messages.deleteByPropertyId(id);
        reviews.deleteByPropertyId(id); bookings.deleteByPropertyId(id);
        properties.delete(p);
        audit.record(actor, "LISTING_DELETED", "Property", id, p.getTitle());
    }
}
