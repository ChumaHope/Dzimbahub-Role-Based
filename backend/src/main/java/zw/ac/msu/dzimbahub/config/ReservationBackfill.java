package zw.ac.msu.dzimbahub.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.ac.msu.dzimbahub.model.BookingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.BookingRepository;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;

/**
 * One-off: homes that already had a paid (confirmed) booking before reservations existed are marked reserved.
 * Only rows whose "reserved" column is still empty are touched, so a home a landlord has released stays available.
 */
@Component
@Order(10)
public class ReservationBackfill implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(ReservationBackfill.class);
    private final PropertyRepository properties;
    private final BookingRepository bookings;

    public ReservationBackfill(PropertyRepository properties, BookingRepository bookings) { this.properties = properties; this.bookings = bookings; }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        int reserved = 0;
        for (Property p : properties.findAll()) {
            if (!p.reservationUnknown()) continue;
            var paid = bookings.findFirstByPropertyIdAndStatusOrderByCreatedAtDesc(p.getId(), BookingStatus.CONFIRMED);
            if (paid.isPresent()) { p.reserve(paid.get().getId()); reserved++; } else p.release();
            properties.save(p);
        }
        if (reserved > 0) log.info("Marked {} home(s) with a paid booking as reserved.", reserved);
    }
}
