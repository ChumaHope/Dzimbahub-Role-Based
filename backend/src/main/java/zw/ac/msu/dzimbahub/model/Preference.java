package zw.ac.msu.dzimbahub.model;

import jakarta.persistence.*;

/** A tenant's saved recommendation preferences (one row per tenant). */
@Entity
@Table(name = "preferences", uniqueConstraints = @UniqueConstraint(name = "uk_preference_user", columnNames = "userId"))
public class Preference {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    private double budget;
    private String area;
    private String roomType;
    private boolean furnished;

    protected Preference() {}
    public Preference(Long userId) { this.userId = userId; }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public double getBudget() { return budget; }
    public String getArea() { return area; }
    public String getRoomType() { return roomType; }
    public boolean isFurnished() { return furnished; }
    public void set(double budget, String area, String roomType, boolean furnished) {
        this.budget = budget; this.area = area; this.roomType = roomType; this.furnished = furnished;
    }
}
