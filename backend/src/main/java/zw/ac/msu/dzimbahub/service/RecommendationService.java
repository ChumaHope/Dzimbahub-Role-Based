package zw.ac.msu.dzimbahub.service;

import org.springframework.stereotype.Service;
import zw.ac.msu.dzimbahub.model.Property;
import java.util.List;
import java.util.Map;

/** Deterministic weighted scoring: budget up to 35, area 30, room type 20, furnishing 10, plus the stored rating. */
@Service
public class RecommendationService {
    public List<Property> recommend(List<Property> properties, double budget, String area, String roomType, boolean furnished) {
        return properties.stream()
                .map(p -> Map.entry(p, score(p, budget, area, roomType, furnished)))
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(6).map(Map.Entry::getKey).toList();
    }

    double score(Property p, double budget, String area, String room, boolean furnished) {
        double s = 0;
        if (budget > 0) {
            double diff = Math.abs(p.getPrice() - budget) / budget;
            s += Math.max(0, 35 - diff * 35);
        }
        if (area != null && !area.isBlank() && p.getArea() != null && p.getArea().toLowerCase().contains(area.trim().toLowerCase())) s += 30;
        if (room != null && room.equalsIgnoreCase(p.getRoomType())) s += 20;
        if (furnished == p.isFurnished()) s += 10;
        s += p.getRating();
        return s;
    }
}
