package com.example.demo.repository;

import com.example.demo.model.AuditLog;
import com.example.demo.model.AuditLog.AuditType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByTimestampDesc();

    List<AuditLog> findByTypeOrderByTimestampDesc(AuditType type);

    List<AuditLog> findByTimestampAfterOrderByTimestampDesc(LocalDateTime after);

    List<AuditLog> findByTypeAndTimestampAfterOrderByTimestampDesc(
            AuditType type, LocalDateTime after);
}
