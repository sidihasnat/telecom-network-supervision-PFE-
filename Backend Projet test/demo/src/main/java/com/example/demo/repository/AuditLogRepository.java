package com.example.demo.repository;

import com.example.demo.model.AuditLog;
import com.example.demo.model.AuditLog.AuditType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // ── Activity Log: آخر الأحداث ──────────────────────────────
    List<AuditLog> findTop100ByOrderByTimestampDesc();

    // ── فلتر حسب النوع ────────────────────────────────────────
    List<AuditLog> findByTypeOrderByTimestampDesc(AuditType type);

    // ── فلتر حسب الفترة ───────────────────────────────────────
    List<AuditLog> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);

    // ── فلتر مجمع: نوع + فترة ─────────────────────────────────
    List<AuditLog> findByTypeAndTimestampAfterOrderByTimestampDesc(
            AuditType type, LocalDateTime after);
}
