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

    public void processMetric(MetricData metric) {
        if (metric == null || metric.getInterfaces() == null) return;

        String deviceName = metric.getDeviceName();
        for (InterfaceMetric iface : metric.getInterfaces()) {

            boolean currentlyUp = iface.getIsUp() == null ? true : iface.getIsUp();
            reconcileInterface(deviceName, iface.getInterfaceName(), currentlyUp);
        }
    }

    private void reconcileInterface(String deviceName, String interfaceName, boolean currentlyUp) {
        InterfaceState desired = currentlyUp ? InterfaceState.UP : InterfaceState.DOWN;

        Optional<InterfaceStatusLog> currentOpen = logRepo
                .findFirstByDeviceNameAndInterfaceNameAndEndedAtIsNull(
                        deviceName, interfaceName);

        if (currentOpen.isEmpty()) {

            openLog(deviceName, interfaceName, desired);

            if (desired == InterfaceState.DOWN) {
                raiseInterfaceDownAlert(deviceName, interfaceName);
            }
            return;
        }

        InterfaceStatusLog log = currentOpen.get();

        if (log.getStatus() == desired) {

            return;
        }

        LocalDateTime now = LocalDateTime.now();
        log.setEndedAt(now);
        logRepo.save(log);
        openLog(deviceName, interfaceName, desired);

        if (desired == InterfaceState.DOWN) {

            raiseInterfaceDownAlert(deviceName, interfaceName);
        } else {

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

    private void raiseInterfaceDownAlert(String deviceName, String interfaceName) {

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

        session.setAvgConfidence(null);
        session.setMaxConfidence(null);
        session.setPredictionCount(1);
        session.setAcknowledged(false);
        session.setEscalationLevel(1);
        sessionRepo.save(session);

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

    public void closeAllForDevice(String deviceName) {
        LocalDateTime now = LocalDateTime.now();

        Set<String> knownInterfaces = new LinkedHashSet<>();
        List<InterfaceStatusLog> openLogs = logRepo.findByEndedAtIsNull();
        for (InterfaceStatusLog log : openLogs) {
            if (deviceName.equals(log.getDeviceName())) {
                knownInterfaces.add(log.getInterfaceName());
            }
        }
        if (knownInterfaces.isEmpty()) {

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

        int closed = 0;
        for (InterfaceStatusLog log : openLogs) {
            if (!deviceName.equals(log.getDeviceName())) continue;
            log.setEndedAt(now);
            logRepo.save(log);
            closed++;

            sessionRepo.findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                            deviceName, log.getInterfaceName(), "interface_down",
                            SessionStatus.ACTIVE)
                    .ifPresent(s -> {
                        s.setStatus(SessionStatus.ENDED);
                        s.setEndedAt(now);
                        sessionRepo.save(s);
                    });
        }

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

    private String formatDuration(long s) {
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }
}
