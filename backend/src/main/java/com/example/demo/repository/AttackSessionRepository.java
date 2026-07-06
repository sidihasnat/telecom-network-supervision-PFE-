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

    List<AttackSession> findByStatus(SessionStatus status);

    Optional<AttackSession> findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
            String deviceName, String interfaceName, String attackType, SessionStatus status);

    Optional<AttackSession> findByDeviceNameAndRuleIdAndStatus(
            String deviceName, Long ruleId, SessionStatus status);

    List<AttackSession> findByStatusInOrderByStartedAtDesc(List<SessionStatus> statuses);

    List<AttackSession> findByDeviceNameAndStatusInOrderByStartedAtDesc(
            String deviceName, List<SessionStatus> statuses);

    List<AttackSession> findByStatusInAndStartedAtAfterOrderByStartedAtDesc(
            List<SessionStatus> statuses, LocalDateTime after);

    List<AttackSession> findByDeviceNameAndStatusInAndStartedAtAfterOrderByStartedAtDesc(
            String deviceName, List<SessionStatus> statuses, LocalDateTime after);


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

    List<AttackSession> findTop10ByOrderByStartedAtDesc();


    @Query("SELECT s FROM AttackSession s WHERE s.status = 'ACTIVE' AND s.lastSeenAt < :threshold")
    List<AttackSession> findStaleSessions(@Param("threshold") LocalDateTime threshold);

    @Query("SELECT s.deviceName, SUM(TIMESTAMPDIFF(SECOND, s.startedAt, " +
            "CASE WHEN s.endedAt IS NOT NULL THEN s.endedAt ELSE CURRENT_TIMESTAMP END)) " +
            "FROM AttackSession s WHERE s.startedAt >= :since " +
            "AND s.attackType != 'transit' " +
            "AND s.attackType NOT LIKE 'trigger:%' " +
            "AND s.attackType != 'interface_down' " +
            "AND s.attackType != 'device_offline' " +
            "GROUP BY s.deviceName")
    List<Object[]> getDowntimeByDevice(@Param("since") LocalDateTime since);

    @Query("SELECT s FROM AttackSession s WHERE s.acknowledged = false " +
            "AND s.status = :status AND s.attackType != 'transit' ORDER BY s.startedAt DESC")
    List<AttackSession> findUnacknowledgedNonTransit(@Param("status") SessionStatus status);

    long countByStatus(SessionStatus status);

    @Query("SELECT s.deviceName, COUNT(s) FROM AttackSession s GROUP BY s.deviceName ORDER BY COUNT(s) DESC")
    List<Object[]> countByDevice();

    @Query("SELECT s.attackType, COUNT(s) FROM AttackSession s GROUP BY s.attackType ORDER BY COUNT(s) DESC")
    List<Object[]> countByAttackType();

    List<AttackSession> findBySessionSourceAndStatusOrderByStartedAtDesc(
            SessionSource source, SessionStatus status);
}