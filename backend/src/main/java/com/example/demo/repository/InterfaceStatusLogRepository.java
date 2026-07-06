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

    Optional<InterfaceStatusLog> findFirstByDeviceNameAndInterfaceNameAndEndedAtIsNull(
            String deviceName, String interfaceName);

    Optional<InterfaceStatusLog> findFirstByDeviceNameAndInterfaceNameAndStatusAndEndedAtIsNull(
            String deviceName, String interfaceName, InterfaceState status);

    List<InterfaceStatusLog> findByDeviceNameAndStartedAtAfterOrderByStartedAtAsc(
            String deviceName, LocalDateTime after);

    @Query("SELECT l FROM InterfaceStatusLog l " +
            "WHERE l.deviceName = :deviceName " +
            "AND (l.startedAt >= :since OR l.endedAt IS NULL OR l.endedAt >= :since) " +
            "ORDER BY l.startedAt ASC")
    List<InterfaceStatusLog> findOverlappingWindow(
            @Param("deviceName") String deviceName,
            @Param("since") LocalDateTime since);

    List<InterfaceStatusLog> findByStartedAtAfterOrderByStartedAtAsc(LocalDateTime after);

    List<InterfaceStatusLog> findByStatusAndEndedAtIsNull(InterfaceState status);

    List<InterfaceStatusLog> findByEndedAtIsNull();
}