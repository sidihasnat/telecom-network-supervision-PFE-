package com.example.demo.repository;

import com.example.demo.model.DeviceStatusLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceStatusLogRepository extends JpaRepository<DeviceStatusLog, Long> {

    Optional<DeviceStatusLog> findByDeviceNameAndEndedAtIsNull(String deviceName);

    List<DeviceStatusLog> findByStartedAtAfterOrderByStartedAtDesc(LocalDateTime after);

    List<DeviceStatusLog> findByDeviceNameAndStartedAtAfterOrderByStartedAtAsc(
            String deviceName, LocalDateTime after);


    @Query("SELECT d FROM DeviceStatusLog d " +
            "WHERE d.startedAt >= :since " +
            "   OR d.endedAt IS NULL " +
            "   OR d.endedAt >= :since " +
            "ORDER BY d.startedAt DESC")
    List<DeviceStatusLog> findOverlappingWindow(@Param("since") LocalDateTime since);

    @Query("SELECT d.deviceName, COALESCE(SUM(d.durationSeconds), 0) " +
            "FROM DeviceStatusLog d " +
            "WHERE d.status = 'OFFLINE' " +
            "AND d.startedAt >= :since " +
            "GROUP BY d.deviceName")
    List<Object[]> sumOfflineDurationSince(@Param("since") LocalDateTime since);

    @Query("SELECT d.deviceName FROM DeviceStatusLog d " +
            "WHERE d.status = 'OFFLINE' AND d.endedAt IS NULL")
    List<String> findCurrentlyOfflineDevices();

    List<DeviceStatusLog> findByDeviceNameAndStartedAtBetweenOrEndedAtBetween(
            String deviceName, LocalDateTime start1, LocalDateTime end1,
            LocalDateTime start2, LocalDateTime end2);
}