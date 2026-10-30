package zw.ac.msu.dzimbahub.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.service.RecommendationService.Criteria;
import zw.ac.msu.dzimbahub.service.RecommendationService.Profile;
import zw.ac.msu.dzimbahub.service.RecommendationService.Recommendation;
import zw.ac.msu.dzimbahub.service.RecommendationService.Result;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {
    private final RecommendationService service = new RecommendationService(null, null, null, null);
    private long nextId = 1;

    private Property property(String title, String area, double price, String room, boolean furnished, double rating, int reviews,
                              double lat, double lon, String amenities) {
        Property p = new Property();
        p.setTitle(title); p.setArea(area); p.setCity("Harare"); p.setPrice(price); p.setRoomType(room); p.setFurnished(furnished);
        p.setRating(rating); p.setReviewCount(reviews); p.setLatitude(lat); p.setLongitude(lon); p.setAmenitiesCsv(amenities);
        try { Field f = Property.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, nextId++); } catch (Exception e) { throw new IllegalStateException(e); }
        return p;
    }
    private Property simple(String title, String area, double price, String room) {
        return property(title, area, price, room, true, 4, 3, -17.80, 31.04, "WiFi");
    }
    private Criteria want(double budget, String area, String room) { return new Criteria(budget, area, room, true, List.of()); }

    @Test @DisplayName("REC-02 a perfect match scores close to 100% and explains why")
    void perfectMatch() {
        Property p = property("Perfect", "Belvedere", 180, "Single", true, 5, 10, -17.8, 31.0, "WiFi,Security");
        Recommendation r = service.score(p, new Criteria(180, "Belvedere", "Single", true, List.of("WiFi")), Profile.empty(), null);
        assertThat(r.match()).isGreaterThanOrEqualTo(95);
        assertThat(r.reasons()).contains("Within your US$180 budget", "In Belvedere", "Single room, as you wanted", "Furnished", "Has WiFi");
    }

    @Test @DisplayName("REC-03 results are ranked by match, limited to six, and homes far over budget are left out")
    void rankedLimitedAndBudgetFiltered() {
        Property best = simple("Best", "Avondale", 200, "Single");
        Property worse = property("Worse", "Eastlea", 240, "Shared", true, 4, 3, -17.83, 31.10, "WiFi"); // ~7 km away
        Property tooDear = simple("Too dear", "Avondale", 400, "Single");
        Result r = service.recommend(List.of(worse, tooDear, best), want(200, "Avondale", "Single"), Profile.empty());
        assertThat(r.results()).extracting(x -> x.property().getTitle()).containsExactly("Best");

        List<Property> many = IntStream.range(0, 10).mapToObj(i -> simple("P" + i, "A", 100, "Single")).toList();
        assertThat(service.recommend(many, want(100, "", "Single"), Profile.empty()).results()).hasSize(6);
    }

    @Test @DisplayName("REC-04 typos in the area still match, and nearby homes score by distance")
    void areaTyposAndDistance() {
        Property inArea = property("In", "Avondale", 200, "Single", true, 4, 3, -17.800, 31.040, "");
        Property near = property("Near", "Milton Park", 200, "Single", true, 4, 3, -17.810, 31.035, "");
        Property far = property("Far", "Chitungwiza", 200, "Single", true, 4, 3, -18.010, 31.070, "");
        Result r = service.recommend(List.of(far, near, inArea), want(200, "Avondle", "Single"), Profile.empty());
        assertThat(r.results().get(0).property().getTitle()).isEqualTo("In");
        assertThat(r.results().get(1).property().getTitle()).isEqualTo("Near");
        assertThat(r.results().get(1).reasons().toString()).contains("km from Avondle");
    }

    @Test @DisplayName("REC-05 when nothing fits, the closest or cheapest homes are shown with a note instead of nothing")
    void fallbacks() {
        Property a = simple("A", "Eastlea", 500, "Shared");
        Property b = simple("B", "Eastlea", 450, "Shared");
        Result r = service.recommend(List.of(a, b), want(100, "Avondale", "Single"), Profile.empty());
        assertThat(r.results()).extracting(x -> x.property().getTitle()).containsExactly("B", "A");
        assertThat(r.note()).contains("most affordable");
        assertThat(service.recommend(List.of(), want(100, "", ""), Profile.empty()).note()).contains("no available homes");
    }

    @Test @DisplayName("REC-06 a tenant's own activity lifts similar homes and hides homes they already booked")
    void personalisation() {
        Property booked = simple("Booked", "Avondale", 200, "Single");
        Property similar = simple("Similar", "Avondale", 210, "Single");
        Property other = simple("Other", "Eastlea", 210, "Shared");
        Profile profile = new Profile(Set.of("avondale"), Set.of("single"), 200, Set.of("wifi"),
                Set.of(booked.getId()), Set.of(booked.getId()));
        Result r = service.recommend(List.of(other, booked, similar), new Criteria(250, "", "", false, List.of()), profile);
        assertThat(r.personalised()).isTrue();
        assertThat(r.results()).extracting(x -> x.property().getTitle()).doesNotContain("Booked").first().isEqualTo("Similar");
        assertThat(r.results().get(0).reasons()).contains("Similar to homes you have looked at");
    }

    @Test @DisplayName("REC-07 one 5-star review does not outrank many 4-star reviews")
    void bayesianQuality() {
        assertThat(RecommendationService.bayesianRating(5, 1)).isLessThan(RecommendationService.bayesianRating(4.5, 20));
        assertThat(RecommendationService.levenshtein("avondle", "avondale")).isEqualTo(1);
    }
}
