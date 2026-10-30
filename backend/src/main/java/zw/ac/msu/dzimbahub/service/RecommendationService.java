package zw.ac.msu.dzimbahub.service;

import org.springframework.stereotype.Service;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.BookingRepository;
import zw.ac.msu.dzimbahub.repository.MessageRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import zw.ac.msu.dzimbahub.repository.ViewingRepository;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Smart recommendations: a content-based recommender that explains itself.
 *
 *  1. Hard filters: only approved, not-reserved homes; nothing more than 25% over the budget.
 *  2. Each criterion the tenant gave earns points: budget 35, location 30 (exact area, a near-miss spelling,
 *     or distance from the area on the map), room type 20, furnishing 10, wanted amenities 15.
 *     Quality always counts (10): a Bayesian average rating, so one 5-star review does not beat many 4-star reviews.
 *  3. Signed-in tenants: a profile is learned from the homes they asked to view, enquired about or booked
 *     (areas, room types, typical rent, amenities) and similar homes get up to 15 extra points.
 *  4. Match % = points earned / points possible. Results under 40% are dropped; if nothing is left the closest homes
 *     are shown with a note, so the tenant never gets an empty page while homes exist.
 */
@Service
public class RecommendationService {
    public static final int LIMIT = 6;
    static final double OVER_BUDGET_LIMIT = 0.25;
    static final int MIN_MATCH = 40;

    public record Criteria(double budget, String area, String roomType, boolean furnished, List<String> amenities) {
        boolean hasBudget() { return budget > 0; }
        boolean hasArea() { return area != null && !area.isBlank(); }
        boolean hasRoom() { return roomType != null && !roomType.isBlank() && !roomType.equalsIgnoreCase("any"); }
        boolean hasAmenities() { return amenities != null && !amenities.isEmpty(); }
    }
    /** What a tenant's own activity says they like. */
    public record Profile(Set<String> areas, Set<String> roomTypes, double typicalRent, Set<String> amenities, Set<Long> seen, Set<Long> booked) {
        public static Profile empty() { return new Profile(Set.of(), Set.of(), 0, Set.of(), Set.of(), Set.of()); }
        public boolean isEmpty() { return seen.isEmpty(); }
    }
    public record Recommendation(Property property, int match, List<String> reasons) {}
    public record Result(List<Recommendation> results, boolean personalised, String note) {}

    private final ViewingRepository viewings;
    private final BookingRepository bookings;
    private final MessageRepository messages;
    private final PropertyRepository properties;

    public RecommendationService(ViewingRepository viewings, BookingRepository bookings, MessageRepository messages, PropertyRepository properties) {
        this.viewings = viewings; this.bookings = bookings; this.messages = messages; this.properties = properties;
    }

    /** Learns a profile from the homes this tenant viewed, enquired about or booked. */
    public Profile profileFor(Long tenantId) {
        if (tenantId == null) return Profile.empty();
        Set<Long> seen = new LinkedHashSet<>();
        viewings.findByTenantIdOrderByCreatedAtDesc(tenantId).forEach(v -> seen.add(v.getPropertyId()));
        messages.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(tenantId, tenantId).forEach(m -> seen.add(m.getPropertyId()));
        Set<Long> booked = bookings.findByTenantIdOrderByCreatedAtDesc(tenantId).stream().map(b -> b.getPropertyId()).collect(Collectors.toSet());
        seen.addAll(booked);
        if (seen.isEmpty()) return Profile.empty();
        List<Property> liked = properties.findAllById(seen);
        Set<String> areas = liked.stream().map(Property::getArea).filter(Objects::nonNull).map(RecommendationService::norm).collect(Collectors.toSet());
        Set<String> rooms = liked.stream().map(Property::getRoomType).filter(Objects::nonNull).map(RecommendationService::norm).collect(Collectors.toSet());
        double rent = liked.stream().mapToDouble(Property::getPrice).average().orElse(0);
        Map<String, Long> counts = liked.stream().flatMap(p -> p.getAmenities().stream()).map(RecommendationService::norm)
                .collect(Collectors.groupingBy(a -> a, Collectors.counting()));
        Set<String> common = counts.entrySet().stream().filter(e -> e.getValue() * 2 >= liked.size()).map(Map.Entry::getKey).collect(Collectors.toSet());
        return new Profile(areas, rooms, rent, common, seen, booked);
    }

    public Result recommend(List<Property> available, Criteria c, Profile profile) {
        List<Property> pool = available.stream().filter(p -> !profile.booked().contains(p.getId())).toList();
        if (pool.isEmpty()) return new Result(List.of(), !profile.isEmpty(), "There are no available homes right now. Please check again soon.");

        double[] centre = areaCentre(pool, c);
        List<Recommendation> scored = pool.stream()
                .filter(p -> !c.hasBudget() || p.getPrice() <= c.budget() * (1 + OVER_BUDGET_LIMIT))
                .map(p -> score(p, c, profile, centre))
                .sorted(Comparator.comparingInt(Recommendation::match).reversed()
                        .thenComparing(r -> -r.property().getRating()))
                .toList();
        List<Recommendation> good = scored.stream().filter(r -> r.match() >= MIN_MATCH).limit(LIMIT).toList();
        if (!good.isEmpty()) return new Result(good, !profile.isEmpty(), null);

        if (!scored.isEmpty())
            return new Result(scored.stream().limit(3).toList(), !profile.isEmpty(),
                    "No home matches everything you asked for yet. These are the closest options.");
        // everything is over budget: show the most affordable homes instead of nothing
        List<Recommendation> cheapest = pool.stream().sorted(Comparator.comparingDouble(Property::getPrice)).limit(3)
                .map(p -> score(p, c, profile, centre)).toList();
        return new Result(cheapest, !profile.isEmpty(),
                "Nothing is available within your budget. These are the most affordable homes right now.");
    }

    Recommendation score(Property p, Criteria c, Profile profile, double[] centre) {
        double got = 0, max = 0;
        List<String> why = new ArrayList<>();

        if (c.hasBudget()) {
            max += 35;
            if (p.getPrice() <= c.budget()) { got += 35; why.add("Within your US$" + money(c.budget()) + " budget"); }
            else {
                double over = (p.getPrice() - c.budget()) / c.budget();
                got += 35 * Math.max(0, 1 - over / OVER_BUDGET_LIMIT);
                why.add("US$" + money(p.getPrice() - c.budget()) + " over your budget");
            }
        }
        if (c.hasArea()) {
            max += 30;
            String want = norm(c.area()), have = norm(p.getArea());
            if (!have.isEmpty() && (have.contains(want) || want.contains(have))) { got += 30; why.add("In " + p.getArea()); }
            else if (!have.isEmpty() && similar(want, have)) { got += 26; why.add("In " + p.getArea() + " (close to \"" + c.area().trim() + "\")"); }
            else if (centre != null) {
                double km = km(centre[0], centre[1], p.getLatitude(), p.getLongitude());
                if (km <= 2) { got += 22; why.add(oneDecimal(km) + " km from " + c.area().trim()); }
                else if (km <= 5) { got += 14; why.add(oneDecimal(km) + " km from " + c.area().trim()); }
                else if (km <= 10) got += 6;
            } else if (norm(p.getCity()).equals(want)) { got += 15; why.add("In " + p.getCity()); }
        }
        if (c.hasRoom()) {
            max += 20;
            if (c.roomType().equalsIgnoreCase(p.getRoomType())) { got += 20; why.add(p.getRoomType() + " room, as you wanted"); }
        }
        if (c.furnished()) {
            max += 10;
            if (p.isFurnished()) { got += 10; why.add("Furnished"); }
        }
        if (c.hasAmenities()) {
            max += 15;
            Set<String> have = p.getAmenities().stream().map(RecommendationService::norm).collect(Collectors.toSet());
            List<String> hit = c.amenities().stream().filter(a -> have.contains(norm(a))).toList();
            got += 15.0 * hit.size() / c.amenities().size();
            if (!hit.isEmpty()) why.add("Has " + String.join(", ", hit));
        }

        max += 10;
        double quality = bayesianRating(p.getRating(), p.getReviewCount());
        got += quality / 5 * 10;
        if (p.getReviewCount() > 0 && p.getRating() >= 4)
            why.add("Rated " + oneDecimal(p.getRating()) + "★ by " + p.getReviewCount() + (p.getReviewCount() == 1 ? " tenant" : " tenants"));

        if (!profile.isEmpty() && !profile.seen().contains(p.getId())) {
            max += 15;
            double bonus = 0;
            if (profile.areas().contains(norm(p.getArea()))) bonus += 6;
            if (profile.roomTypes().contains(norm(p.getRoomType()))) bonus += 4;
            if (profile.typicalRent() > 0 && Math.abs(p.getPrice() - profile.typicalRent()) <= profile.typicalRent() * 0.2) bonus += 3;
            Set<String> have = p.getAmenities().stream().map(RecommendationService::norm).collect(Collectors.toSet());
            if (!profile.amenities().isEmpty())
                bonus += 2.0 * profile.amenities().stream().filter(have::contains).count() / profile.amenities().size();
            got += bonus;
            if (bonus >= 6) why.add("Similar to homes you have looked at");
        }

        int match = (int) Math.round(Math.min(100, 100 * got / max));
        return new Recommendation(p, match, why);
    }

    /** Pulls a rating towards 3.5 when there are few reviews (3 "virtual" reviews). */
    static double bayesianRating(double rating, int count) {
        double prior = 3.5, weight = 3;
        return (prior * weight + rating * count) / (weight + count);
    }

    /** Average map position of the homes in the wanted area, used to score homes nearby. */
    static double[] areaCentre(List<Property> pool, Criteria c) {
        if (!c.hasArea()) return null;
        String want = norm(c.area());
        List<Property> inArea = pool.stream().filter(p -> {
            String have = norm(p.getArea());
            return !have.isEmpty() && (have.contains(want) || want.contains(have) || similar(want, have));
        }).filter(p -> p.getLatitude() != 0 || p.getLongitude() != 0).toList();
        if (inArea.isEmpty()) return null;
        return new double[]{inArea.stream().mapToDouble(Property::getLatitude).average().orElse(0),
                inArea.stream().mapToDouble(Property::getLongitude).average().orElse(0)};
    }

    /** Typo-tolerant comparison: "Avondle" ~ "Avondale". */
    static boolean similar(String a, String b) {
        if (a.length() < 4 || b.length() < 4) return false;
        int allowed = Math.max(a.length(), b.length()) <= 6 ? 1 : 2;
        return levenshtein(a, b) <= allowed;
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    static double km(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371, dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(h));
    }

    static String norm(String s) { return s == null ? "" : s.trim().toLowerCase(Locale.ROOT); }
    private static String money(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.2f", v); }
    private static String oneDecimal(double v) { return String.format(Locale.ROOT, "%.1f", v); }
}
