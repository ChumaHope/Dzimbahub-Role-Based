package zw.ac.msu.dzimbahub.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "properties")
public class Property {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String title;
    private String area;
    private String city;
    private String address;
    @Column(length = 1500) private String description;
    private double price;
    private String roomType;
    private int bedrooms;
    private int bathrooms;
    private boolean furnished;
    private double latitude;
    private double longitude;
    @Column(length = 500) private String imageUrl;
    private String landlordName;
    private double rating;
    private int reviewCount;
    @Column(length = 1000) private String amenitiesCsv;
    /** Id of the landlord account that owns this listing (null for listings created before ownership existed). */
    private Long ownerId;
    @Enumerated(EnumType.STRING) @Column(length = 20) private ListingStatus listingStatus = ListingStatus.ACTIVE;
    private Instant createdAt = Instant.now();

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getArea() { return area; }
    public String getCity() { return city; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public double getPrice() { return price; }
    public String getRoomType() { return roomType; }
    public int getBedrooms() { return bedrooms; }
    public int getBathrooms() { return bathrooms; }
    public boolean isFurnished() { return furnished; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getImageUrl() { return imageUrl; }
    public String getLandlordName() { return landlordName; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public Long getOwnerId() { return ownerId; }
    public Instant getCreatedAt() { return createdAt; }
    public ListingStatus getListingStatus() { return listingStatus == null ? ListingStatus.ACTIVE : listingStatus; }
    @JsonProperty("amenitiesCsv") public String getAmenitiesCsv() { return amenitiesCsv; }
    public List<String> getAmenities() {
        return amenitiesCsv == null ? List.of()
                : Arrays.stream(amenitiesCsv.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    @com.fasterxml.jackson.annotation.JsonIgnore public boolean isActive() { return getListingStatus() == ListingStatus.ACTIVE; }

    public void setTitle(String v) { title = v; }
    public void setArea(String v) { area = v; }
    public void setCity(String v) { city = v; }
    public void setAddress(String v) { address = v; }
    public void setDescription(String v) { description = v; }
    public void setPrice(double v) { price = v; }
    public void setRoomType(String v) { roomType = v; }
    public void setBedrooms(int v) { bedrooms = v; }
    public void setBathrooms(int v) { bathrooms = v; }
    public void setFurnished(boolean v) { furnished = v; }
    public void setLatitude(double v) { latitude = v; }
    public void setLongitude(double v) { longitude = v; }
    public void setImageUrl(String v) { imageUrl = v; }
    public void setLandlordName(String v) { landlordName = v; }
    public void setRating(double v) { rating = v; }
    public void setReviewCount(int v) { reviewCount = v; }
    public void setAmenitiesCsv(String v) { amenitiesCsv = v; }
    public void setOwnerId(Long v) { ownerId = v; }
    public void setListingStatus(ListingStatus v) { listingStatus = v; }
}
