package zw.ac.msu.dzimbahub.service;

import org.springframework.stereotype.Service;
import zw.ac.msu.dzimbahub.model.AuditLog;
import zw.ac.msu.dzimbahub.repository.AuditLogRepository;

/** Records who did what, for the administrator's activity log. */
@Service
public class AuditService {
    private final AuditLogRepository repo;
    public AuditService(AuditLogRepository repo) { this.repo = repo; }

    public void record(String actor, String action, String targetType, Object targetId, String detail) {
        String d = detail == null ? null : (detail.length() > 480 ? detail.substring(0, 480) : detail);
        repo.save(new AuditLog(actor, action, targetType, targetId == null ? null : String.valueOf(targetId), d));
    }
}
