package com.example.demo.repository;

import com.example.demo.model.InterfaceStatusLog;
import com.example.demo.model.InterfaceStatusLog.InterfaceState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface InterfaceStatusLogRepository extends JpaRepository<InterfaceStatusLog, Long> {

    // Find the currently open (no endedAt) log for an interface — regardless of UP/DOWN
    Optional<InterfaceStatusLog> findFirstByDeviceNameAndInterfaceNameAndEndedAtIsNull(
            String deviceName, String interfaceName);

    // Backward-compat: find open record with a specific status
    Optional<InterfaceStatusLog> findFirstByDeviceNameAndInterfaceNameAndStatusAndEndedAtIsNull(
            String deviceName, String interfaceName, InterfaceState status);

    // Timeline: all events for a specific device after a given timestamp
    List<InterfaceStatusLog> findByDeviceNameAndStartedAtAfterOrderByStartedAtAsc(
            String deviceName, LocalDateTime after);

    // All events for a device overlapping a window (for timeline)
    @Query("SELECT l FROM InterfaceStatusLog l " +
            "WHERE l.deviceName = :deviceName " +
            "AND (l.startedAt >= :since OR l.endedAt IS NULL OR l.endedAt >= :since) " +
            "ORDER BY l.startedAt ASC")
    List<InterfaceStatusLog> findOverlappingWindow(
            @Param("deviceName") String deviceName,
            @Param("since") LocalDateTime since);

    // Timeline: all events (all devices) since a timestamp
    List<InterfaceStatusLog> findByStartedAtAfterOrderByStartedAtAsc(LocalDateTime after);

    // All currently-DOWN interfaces
    List<InterfaceStatusLog> findByStatusAndEndedAtIsNull(InterfaceState status);

    // All open logs (for shutdown cleanup)
    List<InterfaceStatusLog> findByEndedAtIsNull();
}