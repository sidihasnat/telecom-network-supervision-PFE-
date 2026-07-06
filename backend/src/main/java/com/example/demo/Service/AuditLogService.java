package com.example.demo.Service;

import com.example.demo.model.AuditLog;
import com.example.demo.model.AuditLog.AuditType;
import com.example.demo.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository repo;

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()
                    && !"anonymousUser".equals(auth.getPrincipal())) {
                return auth.getName();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public AuditLog log(AuditType type, String description, String deviceName) {
        AuditLog entry = new AuditLog();
        entry.setType(type);
        entry.setDescription(description);
        entry.setDeviceName(deviceName);
        entry.setUsername(getCurrentUsername());
        return repo.save(entry);
    }

    public AuditLog log(AuditType type, String description) {
        return log(type, description, null);
    }

    public List<AuditLog> getRecent() {
        return repo.findTop100ByOrderByTimestampDesc();
    }

    public List<AuditLog> getByType(AuditType type) {
        return repo.findByTypeOrderByTimestampDesc(type);
    }

    public List<AuditLog> getByPeriod(String period) {
        LocalDateTime after = parsePeriod(period);
        if (after == null) return repo.findTop100ByOrderByTimestampDesc();
        return repo.findByTimestampAfterOrderByTimestampDesc(after);
    }

    public List<AuditLog> getFiltered(String type, String period) {
        AuditType auditType = (type != null && !type.isBlank()) ? AuditType.valueOf(type) : null;
        LocalDateTime after = parsePeriod(period);

        if (auditType != null && after != null) {
            return repo.findByTypeAndTimestampAfterOrderByTimestampDesc(auditType, after);
        } else if (auditType != null) {
            return repo.findByTypeOrderByTimestampDesc(auditType);
        } else if (after != null) {
            return repo.findByTimestampAfterOrderByTimestampDesc(after);
        } else {
            return repo.findTop100ByOrderByTimestampDesc();
        }
    }

    private LocalDateTime parsePeriod(String period) {
        if (period == null) return null;
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            default -> null;
        };
    }
}
