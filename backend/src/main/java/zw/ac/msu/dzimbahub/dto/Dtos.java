package zw.ac.msu.dzimbahub.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/** Request and response shapes used by the REST API. */
public final class Dtos {
    private Dtos() {}

    // ---------- requests
    public record PropertyRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(max = 100) String area,
            @NotBlank @Size(max = 100) String city,
            @NotBlank @Size(max = 200) String address,
            @Size(max = 1500) String description,
            @Positive double price,
            @NotBlank @Pattern(regexp = "Single|Shared|Self-contained", message = "must be Single, Shared or Self-contained") String roomType,
            @Min(0) @Max(20) int bedrooms,
            @Min(0) @Max(20) int bathrooms,
            boolean furnished,
            @DecimalMin("-90") @DecimalMax("90") double latitude,
            @DecimalMin("-180") @DecimalMax("180") double longitude,
            @Size(max = 500) @Pattern(regexp = "^$|^https?://.+", message = "must start with http:// or https://") String imageUrl,
            @Size(max = 1000) String amenities) {}

    public record ViewingRequest(@NotNull Long propertyId, @NotNull @FutureOrPresent LocalDate date,
                                 @NotNull LocalTime time, @Size(max = 500) String note) {}
    public record ViewingDecision(boolean approve) {}
    public record EnquiryRequest(@NotNull Long propertyId, @NotBlank @Size(max = 1000) String message) {}
    public record ReplyRequest(@NotBlank @Size(max = 1000) String message) {}
    public record ReviewRequest(@NotNull Long propertyId, @Min(1) @Max(5) int rating, @Size(max = 1000) String comment) {}
    public record BookingRequest(@NotNull Long propertyId) {}
    public record PreferenceDto(@PositiveOrZero double budget, @Size(max = 100) String area,
                                @Size(max = 30) String roomType, boolean furnished) {}
    public record RecommendationRequest(@PositiveOrZero double budget, String area, String roomType, boolean furnished) {}
    public record ListingStatusRequest(@NotNull String status) {}

    // ---------- responses
    public record ViewingView(Long id, Long propertyId, String propertyTitle, Long tenantId, String tenantName,
                              LocalDate date, LocalTime time, String note, String status, Instant createdAt) {}
    public record MessageView(Long id, Long propertyId, String propertyTitle, Long senderId, String senderName,
                              Long recipientId, String recipientName, String body, Instant createdAt, boolean mine) {}
    public record ReviewView(Long id, Long propertyId, String tenantName, int rating, String comment, Instant createdAt) {}
    public record BookingView(Long id, Long propertyId, String propertyTitle, Long tenantId, String tenantName,
                              double amount, String status, String paymentRef, Instant createdAt) {}
    public record UserView(Long id, String fullName, String email, String phone, String role, String status, Instant createdAt) {}
    public record AuditView(Long id, String actor, String action, String targetType, String targetId, String detail, Instant createdAt) {}
}
