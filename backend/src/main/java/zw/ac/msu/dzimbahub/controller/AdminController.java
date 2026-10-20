package zw.ac.msu.dzimbahub.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.UserStatus;
import zw.ac.msu.dzimbahub.dto.Dtos.*;
import zw.ac.msu.dzimbahub.model.ListingStatus;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.service.AdminService;
import zw.ac.msu.dzimbahub.service.ListingService;
import java.util.List;
import java.util.Map;

/** Everything under /api/admin/** requires the ADMIN role. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final ListingService listings;

    public AdminController(AdminService admin, ListingService listings) { this.admin = admin; this.listings = listings; }

    @GetMapping("/stats") public Map<String, Long> stats() { return admin.stats(); }
    @GetMapping("/audit") public List<AuditView> audit() { return admin.audit(); }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam(required = false) String status) {
        UserStatus s = null;
        if (status != null && !status.isBlank()) {
            try { s = UserStatus.valueOf(status.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown status."); }
        }
        return admin.listUsers(s);
    }
    @PostMapping("/users/{id}/approve") public UserView approve(Authentication a, @PathVariable Long id) { return admin.approve(a.getName(), id); }
    @PostMapping("/users/{id}/reject") public UserView reject(Authentication a, @PathVariable Long id) { return admin.reject(a.getName(), id); }
    @PostMapping("/users/{id}/suspend") public UserView suspend(Authentication a, @PathVariable Long id) { return admin.suspend(a.getName(), id); }
    @PostMapping("/users/{id}/reactivate") public UserView reactivate(Authentication a, @PathVariable Long id) { return admin.reactivate(a.getName(), id); }

    @GetMapping("/properties") public List<Property> properties() { return listings.all(); }
    @PostMapping("/properties/{id}/status")
    public Property setStatus(Authentication a, @PathVariable Long id, @Valid @RequestBody ListingStatusRequest r) {
        ListingStatus s;
        try { s = ListingStatus.valueOf(r.status().trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status must be ACTIVE or SUSPENDED."); }
        return listings.setStatus(a.getName(), id, s);
    }
    @DeleteMapping("/properties/{id}") public Map<String, String> delete(Authentication a, @PathVariable Long id) { listings.adminDelete(a.getName(), id); return Map.of("message", "Listing removed."); }
}
