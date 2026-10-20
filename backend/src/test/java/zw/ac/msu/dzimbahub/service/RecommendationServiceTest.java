package zw.ac.msu.dzimbahub.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.ac.msu.dzimbahub.model.Property;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RecommendationServiceTest {
    private final RecommendationService service = new RecommendationService();

    private Property property(String title, String area, double price, String room, boolean furnished, double rating) {
        Property p = new Property();
        p.setTitle(title); p.setArea(area); p.setPrice(price); p.setRoomType(room); p.setFurnished(furnished); p.setRating(rating);
        return p;
    }

    @Test @DisplayName("REC-02 a perfect match scores 35 + 30 + 20 + 10 + rating")
    void perfectMatchScore() {
        Property p = property("Perfect", "Belvedere", 180, "Single", true, 4.5);
        assertThat(service.score(p, 180, "Belvedere", "Single", true)).isEqualTo(99.5);
    }

    @Test @DisplayName("REC-03 results are ranked by score and limited to six")
    void rankedAndLimited() {
        Property best = property("Best", "Avondale", 200, "Single", true, 3);
        Property worse = property("Worse", "Eastlea", 400, "Shared", false, 5);
        List<Property> ranked = service.recommend(List.of(worse, best), 200, "Avondale", "Single", true);
        assertThat(ranked).extracting(Property::getTitle).containsExactly("Best", "Worse");

        List<Property> many = java.util.stream.IntStream.range(0, 10).mapToObj(i -> property("P" + i, "A", 100, "Single", true, 1)).toList();
        assertThat(service.recommend(many, 100, "", "", true)).hasSize(6);
    }

    @Test @DisplayName("REC-04 a listing without an area does not break scoring")
    void nullArea() {
        Property p = property("No area", null, 100, "Single", true, 1);
        assertThat(service.score(p, 100, "Avondale", "Single", true)).isEqualTo(35 + 20 + 10 + 1);
    }
}
