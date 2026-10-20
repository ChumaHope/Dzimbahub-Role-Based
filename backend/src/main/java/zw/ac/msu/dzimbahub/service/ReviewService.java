package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.ReviewRequest;
import zw.ac.msu.dzimbahub.dto.Dtos.ReviewView;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.model.RequestStatus;
import zw.ac.msu.dzimbahub.model.Review;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import zw.ac.msu.dzimbahub.repository.ReviewRepository;
import zw.ac.msu.dzimbahub.repository.ViewingRepository;
import java.util.List;

/**
 * Rules: only tenants may review; a tenant may review a property once; and only after the landlord
 * has approved a viewing request for that property.
 */
@Service
public class ReviewService {
    private final ReviewRepository reviews;
    private final PropertyRepository properties;
    private final ViewingRepository viewings;
    private final CurrentUsers current;
    private final AuditService audit;

    public ReviewService(ReviewRepository reviews, PropertyRepository properties, ViewingRepository viewings,
                         CurrentUsers current, AuditService audit) {
        this.reviews = reviews; this.properties = properties; this.viewings = viewings; this.current = current; this.audit = audit;
    }

    ReviewView view(Review r) {
        return new ReviewView(r.getId(), r.getPropertyId(), current.nameOf(r.getTenantId()), r.getRating(), r.getComment(), r.getCreatedAt());
    }

    public List<ReviewView> forProperty(Long propertyId) {
        Property p = properties.findById(propertyId).filter(Property::isActive).orElseThrow(ListingService::notFound);
        return reviews.findByPropertyIdOrderByCreatedAtDesc(p.getId()).stream().map(this::view).toList();
    }

    @Transactional
    public ReviewView create(String tenantEmail, ReviewRequest r) {
        AppUser tenant = current.require(tenantEmail);
        Property p = properties.findById(r.propertyId()).filter(Property::isActive).orElseThrow(ListingService::notFound);
        if (!viewings.existsByPropertyIdAndTenantIdAndStatus(p.getId(), tenant.getId(), RequestStatus.APPROVED))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can rate a property after the landlord has approved your viewing request.");
        if (reviews.existsByPropertyIdAndTenantId(p.getId(), tenant.getId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this property.");
        Review saved = reviews.save(new Review(p.getId(), tenant.getId(), r.rating(), r.comment() == null ? null : r.comment().trim()));
        List<Review> all = reviews.findByPropertyIdOrderByCreatedAtDesc(p.getId());
        double avg = all.stream().mapToInt(Review::getRating).average().orElse(0);
        p.setRating(Math.round(avg * 10.0) / 10.0);
        p.setReviewCount(all.size());
        properties.save(p);
        audit.record(tenantEmail, "REVIEW_SUBMITTED", "Review", saved.getId(), p.getTitle() + " (" + r.rating() + "/5)");
        return view(saved);
    }
}
