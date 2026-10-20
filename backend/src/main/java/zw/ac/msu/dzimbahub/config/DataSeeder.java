package zw.ac.msu.dzimbahub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import zw.ac.msu.dzimbahub.auth.*;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;

/** Creates the first administrator and (optionally) demo accounts and listings on start-up. */
@Configuration
public class DataSeeder {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    CommandLineRunner seed(AppUserRepository users, PropertyRepository properties, PasswordEncoder encoder,
                           @Value("${app.admin.email}") String adminEmail, @Value("${app.admin.password}") String adminPassword,
                           @Value("${app.admin.full-name}") String adminName, @Value("${app.admin.phone}") String adminPhone,
                           @Value("${app.seed.demo-data:true}") boolean demo, @Value("${app.seed.demo-password:Demo#12345}") String demoPassword) {
        return args -> {
            if (!users.existsByRole(UserRole.ADMIN)) {
                users.save(new AppUser(adminName, adminEmail.trim().toLowerCase(), encoder.encode(adminPassword), adminPhone, UserRole.ADMIN, UserStatus.APPROVED));
                log.warn("Created the first administrator account: {}", adminEmail);
                if ("Admin#12345".equals(adminPassword)) log.warn("The default administrator password is in use. Set ADMIN_PASSWORD before deploying.");
            }
            if (!demo || properties.count() > 0) return;
            AppUser tapi = demoUser(users, encoder, "Tapi Moyo", "landlord1@dzimbahub.local", "+263771000001", UserRole.LANDLORD, demoPassword);
            AppUser rudo = demoUser(users, encoder, "Rudo Chirwa", "landlord2@dzimbahub.local", "+263771000002", UserRole.LANDLORD, demoPassword);
            demoUser(users, encoder, "Demo Tenant", "tenant@dzimbahub.local", "+263771000003", UserRole.TENANT, demoPassword);
            properties.save(p(tapi, "Sunrise Student Lodge", "Belvedere", "12 College Road", 180, "Single", 1, 1, true, -17.7935, 31.0230, "photo-1522708323590-d24dbb6b0267", 4.7, 23, "WiFi,Security,Kitchen"));
            properties.save(p(rudo, "Avondale Garden Rooms", "Avondale", "8 Fife Avenue", 230, "Self-contained", 1, 1, true, -17.7850, 31.0530, "photo-1505693416388-ac5ce068fe85", 4.9, 31, "WiFi,Parking,Furnished"));
            properties.save(p(tapi, "Mount Pleasant Study House", "Mount Pleasant", "24 Alpes Road", 150, "Shared", 2, 1, false, -17.7640, 31.0500, "photo-1560185008-b033106af5c3", 4.4, 17, "Study Area,Security,Garden"));
            properties.save(p(rudo, "Milton Park Executive Rooms", "Milton Park", "3 Cork Road", 260, "Self-contained", 1, 1, true, -17.8120, 31.0260, "photo-1564013799919-ab600027ffc6", 4.8, 19, "WiFi,Parking,Backup Power"));
            properties.save(p(tapi, "Eastlea Budget Rooms", "Eastlea", "17 Ridgeway", 130, "Single", 1, 1, false, -17.8110, 31.0660, "photo-1493666438817-866a91353ca9", 4.2, 11, "Security,Kitchen,Water"));
            properties.save(p(rudo, "Greendale Shared Residence", "Greendale", "5 Alpes Road", 175, "Shared", 2, 1, true, -17.8000, 31.0850, "photo-1600607687920-4e2a09cf159d", 4.5, 14, "WiFi,Furnished,Garden"));
            log.warn("Demo accounts created: landlord1@dzimbahub.local, landlord2@dzimbahub.local, tenant@dzimbahub.local. Set SEED_DEMO_DATA=false in production.");
        };
    }

    private AppUser demoUser(AppUserRepository users, PasswordEncoder encoder, String name, String email, String phone, UserRole role, String password) {
        return users.findByEmailIgnoreCase(email).orElseGet(() ->
                users.save(new AppUser(name, email, encoder.encode(password), phone, role, UserStatus.APPROVED)));
    }

    private Property p(AppUser owner, String title, String area, String address, double price, String roomType, int beds, int baths,
                       boolean furnished, double lat, double lng, String photo, double rating, int reviews, String amenities) {
        Property p = new Property();
        p.setTitle(title); p.setArea(area); p.setCity("Harare"); p.setAddress(address);
        p.setDescription("Comfortable boarding accommodation suitable for students and young professionals. Demo listing for the DzimbaHub system.");
        p.setPrice(price); p.setRoomType(roomType); p.setBedrooms(beds); p.setBathrooms(baths); p.setFurnished(furnished);
        p.setLatitude(lat); p.setLongitude(lng);
        p.setImageUrl("https://images.unsplash.com/" + photo + "?auto=format&fit=crop&w=900&q=80");
        p.setLandlordName(owner.getFullName()); p.setOwnerId(owner.getId());
        p.setRating(rating); p.setReviewCount(reviews); p.setAmenitiesCsv(amenities);
        return p;
    }
}
