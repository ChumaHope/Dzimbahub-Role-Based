package zw.ac.msu.dzimbahub.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.*;
import zw.ac.msu.dzimbahub.dto.Dtos.AuditView;
import zw.ac.msu.dzimbahub.dto.Dtos.UserView;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.repository.*;
import zw.ac.msu.dzimbahub.sms.SmsSender;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Account approval and platform oversight for administrators. */
@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final AppUserRepository users;
    private final PropertyRepository properties;
    private final ViewingRepository viewings;
    private final MessageRepository messages;
    private final ReviewRepository reviews;
    private final BookingRepository bookings;
    private final AuditLogRepository auditLogs;
    private final AuditService audit;
    private final SmsSender sms;

    public AdminService(AppUserRepository users, PropertyRepository properties, ViewingRepository viewings,
                        MessageRepository messages, ReviewRepository reviews, BookingRepository bookings,
                        AuditLogRepository auditLogs, AuditService audit, SmsSender sms) {
        this.users = users; this.properties = properties; this.viewings = viewings; this.messages = messages;
        this.reviews = reviews; this.bookings = bookings; this.auditLogs = auditLogs; this.audit = audit; this.sms = sms;
    }

    static UserView view(AppUser u) {
        return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole().name(), u.getStatus().name(), u.getCreatedAt());
    }

    public List<UserView> listUsers(UserStatus status) {
        List<AppUser> list = status == null ? users.findAllByOrderByCreatedAtDesc() : users.findByStatusOrderByCreatedAtAsc(status);
        return list.stream().map(AdminService::view).toList();
    }

    @Transactional
    public UserView approve(String adminEmail, Long id) {
        AppUser u = target(adminEmail, id);
        if (u.getStatus() != UserStatus.PENDING && u.getStatus() != UserStatus.REJECTED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or rejected requests can be approved.");
        return change(adminEmail, u, UserStatus.APPROVED, "USER_APPROVED",
                "Your DzimbaHub account has been approved. You can now sign in with your email, password and the text-message code.");
    }

    @Transactional
    public UserView reject(String adminEmail, Long id) {
        AppUser u = target(adminEmail, id);
        if (u.getStatus() != UserStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending requests can be rejected.");
        return change(adminEmail, u, UserStatus.REJECTED, "USER_REJECTED",
                "Your DzimbaHub signup request was not approved. Please contact the administrator for details.");
    }

    @Transactional
    public UserView suspend(String adminEmail, Long id) {
        AppUser u = target(adminEmail, id);
        if (u.getStatus() != UserStatus.APPROVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only approved accounts can be suspended.");
        return change(adminEmail, u, UserStatus.SUSPENDED, "USER_SUSPENDED", null);
    }

    @Transactional
    public UserView reactivate(String adminEmail, Long id) {
        AppUser u = target(adminEmail, id);
        if (u.getStatus() != UserStatus.SUSPENDED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only suspended accounts can be reactivated.");
        return change(adminEmail, u, UserStatus.APPROVED, "USER_REACTIVATED", null);
    }

    /** Administrators cannot change their own account or any other administrator account. */
    private AppUser target(String adminEmail, Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        if (u.getEmail().equalsIgnoreCase(adminEmail) || u.getRole() == UserRole.ADMIN)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be changed here.");
        return u;
    }

    private UserView change(String adminEmail, AppUser u, UserStatus status, String action, String smsText) {
        u.changeStatus(status, adminEmail);
        users.save(u);
        audit.record(adminEmail, action, "User", u.getId(), u.getEmail());
        if (smsText != null && u.getPhone() != null) {
            try { sms.send(u.getPhone(), smsText); } catch (Exception e) { log.warn("Could not notify {} by SMS: {}", u.getEmail(), e.getMessage()); }
        }
        return view(u);
    }

    public Map<String, Long> stats() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("pendingApprovals", users.countByStatus(UserStatus.PENDING));
        m.put("tenants", users.countByRole(UserRole.TENANT));
        m.put("landlords", users.countByRole(UserRole.LANDLORD));
        m.put("properties", properties.count());
        m.put("activeProperties", properties.findAllByOrderByIdDesc().stream().filter(Property::isActive).count());
        m.put("pendingListings", properties.findAllByOrderByIdDesc().stream()
                .filter(p -> p.getListingStatus() == zw.ac.msu.dzimbahub.model.ListingStatus.PENDING).count());
        m.put("viewings", viewings.count());
        m.put("messages", messages.count());
        m.put("reviews", reviews.count());
        m.put("bookings", bookings.count());
        return m;
    }

    public List<AuditView> audit() {
        return auditLogs.findTop100ByOrderByCreatedAtDesc().stream()
                .map(a -> new AuditView(a.getId(), a.getActor(), a.getAction(), a.getTargetType(), a.getTargetId(), a.getDetail(), a.getCreatedAt())).toList();
    }
}
