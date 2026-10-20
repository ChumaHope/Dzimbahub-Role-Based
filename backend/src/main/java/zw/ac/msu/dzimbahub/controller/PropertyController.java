package zw.ac.msu.dzimbahub.controller;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import zw.ac.msu.dzimbahub.dto.Dtos.RecommendationRequest;
import zw.ac.msu.dzimbahub.dto.Dtos.ReviewView;
import zw.ac.msu.dzimbahub.model.Property;
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

    public PropertyController(ListingService listings, ReviewService reviews, RecommendationService recommender) {
        this.listings = listings; this.reviews = reviews; this.recommender = recommender;
    }

    @GetMapping("/properties") public List<Property> all() { return listings.publicList(); }
    @GetMapping("/properties/{id}") public Property one(@PathVariable Long id) { return listings.publicOne(id); }
    @GetMapping("/properties/{id}/reviews") public List<ReviewView> reviews(@PathVariable Long id) { return reviews.forProperty(id); }

    @PostMapping("/recommendations")
    public List<Property> recommend(@Valid @RequestBody RecommendationRequest r) {
        return recommender.recommend(listings.publicList(), r.budget(), r.area(), r.roomType(), r.furnished());
    }
}
