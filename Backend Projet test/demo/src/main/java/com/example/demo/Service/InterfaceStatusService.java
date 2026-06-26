package com.example.demo.Service;

import com.example.demo.model.AttackSession;
import com.example.demo.model.AttackSession.SessionSource;
import com.example.demo.model.AttackSession.SessionStatus;
import com.example.demo.model.AttackSession.Severity;
import com.example.demo.model.AuditLog;
import com.example.demo.model.InterfaceMetric;
import com.example.demo.model.InterfaceStatusLog;
import com.example.demo.model.InterfaceStatusLog.InterfaceState;
import com.example.demo.model.MetricData;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.InterfaceStatusLogRepository;
import com.example.demo.repository.MetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import jakarta.annotation.PreDestroy;

/**
 * InterfaceStatusService — tracks UP/DOWN transitions of device interfaces.
 *
 * Rewritten April 2026. Now mirrors DeviceStatusService lifecycle:
 *   - Every metric's `isUp` flag drives state transitions.
 *   - Both UP and DOWN states produce log entries (like DeviceStatusLog).
 *   - @PreDestroy closes all open records on shutdown.
 *
 * This makes the Interfaces timeline behave exactly like the Device timeline:
 *   - Green bars for UP periods (from the log entries, not the background)
 *   - Grey bars for DOWN periods
 *   - Any gap between the log coverage and "now" is implicit (handled by the frontend)
 *
 * Works for routers AND servers (servers typically have only eth0 but the logic
 * is identical — if eth0 goes down the metric's isUp=false will flip state).
 */
@Service
public class InterfaceStatusService {

    @Autowired
    private InterfaceStatusLogRepository logRepo;

    @Autowired
    private AttackSessionRepository sessionRepo;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MetricRepository metricRepository;

    // ══════════════════════════════════════════════════════════════
    //  Metric-driven state machine
    // ══════════════════════════════════════════════════════════════

    /**
     * Called once per metric snapshot by MetricService.saveMetric().
     * Iterates all interfaces on this device and updates their state log.
     */
    public void processMetric(MetricData metric) {
        if (metric == null || metric.getInterfaces() == null) return;

        String deviceName = metric.getDeviceName();
        for (InterfaceMetric iface : metric.getInterfaces()) {
            // Default isUp to true if the field is missing (backward compat with older metrics)
            boolean currentlyUp = iface.getIsUp() == null ? true : iface.getIsUp();
            reconcileInterface(deviceName, iface.getInterfaceName(), currentlyUp);
        }
    }

    /**
     * Core state-machine for a single interface.
     *
     * Cases (analogous to DeviceStatusService.deviceHeartbeat):
     *   1. No open log           → open new log with current state
     *   2. Open log with SAME    → nothing to do (state unchanged)
     *   3. Open log with DIFFERENT state → close it, open new one with current state
     */
    private void reconcileInterface(String deviceName, String interfaceName, boolean currentlyUp) {
        InterfaceState desired = currentlyUp ? InterfaceState.UP : InterfaceState.DOWN;

        Optional<InterfaceStatusLog> currentOpen = logRepo
                .findFirstByDeviceNameAndInterfaceNameAndEndedAtIsNull(
                        deviceName, interfaceName);

        if (currentOpen.isEmpty()) {
            // No open log → first time we see this interface → open current state
            openLog(deviceName, interfaceName, desired);

            // If DOWN on first contact, also raise alert
            if (desired == InterfaceState.DOWN) {
                raiseInterfaceDownAlert(deviceName, interfaceName);
            }
            return;
        }

        InterfaceStatusLog log = currentOpen.get();

        if (log.getStatus() == desired) {
            // Same state — no transition, nothing to do
            return;
        }

        // State changed → close old log, open new one
        LocalDateTime now = LocalDateTime.now();
        log.setEndedAt(now);
        logRepo.save(log);
        openLog(deviceName, interfaceName, desired);

        if (desired == InterfaceState.DOWN) {
            // UP → DOWN: raise alert + create AttackSession
            raiseInterfaceDownAlert(deviceName, interfaceName);
        } else {
            // DOWN → UP: close AttackSession + recovery notif
            String durFormatted = formatDuration(
                    Duration.between(log.getStartedAt(), now).getSeconds());
            closeInterfaceDownSession(deviceName, interfaceName, durFormatted);
        }
    }

    private void openLog(String deviceName, String interfaceName, InterfaceState state) {
        InterfaceStatusLog log = new InterfaceStatusLog();
        log.setDeviceName(deviceName);
        log.setInterfaceName(interfaceName);
        log.setStatus(state);
        log.setStartedAt(LocalDateTime.now());
        logRepo.save(log);
    }

    // ══════════════════════════════════════════════════════════════
    //  Alerting (AttackSession + notifications)
    // ══════════════════════════════════════════════════════════════

    private void raiseInterfaceDownAlert(String deviceName, String interfaceName) {
        // Skip if there's already an open INTERFACE_DOWN session (avoid duplicates on restart)
        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                        deviceName, interfaceName, "interface_down", SessionStatus.ACTIVE);
        if (existing.isPresent()) return;

        AttackSession session = new AttackSession();
        session.setDeviceName(deviceName);
        session.setInterfaceName(interfaceName);
        session.setAttackType("interface_down");
        session.setStatus(SessionStatus.ACTIVE);
        session.setSessionSource(SessionSource.INTERFACE_DOWN);
        session.setSeverity(Severity.WARNING);
        session.setStartedAt(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        // Confidence is meaningless for non-AI events — this is a certain fact,
        // not a model prediction. Leave both fields null.
        session.setAvgConfidence(null);
        session.setMaxConfidence(null);
        session.setPredictionCount(1);
        session.setAcknowledged(false);
        session.setEscalationLevel(1);
        sessionRepo.save(session);

        // Toast + bell notification
        Map<String, Object> notif = new HashMap<>();
        notif.put("type", "NEW_ATTACK");
        notif.put("source", "INTERFACE_DOWN");
        notif.put("deviceName", deviceName);
        notif.put("interfaceName", interfaceName);
        notif.put("attackType", "interface_down");
        notif.put("severity", "WARNING");
        notif.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/notifications", (Object) notif);

        auditLogService.log(AuditLog.AuditType.AI,
                "Interface DOWN detected: " + deviceName + "::" + interfaceName);
    }

    private void closeInterfaceDownSession(String deviceName, String interfaceName,
                                           String durationFormatted) {
        sessionRepo.findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                        deviceName, interfaceName, "interface_down", SessionStatus.ACTIVE)
                .ifPresent(s -> {
                    s.setStatus(SessionStatus.ENDED);
                    s.setEndedAt(LocalDateTime.now());
                    sessionRepo.save(s);

                    // Recovery notif
                    Map<String, Object> notif = new HashMap<>();
                    notif.put("type", "ATTACK_ENDED");
                    notif.put("source", "INTERFACE_DOWN");
                    notif.put("deviceName", deviceName);
                    notif.put("interfaceName", interfaceName);
                    notif.put("attackType", "interface_down");
                    notif.put("duration", s.getDurationFormatted());
                    messagingTemplate.convertAndSend("/topic/notifications", (Object) notif);

                    auditLogService.log(AuditLog.AuditType.AI,
                            "Interface recovered: " + deviceName + "::" + interfaceName
                                    + " (down for: " + s.getDurationFormatted() + ")");
                });
    }

    // ══════════════════════════════════════════════════════════════
    //  Graceful shutdown — close all open logs
    // ══════════════════════════════════════════════════════════════

    @PreDestroy
    public void closeAllOpenLogsOnShutdown() {
        LocalDateTime now = LocalDateTime.now();
        List<InterfaceStatusLog> openLogs = logRepo.findByEndedAtIsNull();
        for (InterfaceStatusLog log : openLogs) {
            log.setEndedAt(now);
            logRepo.save(log);
        }
        if (!openLogs.isEmpty()) {
            System.out.println("  🔌 Closed " + openLogs.size()
                    + " open interface status logs before shutdown");
        }
    }

    /**
     * Handle a device going fully offline.
     *
     * Logic:
     *   1. Close every open interface log for this device (both UP and DOWN).
     *   2. Close any open interface_down AttackSessions for this device's
     *      interfaces (they'll be superseded by the device-offline session).
     *   3. Open a fresh DOWN log for each interface we know existed on this
     *      device (derived from its last recorded metric). This makes the
     *      timeline show a continuous grey "not working" segment for the
     *      whole outage, rather than a "no data" gap.
     *
     * When the device comes back and starts sending metrics again, the first
     * metric's `isUp=true` flags will flip these DOWN logs to UP via the
     * normal reconcileInterface path.
     */
    public void closeAllForDevice(String deviceName) {
        LocalDateTime now = LocalDateTime.now();

        // Step 1: collect the list of interfaces we want to reopen BEFORE closing
        // current logs. Prefer the currently-open logs (authoritative), fall back
        // to the last metric if none are open (first-time offline).
        Set<String> knownInterfaces = new LinkedHashSet<>();
        List<InterfaceStatusLog> openLogs = logRepo.findByEndedAtIsNull();
        for (InterfaceStatusLog log : openLogs) {
            if (deviceName.equals(log.getDeviceName())) {
                knownInterfaces.add(log.getInterfaceName());
            }
        }
        if (knownInterfaces.isEmpty()) {
            // No open logs — look at the last metric to find interface names
            List<MetricData> recent = metricRepository
                    .findTop20ByDeviceNameOrderByTimestampDesc(deviceName);
            if (recent != null && !recent.isEmpty()) {
                MetricData latest = recent.get(0);
                if (latest.getInterfaces() != null) {
                    for (InterfaceMetric iface : latest.getInterfaces()) {
                        if (iface.getInterfaceName() != null) {
                            knownInterfaces.add(iface.getInterfaceName());
                        }
                    }
                }
            }
        }

        // Step 2: close open logs for this device
        int closed = 0;
        for (InterfaceStatusLog log : openLogs) {
            if (!deviceName.equals(log.getDeviceName())) continue;
            log.setEndedAt(now);
            logRepo.save(log);
            closed++;

            // Close any open interface_down session so it doesn't linger
            sessionRepo.findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                            deviceName, log.getInterfaceName(), "interface_down",
                            SessionStatus.ACTIVE)
                    .ifPresent(s -> {
                        s.setStatus(SessionStatus.ENDED);
                        s.setEndedAt(now);
                        sessionRepo.save(s);
                    });
        }

        // Step 3: open a fresh DOWN log for each known interface so the
        // timeline continues to render a meaningful state during the outage.
        // (We don't raise new interface_down AttackSessions here — the
        // device-offline session covers the outage at the device level.)
        for (String ifaceName : knownInterfaces) {
            InterfaceStatusLog down = new InterfaceStatusLog();
            down.setDeviceName(deviceName);
            down.setInterfaceName(ifaceName);
            down.setStatus(InterfaceState.DOWN);
            down.setStartedAt(now);
            logRepo.save(down);
        }

        if (closed > 0 || !knownInterfaces.isEmpty()) {
            System.out.println("  🔌 Device " + deviceName
                    + " offline: closed " + closed + " old logs, opened "
                    + knownInterfaces.size() + " DOWN logs");
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private String formatDuration(long s) {
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }
}