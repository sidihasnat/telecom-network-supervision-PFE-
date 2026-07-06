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

    @Autowired(required = false)
    private EmailDigestService emailDigestService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private InterfaceStatusService interfaceStatusService;

    @Autowired
    private com.example.demo.repository.MetricRepository metricRepository;

    private final Map<String, LocalDateTime> lastMetricTime = new ConcurrentHashMap<>();

    private final Set<String> manuallyPausedDevices = ConcurrentHashMap.newKeySet();

    private static final List<String> ALL_DEVICES = List.of(
            "edge-router", "core-router",
            "web-server", "dns-server",
            "ftp-server", "db-server",
            "supervision-app"
    );

    private static final int OFFLINE_THRESHOLD_SECONDS = 15;

    public void markAsManuallyPaused(String deviceName) {
        manuallyPausedDevices.add(deviceName);
    }

    public void markAsResumed(String deviceName) {
        manuallyPausedDevices.remove(deviceName);
    }

    public boolean isManuallyPaused(String deviceName) {
        return manuallyPausedDevices.contains(deviceName);
    }

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

    public void deviceHeartbeat(String deviceName) {
        lastMetricTime.put(deviceName, LocalDateTime.now());

        if (manuallyPausedDevices.contains(deviceName)) {
            manuallyPausedDevices.remove(deviceName);
        }

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

            DeviceStatusLog online = new DeviceStatusLog();
            online.setDeviceName(deviceName);
            online.setStatus(DeviceStatusLog.Status.ONLINE);
            statusLogRepository.save(online);

            Map<String, Object> event = new HashMap<>();
            event.put("deviceName", deviceName);
            event.put("status", "ONLINE");
            event.put("timestamp", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/device-status", (Object) event);

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

    public void recordHeartbeat(String deviceName) {
        deviceHeartbeat(deviceName);
    }

    @Scheduled(fixedRate = 5000)
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

                    current.ifPresent(log -> {
                        log.setEndedAt(LocalDateTime.now());
                        log.setDurationSeconds(
                                Duration.between(log.getStartedAt(), log.getEndedAt()).getSeconds()
                        );
                        statusLogRepository.save(log);
                    });

                    DeviceStatusLog offline = new DeviceStatusLog();
                    offline.setDeviceName(device);
                    offline.setStatus(DeviceStatusLog.Status.OFFLINE);
                    statusLogRepository.save(offline);

                    Map<String, Object> event = new HashMap<>();
                    event.put("deviceName", device);
                    event.put("status", "OFFLINE");
                    event.put("timestamp", LocalDateTime.now().toString());
                    messagingTemplate.convertAndSend("/topic/device-status", (Object) event);

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

    private void raiseDeviceOfflineAlert(String deviceName) {
        try {
            AttackSession session = new AttackSession();
            session.setDeviceName(deviceName);
            session.setInterfaceName("-");
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

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "NEW_ATTACK");
            notification.put("source", "DEVICE_OFFLINE");
            notification.put("deviceName", deviceName);
            notification.put("interfaceName", "-");
            notification.put("attackType", "device_offline");
            notification.put("severity", "CRITICAL");
            notification.put("timestamp", LocalDateTime.now().toString());
            messagingTemplate.convertAndSend("/topic/notifications", (Object) notification);

            if (emailDigestService != null) {
                try { emailDigestService.sendInstantDeviceOffline(deviceName); } catch (Exception ignored) {}
            }
        } catch (Exception e) {

            System.err.println("raiseDeviceOfflineAlert failed for " + deviceName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Map<String, Long> getOfflineDowntime(String period) {
        LocalDateTime since = parsePeriod(period);

        Map<String, Long> result = new HashMap<>();
        for (String device : ALL_DEVICES) {
            result.put(device, 0L);
        }

        List<Object[]> offlineData = statusLogRepository.sumOfflineDurationSince(since);
        for (Object[] row : offlineData) {
            String deviceName = (String) row[0];
            Long seconds = ((Number) row[1]).longValue();
            result.merge(deviceName, seconds, Long::sum);
        }

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

    private LocalDateTime parsePeriod(String period) {
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "6h" -> LocalDateTime.now().minusHours(6);
            case "24h" -> LocalDateTime.now().minusDays(1);
            case "7d" -> LocalDateTime.now().minusDays(7);
            default -> LocalDateTime.now().minusDays(30);
        };
    }

    @PostConstruct
    public void init() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime offlineCutoff = now.minusSeconds(OFFLINE_THRESHOLD_SECONDS);

        for (String device : ALL_DEVICES) {
            Optional<DeviceStatusLog> currentOpt =
                    statusLogRepository.findByDeviceNameAndEndedAtIsNull(device);

            if (currentOpt.isEmpty()) {

                continue;
            }

            DeviceStatusLog current = currentOpt.get();

            LocalDateTime lastSeen = lookupLastMetricTime(device);

            if (lastSeen == null) {

                current.setEndedAt(current.getStartedAt());
                current.setDurationSeconds(0L);
                statusLogRepository.save(current);
                continue;
            }

            if (lastSeen.isAfter(offlineCutoff)) {
                continue;
            }

            current.setEndedAt(lastSeen);
            current.setDurationSeconds(
                    Duration.between(current.getStartedAt(), lastSeen).getSeconds()
            );
            statusLogRepository.save(current);

            DeviceStatusLog offline = new DeviceStatusLog();
            offline.setDeviceName(device);
            offline.setStatus(DeviceStatusLog.Status.OFFLINE);
            offline.setStartedAt(lastSeen);

            statusLogRepository.save(offline);

            try {
                auditLogService.log(AuditLog.AuditType.AI,
                        "Reconciled orphan record on startup: " + device
                                + " (gap " + Duration.between(lastSeen, now).toMinutes() + "m)",
                        device);
            } catch (Exception ignored) {}
        }
    }

    @PreDestroy
    public void closeAllOpenLogsOnShutdown() {
        LocalDateTime now = LocalDateTime.now();
        try {

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

    private LocalDateTime lookupLastMetricTime(String device) {
        try {

            LocalDateTime cached = lastMetricTime.get(device);
            if (cached != null) return cached;

            return metricRepository
                    .findTop1ByDeviceNameOrderByTimestampDesc(device)
                    .map(m -> m.getTimestamp())
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
