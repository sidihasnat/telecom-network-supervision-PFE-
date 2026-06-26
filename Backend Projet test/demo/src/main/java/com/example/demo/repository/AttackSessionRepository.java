package com.example.demo.repository;

import com.example.demo.model.AttackSession;
import com.example.demo.model.AttackSession.SessionStatus;
import com.example.demo.model.AttackSession.SessionSource;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttackSessionRepository extends JpaRepository<AttackSession, Long> {


    List<AttackSession> findByDeviceNameAndStatus(String deviceName, AttackSession.SessionStatus status);

    // ── Live Tab: كل الـ sessions النشطة ───────────────────────
    List<AttackSession> findByStatus(SessionStatus status);

    // ── البحث عن session مفتوح لنفس device+interface+attackType ─
    // هذا الأهم: يُستخدم في savePrediction لتقرير إنشاء session جديد أو تحديث موجود
    Optional<AttackSession> findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
            String deviceName, String interfaceName, String attackType, SessionStatus status);

    // 🆕 For Trigger Rules: find active session for a specific rule on a device
    Optional<AttackSession> findByDeviceNameAndRuleIdAndStatus(
            String deviceName, Long ruleId, SessionStatus status);

    // ── History Tab: sessions منتهية مع فلترة ──────────────────
    List<AttackSession> findByStatusInOrderByStartedAtDesc(List<SessionStatus> statuses);

    List<AttackSession> findByDeviceNameAndStatusInOrderByStartedAtDesc(
            String deviceName, List<SessionStatus> statuses);

    // ── History مع فلتر زمني ───────────────────────────────────
    List<AttackSession> findByStatusInAndStartedAtAfterOrderByStartedAtDesc(
            List<SessionStatus> statuses, LocalDateTime after);

    List<AttackSession> findByDeviceNameAndStatusInAndStartedAtAfterOrderByStartedAtDesc(
            String deviceName, List<SessionStatus> statuses, LocalDateTime after);

    /**
     * Overlap-aware history query.
     *
     * Catches sessions that started BEFORE the window but extended INTO it.
     * Example: window = last 1 hour (19:00 → 20:00). A session that ran
     * 18:30 → 19:30 must be returned — its tail (19:00 → 19:30) is in-window.
     *
     * A session overlaps [since, now] if either:
     *   - it started after `since`, OR
     *   - it ended after `since` (including ongoing sessions where endedAt IS NULL)
     */
    @Query("SELECT s FROM AttackSession s " +
            "WHERE s.status IN :statuses " +
            "  AND (s.startedAt >= :since " +
            "       OR s.endedAt IS NULL " +
            "       OR s.endedAt >= :since) " +
            "ORDER BY s.startedAt DESC")
    List<AttackSession> findOverlappingByStatusIn(
            @Param("statuses") List<SessionStatus> statuses,
            @Param("since") LocalDateTime since);

    @Query("SELECT s FROM AttackSession s " +
            "WHERE s.deviceName = :deviceName " +
            "  AND s.status IN :statuses " +
            "  AND (s.startedAt >= :since " +
            "       OR s.endedAt IS NULL " +
            "       OR s.endedAt >= :since) " +
            "ORDER BY s.startedAt DESC")
    List<AttackSession> findOverlappingByDeviceAndStatusIn(
            @Param("deviceName") String deviceName,
            @Param("statuses") List<SessionStatus> statuses,
            @Param("since") LocalDateTime since);

    // ── Home Live Feed: آخر sessions (أي status) ───────────────
    List<AttackSession> findTop10ByOrderByStartedAtDesc();

    // ── إغلاق الـ sessions المنتهية (30s بدون prediction) ──────
    // sessions نشطة ولم تحدّث منذ أكثر من threshold
    @Query("SELECT s FROM AttackSession s WHERE s.status = 'ACTIVE' AND s.lastSeenAt < :threshold")
    List<AttackSession> findStaleSessions(@Param("threshold") LocalDateTime threshold);

    // ── SLA: downtime caused by actual AI-detected attacks only ──
    //
    // Excludes from SLA:
    //   - transit: router passing attack traffic but it's not down
    //   - trigger:*: operator-defined thresholds (server still works)
    //   - interface_down: one link down ≠ whole device down
    //   - device_offline: already counted by DeviceStatusService (duplicate otherwise)
    //
    // The AI-attack duration is considered "service-impacting" downtime.
    @Query("SELECT s.deviceName, SUM(TIMESTAMPDIFF(SECOND, s.startedAt, " +
            "CASE WHEN s.endedAt IS NOT NULL THEN s.endedAt ELSE CURRENT_TIMESTAMP END)) " +
            "FROM AttackSession s WHERE s.startedAt >= :since " +
            "AND s.attackType != 'transit' " +
            "AND s.attackType NOT LIKE 'trigger:%' " +
            "AND s.attackType != 'interface_down' " +
            "AND s.attackType != 'device_offline' " +
            "GROUP BY s.deviceName")
    List<Object[]> getDowntimeByDevice(@Param("since") LocalDateTime since);

    // ── Notifications: sessions غير معترف بها (بدون transit) ──
    @Query("SELECT s FROM AttackSession s WHERE s.acknowledged = false " +
            "AND s.status = :status AND s.attackType != 'transit' ORDER BY s.startedAt DESC")
    List<AttackSession> findUnacknowledgedNonTransit(@Param("status") SessionStatus status);

    // ── Stats ──────────────────────────────────────────────────
    long countByStatus(SessionStatus status);

    @Query("SELECT s.deviceName, COUNT(s) FROM AttackSession s GROUP BY s.deviceName ORDER BY COUNT(s) DESC")
    List<Object[]> countByDevice();

    @Query("SELECT s.attackType, COUNT(s) FROM AttackSession s GROUP BY s.attackType ORDER BY COUNT(s) DESC")
    List<Object[]> countByAttackType();

    // 🆕 Filter by source (AI / TRIGGER_RULE / INTERFACE_DOWN)
    List<AttackSession> findBySessionSourceAndStatusOrderByStartedAtDesc(
            SessionSource source, SessionStatus status);
}