package zw.ac.msu.dzimbahub.controller;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import zw.ac.msu.dzimbahub.dto.Dtos.RecommendationRequest;
import zw.ac.msu.dzimbahub.dto.Dtos.ReviewView;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.service.CurrentUsers;
import zw.ac.msu.dzimbahub.service.ListingService;
import zw.ac.msu.dzimbahub.service.RecommendationService;
import zw.ac.msu.dzimbahub.service.ReviewService;
import java.util.List;

/** Public, read-only endpoints (no sign-in needed). Suspended listings are never returned. */
@RestController
@RequestMapping("/api")
public class PropertyController {
    private final ListingService listings;
    private final ReviewService reviews;
    private final RecommendationService recommender;
    private final CurrentUsers current;

    public PropertyController(ListingService listings, ReviewService reviews, RecommendationService recommender, CurrentUsers current) {
        this.listings = listings; this.reviews = reviews; this.recommender = recommender; this.current = current;
    }

    @GetMapping("/properties") public List<Property> all() { return listings.publicList(); }
    @GetMapping("/properties/{id}") public Property one(@PathVariable Long id) { return listings.publicOne(id); }
    @GetMapping("/properties/{id}/reviews") public List<ReviewView> reviews(@PathVariable Long id) { return reviews.forProperty(id); }

    /** Public. When a tenant is signed in, their own activity personalises the results. */
    @PostMapping("/recommendations")
    public RecommendationService.Result recommend(Authentication a, @Valid @RequestBody RecommendationRequest r) {
        boolean tenant = a != null && a.getAuthorities().stream().anyMatch(g -> "ROLE_TENANT".equals(g.getAuthority()));
        RecommendationService.Profile profile = tenant ? recommender.profileFor(current.require(a.getName()).getId())
                : RecommendationService.Profile.empty();
        List<String> amenities = r.amenities() == null ? List.of()
                : r.amenities().stream().filter(s -> s != null && !s.isBlank()).map(String::trim).distinct().toList();
        return recommender.recommend(listings.availableList(),
                new RecommendationService.Criteria(r.budget(), r.area(), r.roomType(), r.furnished(), amenities), profile);
    }
}
