package zw.ac.msu.dzimbahub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.auth.AppUserRepository;
import zw.ac.msu.dzimbahub.dto.Dtos.PropertyRequest;
import zw.ac.msu.dzimbahub.model.ListingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.*;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.util.ArrayList;
import java.util.List;

/**
 * Listings created or edited by a landlord are PENDING until an administrator approves them.
 * Only ACTIVE listings are shown to the public.
 */
@Service
public class ListingService {
    private static final Logger log = LoggerFactory.getLogger(ListingService.class);
    public static final int MAX_PHOTOS = 8;
    static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=900&q=80";

    private final PropertyRepository properties;
    private final ViewingRepository viewings;
    private final MessageRepository messages;
    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final CurrentUsers current;
    private final AuditService audit;
    private final ImageService images;
    private final AppUserRepository users;
    private final SmsSender sms;

    public ListingService(PropertyRepository properties, ViewingRepository viewings, MessageRepository messages,
                          ReviewRepository reviews, BookingRepository bookings, CurrentUsers current, AuditService audit,
                          ImageService images, AppUserRepository users, SmsSender sms) {
        this.properties = properties; this.viewings = viewings; this.messages = messages;
        this.reviews = reviews; this.bookings = bookings; this.current = current; this.audit = audit;
        this.images = images; this.users = users; this.sms = sms;
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
        apply(p, r, owner.getId());
        p.setOwnerId(owner.getId());
        p.setLandlordName(owner.getFullName());
        p.setListingStatus(ListingStatus.PENDING);
        p = properties.save(p);
        audit.record(email, "LISTING_SUBMITTED", "Property", p.getId(), p.getTitle());
        return p;
    }

    /** Editing sends the listing back for approval, unless an administrator has suspended it. */
    @Transactional
    public Property update(String email, Long id, PropertyRequest r) {
        Property p = ownedBy(email, id);
        List<String> photosBefore = new ArrayList<>(p.getPhotos());
        apply(p, r, p.getOwnerId());
        if (p.getListingStatus() != ListingStatus.SUSPENDED) {
            p.setListingStatus(ListingStatus.PENDING);
            p.setReviewNote(null);
        }
        p = properties.save(p);
        images.deleteUnused(photosBefore, p.getPhotos());
        audit.record(email, "LISTING_RESUBMITTED", "Property", p.getId(), p.getTitle());
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

    private void apply(Property p, PropertyRequest r, Long ownerId) {
        p.setTitle(r.title().trim()); p.setArea(r.area().trim()); p.setCity(r.city().trim()); p.setAddress(r.address().trim());
        p.setDescription(r.description() == null ? "" : r.description().trim());
        p.setPrice(r.price()); p.setRoomType(r.roomType()); p.setBedrooms(r.bedrooms()); p.setBathrooms(r.bathrooms());
        p.setFurnished(r.furnished()); p.setLatitude(r.latitude()); p.setLongitude(r.longitude());
        List<String> photos = r.photos() == null ? List.of()
                : r.photos().stream().filter(u -> u != null && !u.isBlank()).map(String::trim).distinct().toList();
        if (photos.size() > MAX_PHOTOS)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A listing can have at most " + MAX_PHOTOS + " photos.");
        images.checkOwnership(ownerId, photos);
        String link = r.imageUrl() == null ? "" : r.imageUrl().trim();
        if (!photos.isEmpty()) {
            p.setPhotos(photos);
            p.setImageUrl(photos.get(0));
        } else {
            p.setPhotos(null);
            p.setImageUrl(link.isBlank() ? DEFAULT_IMAGE : link);
        }
        p.setAmenitiesCsv(r.amenities() == null ? "" : r.amenities().trim());
    }

    // ---- administrator
    public List<Property> all() { return properties.findAllByOrderByIdDesc(); }

    public List<Property> pending() {
        return properties.findAllByOrderByIdDesc().stream().filter(p -> p.getListingStatus() == ListingStatus.PENDING).toList();
    }

    @Transactional
    public Property setStatus(String adminEmail, Long id, ListingStatus status, String note) {
        Property p = properties.findById(id).orElseThrow(ListingService::notFound);
        String reason = note == null ? "" : note.trim();
        if (status == ListingStatus.REJECTED && reason.isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please give the landlord a reason for the rejection.");
        ListingStatus before = p.getListingStatus();
        p.setListingStatus(status);
        p.setReviewNote(reason.isEmpty() ? null : reason);
        p = properties.save(p);
        String action = switch (status) {
            case ACTIVE -> before == ListingStatus.SUSPENDED ? "LISTING_RESTORED" : "LISTING_APPROVED";
            case REJECTED -> "LISTING_REJECTED";
            case SUSPENDED -> "LISTING_SUSPENDED";
            case PENDING -> "LISTING_SENT_FOR_REVIEW";
        };
        audit.record(adminEmail, action, "Property", id, p.getTitle());
        if (before == ListingStatus.PENDING && status == ListingStatus.ACTIVE)
            notifyOwner(p, "Good news! Your DzimbaHub listing \"" + p.getTitle() + "\" has been approved and is now visible to tenants.");
        else if (status == ListingStatus.REJECTED)
            notifyOwner(p, "Your DzimbaHub listing \"" + p.getTitle() + "\" was not approved: " + reason + ". Please edit it and save to resubmit.");
        return p;
    }

    private void notifyOwner(Property p, String text) {
        if (p.getOwnerId() == null) return;
        users.findById(p.getOwnerId()).filter(u -> u.getPhone() != null && !u.getPhone().isBlank()).ifPresent(u -> {
            try { sms.send(u.getPhone(), text); }
            catch (Exception e) { log.warn("Could not notify landlord {} about listing {}: {}", u.getEmail(), p.getId(), e.getMessage()); }
        });
    }

    @Transactional
    public void adminDelete(String adminEmail, Long id) {
        remove(properties.findById(id).orElseThrow(ListingService::notFound), adminEmail);
    }

    private void remove(Property p, String actor) {
        Long id = p.getId();
        List<String> photos = new ArrayList<>(p.getPhotos());
        viewings.deleteByPropertyId(id); messages.deleteByPropertyId(id);
        reviews.deleteByPropertyId(id); bookings.deleteByPropertyId(id);
        properties.delete(p);
        images.deleteUnused(photos, List.of());
        audit.record(actor, "LISTING_DELETED", "Property", id, p.getTitle());
    }
}
