package com.example.demo.Service;

import com.example.demo.model.AttackSession;
import com.example.demo.model.AttackSession.SessionSource;
import com.example.demo.model.AttackSession.SessionStatus;
import com.example.demo.model.AttackSession.Severity;
import com.example.demo.model.AuditLog;
import com.example.demo.model.DeviceStatusLog;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.DeviceStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * DeviceStatusService — يتتبع حالة online/offline لكل جهاز.
 *
 * Updated 2026 (minimal changes):
 *   - ALL_DEVICES: ssh-server → ftp-server (+ supervision-app)
 *   - Added markAsPoweredOff/markAsPoweredOn (alongside legacy markAsManuallyPaused/markAsResumed)
 *   - Original API methods (getTimeline, getOfflineDowntime, etc.) kept INTACT
 */
@Service
public class DeviceStatusService {

    @Autowired
    private DeviceStatusLogRepository statusLogRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private AttackSessionRepository attackSessionRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired(required = false)  // optional — system works without Email
    private EmailDigestService emailDigestService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private InterfaceStatusService interfaceStatusService;

    @Autowired
    private com.example.demo.repository.MetricRepository metricRepository;

    private final Map<String, LocalDateTime> lastMetricTime = new ConcurrentHashMap<>();

    // الأجهزة التي تم إيقافها يدوياً (Power Off OR pause - same effect)
    private final Set<String> manuallyPausedDevices = ConcurrentHashMap.newKeySet();

    // ─────────────────────────────────────────────────────────────
    // قائمة الأجهزة المُراقبة (محدّثة)
    // ─────────────────────────────────────────────────────────────
    private static final List<String> ALL_DEVICES = List.of(
            "edge-router", "core-router",
            "web-server", "dns-server",
            "ftp-server", "db-server",       // 🆕 ftp-server (كان ssh-server)
            "supervision-app"                // 🆕 يراقب نفسه
    );

    private static final int OFFLINE_THRESHOLD_SECONDS = 40;

    // ══════════════════════════════════════════════════════════════
    //  Manual pause API (LEGACY — kept for backward compat)
    // ══════════════════════════════════════════════════════════════

    public void markAsManuallyPaused(String deviceName) {
        manuallyPausedDevices.add(deviceName);
    }

    public void markAsResumed(String deviceName) {
        manuallyPausedDevices.remove(deviceName);
    }

    public boolean isManuallyPaused(String deviceName) {
        return manuallyPausedDevices.contains(deviceName);
    }

    // ══════════════════════════════════════════════════════════════
    //  Power Control API (NEW — used by DeviceControlController)
    //  ──────────────────────────────────────────────────────────
    //  These are aliases for the legacy methods above.
    // ══════════════════════════════════════════════════════════════

    public void markAsPoweredOff(String deviceName) {
        markAsManuallyPaused(deviceName);
        try {
            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                    "Device marked as powered-off: " + deviceName,
                    deviceName);
        } catch (Exception ignored) {}
    }

    public void markAsPoweredOn(String deviceName) {
        markAsResumed(deviceName);
        try {
            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                    "Device marked as powered-on: " + deviceName,
                    deviceName);
        } catch (Exception ignored) {}
    }

    public boolean isPoweredOff(String deviceName) {
        return manuallyPausedDevices.contains(deviceName);
    }

    // ══════════════════════════════════════════════════════════════
    //  Heartbeat — يُستدعى من MetricService عند استلام metric
    // ══════════════════════════════════════════════════════════════

    /**
     * Original method name (kept for backward compatibility).
     * MetricService calls this on every metric received.
     */
    public void deviceHeartbeat(String deviceName) {
        lastMetricTime.put(deviceName, LocalDateTime.now());

        // Auto-clear power-off flag if device is alive
        if (manuallyPausedDevices.contains(deviceName)) {
            manuallyPausedDevices.remove(deviceName);
        }

        // Close any open OFFLINE record
        Optional<DeviceStatusLog> openOffline =
                statusLogRepository.findByDeviceNameAndEndedAtIsNull(deviceName);

        if (openOffline.isPresent() &&
                openOffline.get().getStatus() == DeviceStatusLog.Status.OFFLINE) {

            DeviceStatusLog log = openOffline.get();
            log.setEndedAt(LocalDateTime.now());
            log.setDurationSeconds(
                    Duration.between(log.getStartedAt(), log.getEndedAt()).getSeconds()
            );
            statusLogRepository.save(log);

            // Open new ONLINE record
            DeviceStatusLog online = new DeviceStatusLog();
            online.setDeviceName(deviceName);
            online.setStatus(DeviceStatusLog.Status.ONLINE);
            statusLogRepository.save(online);

            // Notify frontend
            Map<String, Object> event = new HashMap<>();
            event.put("deviceName", deviceName);
            event.put("status", "ONLINE");
            event.put("timestamp", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/device-status", (Object) event);

            // Close any open device-offline AttackSession
            attackSessionRepository.findByDeviceNameAndStatus(
                            deviceName, SessionStatus.ACTIVE).stream()
                    .filter(s -> s.getSessionSource() == SessionSource.DEVICE_OFFLINE)
                    .forEach(s -> {
                        s.setStatus(SessionStatus.ENDED);
                        s.setEndedAt(LocalDateTime.now());
                        attackSessionRepository.save(s);
                    });
        }
    }

    /**
     * Alias — same as deviceHeartbeat.
     * Some code may call this name; both work.
     */
    public void recordHeartbeat(String deviceName) {
        deviceHeartbeat(deviceName);
    }

    // ══════════════════════════════════════════════════════════════
    //  Scheduled offline check
    // ══════════════════════════════════════════════════════════════
    @Scheduled(fixedRate = 15000)
    public void checkDeviceStatus() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(OFFLINE_THRESHOLD_SECONDS);

        for (String device : ALL_DEVICES) {
            LocalDateTime lastSeen = lastMetricTime.get(device);
            boolean isOffline = (lastSeen == null) || lastSeen.isBefore(threshold);

            if (isOffline) {
                Optional<DeviceStatusLog> current =
                        statusLogRepository.findByDeviceNameAndEndedAtIsNull(device);

                boolean alreadyOffline = current.isPresent()
                        && current.get().getStatus() == DeviceStatusLog.Status.OFFLINE;

                if (!alreadyOffline) {
                    // Close any open ONLINE record
                    current.ifPresent(log -> {
                        log.setEndedAt(LocalDateTime.now());
                        log.setDurationSeconds(
                                Duration.between(log.getStartedAt(), log.getEndedAt()).getSeconds()
                        );
                        statusLogRepository.save(log);
                    });

                    // Open new OFFLINE record (always — for SLA)
                    DeviceStatusLog offline = new DeviceStatusLog();
                    offline.setDeviceName(device);
                    offline.setStatus(DeviceStatusLog.Status.OFFLINE);
                    statusLogRepository.save(offline);

                    // Notify frontend
                    Map<String, Object> event = new HashMap<>();
                    event.put("deviceName", device);
                    event.put("status", "OFFLINE");
                    event.put("timestamp", LocalDateTime.now().toString());
                    messagingTemplate.convertAndSend("/topic/device-status", (Object) event);

                    // Only raise alert if not manually paused/powered-off
                    if (!manuallyPausedDevices.contains(device)) {
                        raiseDeviceOfflineAlert(device);
                    } else {
                        try {
                            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                                    "Device went offline (manually paused/powered-off): " + device,
                                    device);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Offline alert
    // ══════════════════════════════════════════════════════════════
    private void raiseDeviceOfflineAlert(String deviceName) {
        try {
            AttackSession session = new AttackSession();
            session.setDeviceName(deviceName);
            session.setAttackType("device_offline");
            session.setSessionSource(SessionSource.DEVICE_OFFLINE);
            session.setSeverity(Severity.CRITICAL);
            session.setStatus(SessionStatus.ACTIVE);
            session.setStartedAt(LocalDateTime.now());
            session.setLastSeenAt(LocalDateTime.now());
            attackSessionRepository.save(session);

            auditLogService.log(AuditLog.AuditType.AI,
                    "Device offline detected: " + deviceName,
                    deviceName);

            // ── Email instant alert (best-effort, never breaks offline detection) ──
            if (emailDigestService != null) {
                try { emailDigestService.sendInstantDeviceOffline(deviceName); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            // If something fails (e.g., AttackSession schema differs), don't crash
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Original API methods (UNCHANGED)
    //  ────────────────────────────────────────────────────────────
    //  هذه الدوال هي ما يستدعيها DeviceStatusController.
    //  حافظت عليها كما هي بالضبط.
    // ══════════════════════════════════════════════════════════════

    /**
     * Used by SLA tab.
     */
    public Map<String, Long> getOfflineDowntime(String period) {
        LocalDateTime since = parsePeriod(period);

        Map<String, Long> result = new HashMap<>();
        for (String device : ALL_DEVICES) {
            result.put(device, 0L);
        }

        // Add completed offline durations
        List<Object[]> offlineData = statusLogRepository.sumOfflineDurationSince(since);
        for (Object[] row : offlineData) {
            String deviceName = (String) row[0];
            Long seconds = ((Number) row[1]).longValue();
            result.merge(deviceName, seconds, Long::sum);
        }

        // Add ongoing offline durations
        for (String device : ALL_DEVICES) {
            statusLogRepository.findByDeviceNameAndEndedAtIsNull(device)
                    .ifPresent(log -> {
                        if (log.getStatus() == DeviceStatusLog.Status.OFFLINE
                                && log.getStartedAt().isAfter(since)) {
                            long ongoingSeconds = Duration.between(
                                    log.getStartedAt(), LocalDateTime.now()
                            ).getSeconds();
                            result.merge(device, ongoingSeconds, Long::sum);
                        }
                    });
        }

        return result;
    }

    /**
     * Timeline — status log entries for visualization.
     */
    public List<Map<String, Object>> getTimeline(String period) {
        LocalDateTime since = parsePeriod(period);
        List<DeviceStatusLog> logs = statusLogRepository.findOverlappingWindow(since);

        List<Map<String, Object>> result = new ArrayList<>();
        for (DeviceStatusLog log : logs) {
            Map<String, Object> entry = new HashMap<>();
            entry.put("id", log.getId());
            entry.put("deviceName", log.getDeviceName());
            entry.put("status", log.getStatus().name());
            entry.put("startedAt", log.getStartedAt().toString());
            entry.put("endedAt", log.getEndedAt() != null ? log.getEndedAt().toString() : null);
            entry.put("durationSeconds", log.getDurationSeconds());
            result.add(entry);
        }
        return result;
    }

    public List<String> getCurrentlyOfflineDevices() {
        return statusLogRepository.findCurrentlyOfflineDevices();
    }

    // ══════════════════════════════════════════════════════════════
    //  Public utility methods (used by other services if needed)
    // ══════════════════════════════════════════════════════════════

    public boolean isDeviceOnline(String deviceName) {
        LocalDateTime lastSeen = lastMetricTime.get(deviceName);
        if (lastSeen == null) return false;
        return Duration.between(lastSeen, LocalDateTime.now()).getSeconds()
                < OFFLINE_THRESHOLD_SECONDS;
    }

    public Map<String, Boolean> getAllDevicesOnline() {
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String device : ALL_DEVICES) {
            result.put(device, isDeviceOnline(device));
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  Helper
    // ══════════════════════════════════════════════════════════════

    private LocalDateTime parsePeriod(String period) {
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "6h" -> LocalDateTime.now().minusHours(6);
            case "24h" -> LocalDateTime.now().minusDays(1);
            case "7d" -> LocalDateTime.now().minusDays(7);
            default -> LocalDateTime.now().minusDays(30);
        };
    }

    /**
     * Startup reconciliation — handle orphan open records left from a
     * previous Spring Boot run that didn't shut down gracefully (kill -9,
     * crash, container OOM, power cut, etc.).
     *
     * For each device:
     *   - If there's an open record (ended_at is NULL):
     *       * Find the timestamp of its last received metric. That's the
     *         last moment we KNOW the device was alive.
     *       * If last_metric_time is older than OFFLINE_THRESHOLD_SECONDS
     *         relative to now, the device was offline during the gap.
     *           Close the open record at last_metric_time (truthful end).
     *           Open a fresh OFFLINE record from last_metric_time → now.
     *       * Otherwise (last_metric was very recent — Spring Boot just
     *         restarted and metrics are still flowing), leave the open
     *         record alone. heartbeat() will handle it naturally.
     *   - If there's no open record at all, leave it. The first heartbeat
     *     for this device will create one.
     *
     * Why this approach: the timeline should reflect reality. If we just
     * left the open ONLINE record alone, the timeline would show the
     * device as ONLINE through the whole Spring Boot downtime — which
     * misrepresents the actual outage.
     *
     * Note: we use last_metric_time (from MetricData) as the end timestamp,
     * NOT (now - downtime). The metric is the most precise "last alive"
     * signal we have.
     */
    @PostConstruct
    public void init() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineCutoff = now.minusSeconds(OFFLINE_THRESHOLD_SECONDS);

        for (String device : ALL_DEVICES) {
            Optional<DeviceStatusLog> currentOpt =
                    statusLogRepository.findByDeviceNameAndEndedAtIsNull(device);

            if (currentOpt.isEmpty()) {
                // No open record at all — wait for first heartbeat to create one.
                continue;
            }

            DeviceStatusLog current = currentOpt.get();

            // Look up last received metric for this device — our most
            // accurate "last sign of life" timestamp.
            LocalDateTime lastSeen = lookupLastMetricTime(device);

            if (lastSeen == null) {
                // Device has never sent a metric (fresh DB) — close the
                // dangling record at its own startedAt to avoid a 0-second
                // open hole, and don't open a new one. heartbeat() will
                // open ONLINE on first arrival.
                current.setEndedAt(current.getStartedAt());
                current.setDurationSeconds(0L);
                statusLogRepository.save(current);
                continue;
            }

            // If the last metric is recent (within OFFLINE_THRESHOLD_SECONDS),
            // the device might still be alive — Spring Boot just restarted
            // mid-flight. Leave the open record; heartbeat() will close
            // and reopen as needed.
            if (lastSeen.isAfter(offlineCutoff)) {
                continue;
            }

            // The device was last seen too long ago — there's a real gap
            // between its last metric and now. Close the open record at
            // last_seen (truthful end of activity), then open a fresh
            // OFFLINE record covering the gap.
            current.setEndedAt(lastSeen);
            current.setDurationSeconds(
                    Duration.between(current.getStartedAt(), lastSeen).getSeconds()
            );
            statusLogRepository.save(current);

            // Open OFFLINE for the gap. When heartbeat() arrives later, it
            // will close this OFFLINE and open a new ONLINE — same flow as
            // a regular offline-to-online transition.
            DeviceStatusLog offline = new DeviceStatusLog();
            offline.setDeviceName(device);
            offline.setStatus(DeviceStatusLog.Status.OFFLINE);
            offline.setStartedAt(lastSeen);
            // ended_at = null — still ongoing until first heartbeat
            statusLogRepository.save(offline);

            try {
                auditLogService.log(AuditLog.AuditType.AI,
                        "Reconciled orphan record on startup: " + device
                                + " (gap " + Duration.between(lastSeen, now).toMinutes() + "m)",
                        device);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Graceful shutdown — close every open DeviceStatusLog record.
     *
     * Without this, a clean Spring Boot stop would leave records with
     * ended_at = NULL forever, and the timeline would show the device as
     * still online during the next downtime (the bug this whole patch fixes).
     *
     * Mirrors InterfaceStatusService.closeAllOpenLogsOnShutdown().
     */
    @PreDestroy
    public void closeAllOpenLogsOnShutdown() {
        LocalDateTime now = LocalDateTime.now();
        try {
            // Find every open record (any status, any device)
            List<DeviceStatusLog> openLogs = new ArrayList<>();
            for (String device : ALL_DEVICES) {
                statusLogRepository.findByDeviceNameAndEndedAtIsNull(device)
                        .ifPresent(openLogs::add);
            }

            for (DeviceStatusLog log : openLogs) {
                log.setEndedAt(now);
                if (log.getStartedAt() != null) {
                    log.setDurationSeconds(
                            Duration.between(log.getStartedAt(), now).getSeconds()
                    );
                }
                statusLogRepository.save(log);
            }

            // Also close any open DEVICE_OFFLINE AttackSessions —
            // these were waiting for a heartbeat that's not coming during
            // shutdown. They reopen via raiseDeviceOfflineAlert() if the
            // gap continues after restart.
            attackSessionRepository.findByStatus(SessionStatus.ACTIVE).stream()
                    .filter(s -> s.getSessionSource() == SessionSource.DEVICE_OFFLINE)
                    .forEach(s -> {
                        s.setStatus(SessionStatus.ENDED);
                        s.setEndedAt(now);
                        attackSessionRepository.save(s);
                    });

            if (!openLogs.isEmpty()) {
                System.out.println("  🔌 Closed " + openLogs.size()
                        + " open device status logs before shutdown");
            }
        } catch (Exception e) {
            System.err.println("Error closing device status logs on shutdown: " + e.getMessage());
        }
    }

    /**
     * Helper: find the timestamp of the last metric received for a device.
     * Returns null if the device has never sent a metric.
     *
     * Used by init() to determine the truthful "last alive" moment when
     * reconciling orphan records from a non-graceful shutdown.
     */
    private LocalDateTime lookupLastMetricTime(String device) {
        try {
            // Try the in-memory cache first (current run only)
            LocalDateTime cached = lastMetricTime.get(device);
            if (cached != null) return cached;

            // Fall back to DB — last metric ever received for this device
            return metricRepository
                    .findTop1ByDeviceNameOrderByTimestampDesc(device)
                    .map(m -> m.getTimestamp())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}