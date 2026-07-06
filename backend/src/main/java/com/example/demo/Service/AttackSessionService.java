package com.example.demo.Service;

import com.example.demo.model.AiResult;
import com.example.demo.model.AlertRule;
import com.example.demo.model.AttackSession;
import com.example.demo.model.AttackSession.SessionStatus;
import com.example.demo.model.AttackSession.SessionSource;
import com.example.demo.model.AttackSession.Severity;
import com.example.demo.model.AuditLog;
import com.example.demo.model.MetricData;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.MetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class AttackSessionService {

    @Autowired
    private AttackSessionRepository sessionRepo;

    @Autowired
    private AuditLogRepository auditLogRepo;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PlaybookExecutionService playbookExecService;

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private com.example.demo.repository.ProtectionActionRepository protectionActionRepository;

    @Autowired(required = false)
    private EmailDigestService emailDigestService;

    private boolean isTransit(String attackType) {
        return "transit".equalsIgnoreCase(attackType);
    }

    public AttackSession processAttackPrediction(String deviceName,
                                                 String interfaceName,
                                                 String attackType,
                                                 Double confidence,
                                                 String topFeatures,
                                                 AiResult aiResult,
                                                 String topAttackersJson) {

        boolean transit = isTransit(attackType);

        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                        deviceName, interfaceName, attackType, SessionStatus.ACTIVE);

        AttackSession session;

        if (existing.isPresent()) {
            session = existing.get();
            session.setLastSeenAt(LocalDateTime.now());
            session.setPredictionCount(session.getPredictionCount() + 1);

            double totalConf = session.getAvgConfidence() * (session.getPredictionCount() - 1)
                    + confidence;
            session.setAvgConfidence(totalConf / session.getPredictionCount());

            if (confidence > session.getMaxConfidence()) {
                session.setMaxConfidence(confidence);
            }

        } else {
            session = new AttackSession();
            session.setDeviceName(deviceName);
            session.setInterfaceName(interfaceName);
            session.setAttackType(attackType);
            session.setStatus(SessionStatus.ACTIVE);
            session.setStartedAt(LocalDateTime.now());
            session.setLastSeenAt(LocalDateTime.now());
            session.setAvgConfidence(confidence);
            session.setMaxConfidence(confidence);
            session.setPredictionCount(1);
            session.setTopFeatures(topFeatures);

            List<String> newIps = extractIpsFromJson(topAttackersJson);
            if (!newIps.isEmpty()) {
                session.setAttackerIps(String.join(",", new HashSet<>(newIps)));
            }

            if (transit) {
                session.setAcknowledged(true);
                session.setEscalationLevel(0);
            } else {
                session.setAcknowledged(false);
                session.setEscalationLevel(1);
            }

            if (!transit) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "NEW_ATTACK");
                notification.put("deviceName", deviceName);
                notification.put("interfaceName", interfaceName);
                notification.put("attackType", attackType);
                notification.put("confidence", confidence);
                notification.put("timestamp", LocalDateTime.now().toString());

                notification.put("attackerIps", session.getAttackerIps());

                messagingTemplate.convertAndSend("/topic/notifications", (Object) notification);

                if (emailDigestService != null) {
                    try {
                        emailDigestService.sendInstantAiAttack(session);
                    } catch (Exception ignored) {}
                }
            }
        }

        if (aiResult != null) {
            aiResult.setAttackSession(session);
            session.getPredictions().add(aiResult);
        }

        List<String> newIps = extractIpsFromJson(topAttackersJson);
        if (!newIps.isEmpty()) {
            Set<String> uniqueIps = new HashSet<>();

            if (session.getAttackerIps() != null && !session.getAttackerIps().isBlank()) {
                uniqueIps.addAll(Arrays.asList(session.getAttackerIps().split(",")));
            }

            uniqueIps.addAll(newIps);
            session.setAttackerIps(String.join(",", uniqueIps));
        }

        AttackSession savedSession = sessionRepo.save(session);

        if (existing.isEmpty() && !transit) {
            try {
                String playbookName = playbookExecService.runAutoExecFor(savedSession);
                if (playbookName != null) {

                    AttackSession refreshed = sessionRepo.findById(savedSession.getId())
                            .orElse(savedSession);

                    try {
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type", "ATTACK_MITIGATED");
                        notif.put("sessionId", refreshed.getId());
                        notif.put("deviceName", refreshed.getDeviceName());
                        notif.put("attackType", refreshed.getAttackType());
                        notif.put("playbook", playbookName);
                        notif.put("auto", true);
                        notif.put("timestamp", LocalDateTime.now().toString());

                        messagingTemplate.convertAndSend("/topic/notifications", (Object) notif);
                    } catch (Throwable ignored) {}

                    return refreshed;
                }
            } catch (Throwable t) {
                System.err.println("Auto-exec playbook failed: " + t.getMessage());
            }
        }

        return savedSession;
    }

    public AttackSession processAttackPrediction1(String deviceName,
                                                 String interfaceName,
                                                 String attackType,
                                                 Double confidence,
                                                 String topFeatures,
                                                 AiResult aiResult,
                                                 String topAttackersJson) {

        boolean transit = isTransit(attackType);

        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                        deviceName, interfaceName, attackType, SessionStatus.ACTIVE);

        AttackSession session;

        if (existing.isPresent()) {
            session = existing.get();
            session.setLastSeenAt(LocalDateTime.now());
            session.setPredictionCount(session.getPredictionCount() + 1);

            double totalConf = session.getAvgConfidence() * (session.getPredictionCount() - 1)
                    + confidence;
            session.setAvgConfidence(totalConf / session.getPredictionCount());

            if (confidence > session.getMaxConfidence()) {
                session.setMaxConfidence(confidence);
            }

        } else {
            session = new AttackSession();
            session.setDeviceName(deviceName);
            session.setInterfaceName(interfaceName);
            session.setAttackType(attackType);
            session.setStatus(SessionStatus.ACTIVE);
            session.setStartedAt(LocalDateTime.now());
            session.setLastSeenAt(LocalDateTime.now());
            session.setAvgConfidence(confidence);
            session.setMaxConfidence(confidence);
            session.setPredictionCount(1);
            session.setTopFeatures(topFeatures);

            if (transit) {
                session.setAcknowledged(true);
                session.setEscalationLevel(0);
            } else {
                session.setAcknowledged(false);
                session.setEscalationLevel(1);
            }

            if (!transit) {
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "NEW_ATTACK");
                notification.put("deviceName", deviceName);
                notification.put("interfaceName", interfaceName);
                notification.put("attackType", attackType);
                notification.put("confidence", confidence);
                notification.put("timestamp", LocalDateTime.now().toString());
                messagingTemplate.convertAndSend("/topic/notifications",(Object) notification);
                if (emailDigestService != null) {
                    try { emailDigestService.sendInstantAiAttack(session); } catch (Exception ignored) {}
                }
            }
        }

        if (aiResult != null) {
            aiResult.setAttackSession(session);
            session.getPredictions().add(aiResult);
        }

        List<String> newIps = extractIpsFromJson(topAttackersJson);
        if (!newIps.isEmpty()) {
            Set<String> uniqueIps = new HashSet<>();

            if (session.getAttackerIps() != null && !session.getAttackerIps().isBlank()) {
                uniqueIps.addAll(Arrays.asList(session.getAttackerIps().split(",")));
            }

            uniqueIps.addAll(newIps);
            session.setAttackerIps(String.join(",", uniqueIps));
        }

        AttackSession savedSession = sessionRepo.save(session);

        if (existing.isEmpty() && !transit) {
            try {
                String playbookName = playbookExecService.runAutoExecFor(savedSession);
                if (playbookName != null) {

                    AttackSession refreshed = sessionRepo.findById(savedSession.getId())
                            .orElse(savedSession);
                    try {
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type", "ATTACK_MITIGATED");
                        notif.put("sessionId", refreshed.getId());
                        notif.put("deviceName", refreshed.getDeviceName());
                        notif.put("attackType", refreshed.getAttackType());
                        notif.put("playbook", playbookName);
                        notif.put("auto", true);
                        notif.put("timestamp", LocalDateTime.now().toString());
                        messagingTemplate.convertAndSend("/topic/notifications", (Object) notif);
                    } catch (Throwable ignored) { }
                    return refreshed;
                }
            } catch (Throwable t) {

                System.err.println("Auto-exec playbook failed (caught & swallowed): "
                        + t.getMessage());
            }
        }

        return savedSession;
    }

    public AttackSession processTriggerRuleFired(AlertRule rule,
                                                 String deviceName,
                                                 Double actualValue) {
        String attackType = "trigger:" + rule.getMetric() + rule.getConditionOp() + rule.getValue();

        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndRuleIdAndStatus(
                        deviceName, rule.getId(), SessionStatus.ACTIVE);

        AttackSession session;

        if (existing.isPresent()) {
            session = existing.get();
            session.setLastSeenAt(LocalDateTime.now());
            session.setPredictionCount(session.getPredictionCount() + 1);

            return sessionRepo.save(session);
        }

        session = new AttackSession();
        session.setDeviceName(deviceName);
        session.setInterfaceName("-");
        session.setAttackType(attackType);
        session.setStatus(SessionStatus.ACTIVE);
        session.setSessionSource(SessionSource.TRIGGER_RULE);
        session.setRuleId(rule.getId());

        Severity sev = Severity.WARNING;
        if (rule.getSeverity() == AlertRule.AlertSeverity.CRITICAL) {
            sev = Severity.CRITICAL;
        }
        session.setSeverity(sev);

        session.setStartedAt(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());

        session.setAvgConfidence(null);
        session.setMaxConfidence(null);
        session.setPredictionCount(1);
        session.setAcknowledged(false);
        session.setEscalationLevel(1);

        AttackSession saved = sessionRepo.save(session);

        try {
            playbookExecService.runAutoExecFor(saved);
        } catch (Throwable t) {
            System.err.println("Auto-exec playbook failed for trigger (caught & swallowed): "
                    + t.getMessage());
        }

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_ATTACK");
        notification.put("source", "TRIGGER_RULE");
        notification.put("deviceName", deviceName);
        notification.put("interfaceName", "-");
        notification.put("attackType", attackType);
        notification.put("ruleName", rule.getMetric() + " " + rule.getConditionOp() + " " + rule.getValue());
        notification.put("actualValue", actualValue);
        notification.put("severity", sev.name());
        notification.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/notifications", (Object) notification);

        AuditLog log = new AuditLog();
        log.setType(AuditLog.AuditType.SETTINGS);
        log.setDescription(String.format(
                "Trigger rule fired: %s %s %.2f (actual: %.2f) on %s [%s]",
                rule.getMetric(), rule.getConditionOp(), rule.getValue(),
                actualValue, deviceName, sev.name()));
        log.setDeviceName(deviceName);
        auditLogRepo.save(log);

        if (emailDigestService != null) {
            try { emailDigestService.sendInstantTriggerRule(saved, actualValue); } catch (Exception ignored) {}
        }

        return saved;
    }

    @Scheduled(fixedRate = 10000)
    public void closeExpiredSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<AttackSession> stale = sessionRepo.findStaleSessions(threshold);

        for (AttackSession session : stale) {
            if (session.getMitigatedAt() != null) {
                session.setStatus(SessionStatus.MITIGATED);
            } else {
                session.setStatus(SessionStatus.ENDED);
            }
            session.setEndedAt(LocalDateTime.now());
            sessionRepo.save(session);

            if (isTransit(session.getAttackType())) continue;

            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "ATTACK_ENDED");
            notification.put("source", session.getSessionSource() != null
                    ? session.getSessionSource().name() : "AI");
            notification.put("sessionId", session.getId());
            notification.put("deviceName", session.getDeviceName());
            notification.put("interfaceName", session.getInterfaceName());
            notification.put("attackType", session.getAttackType());
            notification.put("duration", session.getDurationFormatted());
            messagingTemplate.convertAndSend("/topic/notifications",(Object) notification);

            AuditLog log = new AuditLog();
            String description;
            if (session.getSessionSource() == SessionSource.TRIGGER_RULE) {
                log.setType(AuditLog.AuditType.SETTINGS);
                description = "Trigger rule cleared: " + session.getAttackType()
                        + " on " + session.getDeviceName()
                        + " (duration: " + session.getDurationFormatted() + ")";
            } else if (session.getSessionSource() == SessionSource.INTERFACE_DOWN) {
                log.setType(AuditLog.AuditType.AI);
                description = "Interface recovered: " + session.getDeviceName()
                        + "::" + session.getInterfaceName()
                        + " (down for: " + session.getDurationFormatted() + ")";
            } else {
                log.setType(AuditLog.AuditType.AI);
                description = "Attack ended: " + session.getAttackType()
                        + " on " + session.getDeviceName() + "::" + session.getInterfaceName()
                        + " (duration: " + session.getDurationFormatted() + ")";
            }
            log.setDescription(description);
            log.setDeviceName(session.getDeviceName());
            auditLogRepo.save(log);

        }
    }

    public AttackSession acknowledge(Long sessionId) {
        AttackSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        session.setAcknowledged(true);
        session.setAcknowledgedAt(LocalDateTime.now());

        AuditLog log = new AuditLog();
        log.setType(AuditLog.AuditType.ACKNOWLEDGE);
        log.setDescription("Admin acknowledged " + session.getAttackType()
                + " alert on " + session.getDeviceName());
        log.setDeviceName(session.getDeviceName());
        auditLogRepo.save(log);

        return sessionRepo.save(session);
    }

    public List<AttackSession> getActiveSessions() {
        return sessionRepo.findByStatus(SessionStatus.ACTIVE);
    }

    public List<AttackSession> getHistory(String deviceName, String period) {
        List<SessionStatus> endedStatuses = List.of(SessionStatus.ENDED, SessionStatus.MITIGATED);
        LocalDateTime after = parsePeriod(period);

        if (deviceName != null && !deviceName.isBlank() && after != null) {
            return sessionRepo.findOverlappingByDeviceAndStatusIn(
                    deviceName, endedStatuses, after);
        } else if (deviceName != null && !deviceName.isBlank()) {
            return sessionRepo.findByDeviceNameAndStatusInOrderByStartedAtDesc(
                    deviceName, endedStatuses);
        } else if (after != null) {
            return sessionRepo.findOverlappingByStatusIn(endedStatuses, after);
        } else {
            return sessionRepo.findByStatusInOrderByStartedAtDesc(endedStatuses);
        }
    }

    public List<AttackSession> getTimelineSessions(String period) {
        List<SessionStatus> allStatuses = List.of(
                SessionStatus.ACTIVE, SessionStatus.ENDED, SessionStatus.MITIGATED);
        LocalDateTime after = parsePeriod(period);
        if (after == null) {
            return sessionRepo.findByStatusInOrderByStartedAtDesc(allStatuses);
        }
        return sessionRepo.findOverlappingByStatusIn(allStatuses, after);
    }

    public List<AttackSession> getRecentSessions() {
        return sessionRepo.findTop10ByOrderByStartedAtDesc();
    }

    public List<AttackSession> getUnacknowledged() {
        return sessionRepo.findUnacknowledgedNonTransit(SessionStatus.ACTIVE);
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalActive", sessionRepo.countByStatus(SessionStatus.ACTIVE));
        stats.put("totalEnded", sessionRepo.countByStatus(SessionStatus.ENDED));
        stats.put("totalMitigated", sessionRepo.countByStatus(SessionStatus.MITIGATED));

        List<Object[]> byDevice = sessionRepo.countByDevice();
        if (!byDevice.isEmpty()) {
            stats.put("mostTargeted", byDevice.get(0)[0]);
        }
        List<Object[]> byType = sessionRepo.countByAttackType();
        if (!byType.isEmpty()) {
            stats.put("mostCommon", byType.get(0)[0]);
        }
        return stats;
    }

    public Map<String, Long> getDowntime(String period) {
        LocalDateTime since = parsePeriod(period);
        if (since == null) since = LocalDateTime.now().minusDays(30);

        List<Object[]> rows = sessionRepo.getDowntimeByDevice(since);
        Map<String, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((String) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }

    private LocalDateTime parsePeriod(String period) {
        if (period == null) return null;
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> null;
        };
    }

    public Map<String, Object> getImpact(Long sessionId) {
        AttackSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            return null;
        }

        LocalDateTime startedAt = session.getStartedAt();
        LocalDateTime endedAt = session.getEndedAt() != null
                ? session.getEndedAt()
                : LocalDateTime.now();

        LocalDateTime beforeStart = startedAt.minusMinutes(2);
        LocalDateTime afterEnd = endedAt.plusMinutes(2);

        LocalDateTime now = LocalDateTime.now();
        if (afterEnd.isAfter(now)) {
            afterEnd = now;
        }

        List<MetricData> allMetrics = metricRepository
                .findByDeviceNameAndTimestampBetweenOrderByTimestampAsc(
                        session.getDeviceName(), beforeStart, afterEnd);

        List<MetricData> metricsBefore = new ArrayList<>();
        List<MetricData> metricsDuring = new ArrayList<>();
        List<MetricData> metricsAfter = new ArrayList<>();

        for (MetricData m : allMetrics) {
            LocalDateTime t = m.getTimestamp();
            if (t == null) continue;

            if (t.isBefore(startedAt)) {
                metricsBefore.add(m);
            } else if (t.isAfter(endedAt)) {
                metricsAfter.add(m);
            } else {
                metricsDuring.add(m);
            }
        }

        List<AuditLog> auditEvents = auditLogRepo.findByTimestampAfterOrderByTimestampDesc(
                beforeStart);
        final LocalDateTime windowEnd = afterEnd;
        final String targetDevice = session.getDeviceName();
        final String attackType = session.getAttackType();

        auditEvents.removeIf(e -> {
            if (e.getTimestamp() == null) return true;
            if (e.getTimestamp().isAfter(windowEnd)) return true;

            if (e.getDeviceName() == null) return true;
            if (!e.getDeviceName().equals(targetDevice)) return true;

            AuditLog.AuditType type = e.getType();
            if (type == AuditLog.AuditType.AI) {

                String desc = e.getDescription() == null ? "" : e.getDescription();
                return !desc.contains(attackType);
            }
            if (type == AuditLog.AuditType.PROTECTION) {
                return false;
            }
            if (type == AuditLog.AuditType.DEVICE_CONTROL) {
                return false;
            }
            return true;
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("metricsBefore", metricsBefore);
        result.put("metricsDuring", metricsDuring);
        result.put("metricsAfter", metricsAfter);
        result.put("auditLogs", auditEvents);

        try {
            result.put("protectionActions",
                    protectionActionRepository.findByAttackSessionId(sessionId));
        } catch (Exception e) {
            result.put("protectionActions", java.util.Collections.emptyList());
        }

        Map<String, String> window = new LinkedHashMap<>();
        window.put("beforeStart", beforeStart.toString());
        window.put("attackStart", startedAt.toString());
        window.put("attackEnd", endedAt.toString());
        window.put("afterEnd", afterEnd.toString());
        result.put("window", window);

        return result;
    }

    private List<String> extractIpsFromJson(String json) {
        if (json == null || json.isBlank() || json.equals("{}")) return Collections.emptyList();
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(json, Map.class);
            return new ArrayList<>(parsed.keySet());
        } catch (Exception e) { return Collections.emptyList(); }
    }
}
