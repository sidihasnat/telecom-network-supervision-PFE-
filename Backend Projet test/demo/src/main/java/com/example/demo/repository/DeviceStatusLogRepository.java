package com.example.demo.repository;

import com.example.demo.model.DeviceStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusLogRepository extends JpaRepository<DeviceStatusLog, Long> {

    // Find current open record for a device (endedAt IS NULL)
    Optional<DeviceStatusLog> findByDeviceNameAndEndedAtIsNull(String deviceName);

    // (Legacy) kept for compat — strict "started-after" query
    List<DeviceStatusLog> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime after);

    // (Legacy) kept for compat
    List<DeviceStatusLog> findByDeviceNameAndStartedAtAfterOrderByStartedAtAsc(
            String deviceName, LocalDateTime after);

    /**
     * Overlap-aware timeline query.
     *
     * Returns all logs that OVERLAP the window [since, now]. This catches three
     * cases that the old "startedAt > since" query missed:
     *   1. A log that started BEFORE `since` but is still open (endedAt IS NULL)
     *      — it's currently ongoing inside the window.
     *   2. A log that started BEFORE `since` and ended AFTER `since` — its tail
     *      is inside the window.
     *   3. The normal case: started after `since`.
     *
     * Without this, a "Last hour" view at 20:00 would miss a log that ran
     * 18:30 → 19:30 even though half of it falls inside 19:00 → 20:00.
     */
    @Query("SELECT d FROM DeviceStatusLog d " +
            "WHERE d.startedAt >= :since " +
            "   OR d.endedAt IS NULL " +
            "   OR d.endedAt >= :since " +
            "ORDER BY d.startedAt DESC")
    List<DeviceStatusLog> findOverlappingWindow(@Param("since") LocalDateTime since);

    // Sum offline duration per device since a date (for SLA)
    @Query("SELECT d.deviceName, COALESCE(SUM(d.durationSeconds), 0) " +
            "FROM DeviceStatusLog d " +
            "WHERE d.status = 'OFFLINE' " +
            "AND d.startedAt >= :since " +
            "GROUP BY d.deviceName")
    List<Object[]> sumOfflineDurationSince(@Param("since") LocalDateTime since);

    // Count currently offline devices (endedAt IS NULL AND status = OFFLINE)
    @Query("SELECT d.deviceName FROM DeviceStatusLog d " +
            "WHERE d.status = 'OFFLINE' AND d.endedAt IS NULL")
    List<String> findCurrentlyOfflineDevices();

    // Used by history/offline-overlay chart
    List<DeviceStatusLog> findByDeviceNameAndStartedAtBetweenOrEndedAtBetween(
            String deviceName, LocalDateTime start1, LocalDateTime end1,
            LocalDateTime start2, LocalDateTime end2);
}