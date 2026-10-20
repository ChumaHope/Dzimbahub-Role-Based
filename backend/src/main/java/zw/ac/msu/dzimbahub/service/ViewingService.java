package zw.ac.msu.dzimbahub.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.ac.msu.dzimbahub.auth.AppUser;
import zw.ac.msu.dzimbahub.dto.Dtos.ViewingRequest;
import zw.ac.msu.dzimbahub.dto.Dtos.ViewingView;
import zw.ac.msu.dzimbahub.model.Property;
import zw.ac.msu.dzimbahub.model.RequestStatus;
import zw.ac.msu.dzimbahub.model.Viewing;
import zw.ac.msu.dzimbahub.repository.PropertyRepository;
import zw.ac.msu.dzimbahub.repository.ViewingRepository;
import java.util.List;

@Service
public class ViewingService {
    private final ViewingRepository viewings;
    private final PropertyRepository properties;
    private final CurrentUsers current;
    private final AuditService audit;

    public ViewingService(ViewingRepository viewings, PropertyRepository properties, CurrentUsers current, AuditService audit) {
        this.viewings = viewings; this.properties = properties; this.current = current; this.audit = audit;
    }

    ViewingView view(Viewing v) {
        String title = properties.findById(v.getPropertyId()).map(Property::getTitle).orElse("Removed listing");
        return new ViewingView(v.getId(), v.getPropertyId(), title, v.getTenantId(), current.nameOf(v.getTenantId()),
                v.getViewDate(), v.getViewTime(), v.getNote(), v.getStatus().name(), v.getCreatedAt());
    }

    // ---- tenant
    @Transactional
    public ViewingView request(String tenantEmail, ViewingRequest r) {
        AppUser tenant = current.require(tenantEmail);
        Property p = properties.findById(r.propertyId()).filter(Property::isActive).orElseThrow(ListingService::notFound);
        if (viewings.existsByPropertyIdAndTenantIdAndStatus(p.getId(), tenant.getId(), RequestStatus.PENDING))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You already have a pending viewing request for this property.");
        Viewing v = viewings.save(new Viewing(p.getId(), tenant.getId(), r.date(), r.time(),
                r.note() == null ? null : r.note().trim()));
        audit.record(tenantEmail, "VIEWING_REQUESTED", "Viewing", v.getId(), p.getTitle());
        return view(v);
    }

    public List<ViewingView> forTenant(String tenantEmail) {
        return viewings.findByTenantIdOrderByCreatedAtDesc(current.require(tenantEmail).getId()).stream().map(this::view).toList();
    }

    @Transactional
    public ViewingView cancel(String tenantEmail, Long id) {
        AppUser tenant = current.require(tenantEmail);
        Viewing v = viewings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing request not found."));
        if (!v.getTenantId().equals(tenant.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only cancel your own viewing requests.");
        if (v.getStatus() != RequestStatus.PENDING && v.getStatus() != RequestStatus.APPROVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This viewing request can no longer be cancelled.");
        v.setStatus(RequestStatus.CANCELLED);
        viewings.save(v);
        audit.record(tenantEmail, "VIEWING_CANCELLED", "Viewing", id, null);
        return view(v);
    }

    // ---- landlord
    public List<ViewingView> forLandlord(String landlordEmail) {
        AppUser landlord = current.require(landlordEmail);
        List<Long> ids = properties.findByOwnerIdOrderByIdDesc(landlord.getId()).stream().map(Property::getId).toList();
        if (ids.isEmpty()) return List.of();
        return viewings.findByPropertyIdInOrderByCreatedAtDesc(ids).stream().map(this::view).toList();
    }

    @Transactional
    public ViewingView decide(String landlordEmail, Long id, boolean approve) {
        AppUser landlord = current.require(landlordEmail);
        Viewing v = viewings.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viewing request not found."));
        Property p = properties.findById(v.getPropertyId()).orElseThrow(ListingService::notFound);
        if (p.getOwnerId() == null || !p.getOwnerId().equals(landlord.getId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only respond to requests for your own listings.");
        if (v.getStatus() != RequestStatus.PENDING)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This request has already been handled.");
        v.setStatus(approve ? RequestStatus.APPROVED : RequestStatus.DECLINED);
        viewings.save(v);
        audit.record(landlordEmail, approve ? "VIEWING_APPROVED" : "VIEWING_DECLINED", "Viewing", id, p.getTitle());
        return view(v);
    }
}
