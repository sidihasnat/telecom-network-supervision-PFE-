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

/**
 * AttackSessionService — إدارة تجميع الهجمات.
 *
 * يُستدعى من MetricService.savePrediction() عند كل prediction غير normal.
 *
 * المنطق:
 *   1. prediction هجومي → نبحث عن session مفتوح لنفس device+interface+attackType
 *      → موجود = نحدثه
 *      → غير موجود = ننشئ جديد + نرسل notification
 *   2. كل 10 ثواني (Scheduled) → نفحص sessions لم تتحدث منذ 30+ ثانية → نغلقها
 */
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

    @Autowired(required = false)  // optional — system works without Email
    private EmailDigestService emailDigestService;

    // ═══════════════════════════════════════════════════════════
    //  Transit detection
    // ═══════════════════════════════════════════════════════════

    /**
     * transit = الـ router يمرر هجوم لكنه ليس الهدف.
     *
     * القرار (Option B):
     *   transit يُسجل في AttackSession (يظهر في History + Live)
     *   لكن بدون: notification, escalation, acknowledge, SLA downtime
     *   لأن الهجوم الحقيقي على الجهاز المستهدف — وهناك session منفصل له
     */
    private boolean isTransit(String attackType) {
        return "transit".equalsIgnoreCase(attackType);
    }

    // ═══════════════════════════════════════════════════════════
    //  Core: معالجة prediction هجومي
    // ═══════════════════════════════════════════════════════════

    /**
     * يُستدعى من MetricService.savePrediction() عند prediction غير normal.
     *
     * @param deviceName    اسم الجهاز (e.g. "web-server")
     * @param interfaceName اسم الـ interface (e.g. "eth1")
     * @param attackType    نوع الهجوم (e.g. "synflood") أو "transit"
     * @param confidence    الثقة (0.0-1.0)
     * @param topFeatures   JSON string — أهم الـ features
     * @param aiResult      الـ AiResult المرتبط (لربطه بالـ session)
     * @return الـ AttackSession (جديد أو محدّث)
     */
    public AttackSession processAttackPrediction(String deviceName,
                                                 String interfaceName,
                                                 String attackType,
                                                 Double confidence,
                                                 String topFeatures,
                                                 AiResult aiResult) {

        boolean transit = isTransit(attackType);

        // Recherche session active existante
        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndInterfaceNameAndAttackTypeAndStatus(
                        deviceName, interfaceName, attackType, SessionStatus.ACTIVE);

        AttackSession session;

        if (existing.isPresent()) {
            // ── Mettre à jour session existante ──────────────
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
            // ── Créer nouvelle session ──────────────
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

            // ── Notification ONLY pour attaques réelles ──────────────
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

        // Lier AiResult au session
        if (aiResult != null) {
            aiResult.setAttackSession(session);
            session.getPredictions().add(aiResult);
        }

        // Save the session — both new and updated branches end up here.
        AttackSession savedSession = sessionRepo.save(session);

        // ── SOAR: auto-execute matching playbook with autoExecute=true ─────
        // CRITICAL: only run on FIRST detection (new session, not refresh).
        //
        // Why: if we ran auto-exec on every prediction, the playbook would
        // fire every 5-10 seconds for the entire duration of the attack —
        // flooding the target container with the same iptables/sysctl
        // commands, causing race conditions and "rule already exists"
        // failures that hide the initial successful run.
        //
        // The check `existing.isEmpty()` (computed at the top of this
        // method) is the cleanest signal: this prediction CREATED a new
        // session, vs. refreshed an existing one.
        //
        // CRITICAL FIX (2026-05-08): runAutoExecFor must NOT be allowed to
        // poison the surrounding transaction. The previous version would
        // catch Exception, but if the playbook's executePlaybook throws
        // anything that marks the JPA transaction as rollback-only (which
        // Hibernate does on ANY persistence error inside a @Transactional
        // boundary), the entire MetricService.savePrediction() transaction
        // is lost — INCLUDING the AttackSession we just saved 5 lines above.
        // Symptom: attack visible in Sidebar/Topology (from WebSocket) but
        // missing from DB → Security Live shows "normal".
        //
        // The fix is to call runAutoExecFor in a way that, even if it fails,
        // doesn't taint THIS transaction. Two protections:
        //   1. PlaybookExecutionService.runAutoExecFor must be annotated
        //      @Transactional(propagation = REQUIRES_NEW) — see patch below.
        //   2. We catch Throwable here (not just Exception) so even Errors
        //      can't escape and abort the outer save.
        if (existing.isEmpty() && !transit) {
            try {
                String playbookName = playbookExecService.runAutoExecFor(savedSession);
                if (playbookName != null) {
                    // Refresh from DB so we see the mitigatedAt that
                    // executePlaybook() just stamped, then broadcast
                    // ATTACK_MITIGATED so the UI flips the row to the
                    // orange "⏳ Mitigated (monitoring)" state without
                    // waiting for the next polling refresh.
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
                // Catch Throwable, not Exception — even errors from the
                // playbook subsystem must not break the detection flow.
                // The session is already saved; auto-exec is a best-effort.
                System.err.println("Auto-exec playbook failed (caught & swallowed): "
                        + t.getMessage());
            }
        }

        return savedSession;
    }


    // ═══════════════════════════════════════════════════════════
    //  🆕 Trigger Rules — threshold-based incident tracking
    // ═══════════════════════════════════════════════════════════

    /**
     * Called by MetricService.evaluateTriggerRules() when a rule condition is met.
     *
     * Mirrors processAttackPrediction but for admin-defined rules:
     *   - Creates an AttackSession with sessionSource=TRIGGER_RULE
     *   - Severity from AlertRule (WARNING / CRITICAL)
     *   - attackType = "trigger:cpuUsage>80"  (human readable)
     *   - avgConfidence used to store the actual metric value
     *
     * Same lifecycle as attacks:
     *   - Active until condition stops being met for 30s
     *   - Shows in Timeline, Security Live, TopologyMap, SidePanel AI Result
     *   - Sends WebSocket notifications + AuditLog
     */
    public AttackSession processTriggerRuleFired(AlertRule rule,
                                                 String deviceName,
                                                 Double actualValue) {
        String attackType = "trigger:" + rule.getMetric() + rule.getConditionOp() + rule.getValue();

        // Existing active session for this rule on this device?
        Optional<AttackSession> existing = sessionRepo
                .findByDeviceNameAndRuleIdAndStatus(
                        deviceName, rule.getId(), SessionStatus.ACTIVE);

        AttackSession session;

        if (existing.isPresent()) {
            // Refresh existing session
            session = existing.get();
            session.setLastSeenAt(LocalDateTime.now());
            session.setPredictionCount(session.getPredictionCount() + 1);
            // Confidence is NOT set for triggers — it's only meaningful for AI
            // predictions. The metric value that fired the rule is still
            // available via metric_data lookup at session.startedAt if needed.
            return sessionRepo.save(session);
        }

        // Create new trigger session
        session = new AttackSession();
        session.setDeviceName(deviceName);
        session.setInterfaceName("-"); // triggers are device-level, not interface-level
        session.setAttackType(attackType);
        session.setStatus(SessionStatus.ACTIVE);
        session.setSessionSource(SessionSource.TRIGGER_RULE);
        session.setRuleId(rule.getId());

        // Map AlertRule severity to AttackSession severity
        Severity sev = Severity.WARNING;
        if (rule.getSeverity() == AlertRule.AlertSeverity.CRITICAL) {
            sev = Severity.CRITICAL;
        }
        session.setSeverity(sev);

        session.setStartedAt(LocalDateTime.now());
        session.setLastSeenAt(LocalDateTime.now());
        // Confidence null — this is a deterministic event (threshold crossed),
        // not a model prediction. Null in DB, "—" in UI.
        session.setAvgConfidence(null);
        session.setMaxConfidence(null);
        session.setPredictionCount(1);
        session.setAcknowledged(false);
        session.setEscalationLevel(1);

        AttackSession saved = sessionRepo.save(session);

        // SOAR: auto-execute any matching playbook with autoExecute=true for this
        // trigger. No-op if none match.
        // CRITICAL: catch Throwable (not just Exception) so a poisoned transaction
        // can't take down the trigger session we just saved. Same fix as the AI
        // detection path above. See note at the top of processAttackPrediction.
        try {
            playbookExecService.runAutoExecFor(saved);
        } catch (Throwable t) {
            System.err.println("Auto-exec playbook failed for trigger (caught & swallowed): "
                    + t.getMessage());
        }

        // WebSocket notification — same format as NEW_ATTACK but sourceType differs
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "NEW_ATTACK");  // Frontend handles it uniformly
        notification.put("source", "TRIGGER_RULE");
        notification.put("deviceName", deviceName);
        notification.put("interfaceName", "-");
        notification.put("attackType", attackType);
        notification.put("ruleName", rule.getMetric() + " " + rule.getConditionOp() + " " + rule.getValue());
        notification.put("actualValue", actualValue);
        notification.put("severity", sev.name());
        // Note: no "confidence" field sent for triggers — frontend will show "—"
        notification.put("timestamp", LocalDateTime.now().toString());
        messagingTemplate.convertAndSend("/topic/notifications", (Object) notification);

        // Audit log
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
    // ═══════════════════════════════════════════════════════════
    //  Scheduled: إغلاق sessions منتهية (كل 10 ثواني)
    // ═══════════════════════════════════════════════════════════

    /**
     * نفحص كل sessions نشطة ولم تتحدث منذ 30+ ثانية → نغلقها.
     * يعمل في الخلفية حتى لو Dashboard مغلق.
     */
    @Scheduled(fixedRate = 10000) // كل 10 ثواني
    public void closeExpiredSessions() {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(30);
        List<AttackSession> stale = sessionRepo.findStaleSessions(threshold);

        for (AttackSession session : stale) {
            // If protection was applied, mark as MITIGATED; otherwise ENDED
            if (session.getMitigatedAt() != null) {
                session.setStatus(SessionStatus.MITIGATED);
            } else {
                session.setStatus(SessionStatus.ENDED);
            }
            session.setEndedAt(LocalDateTime.now());
            sessionRepo.save(session);

            // transit ينغلق بصمت — بدون notification أو audit
            if (isTransit(session.getAttackType())) continue;

            // إرسال notification إنهاء (هجمات حقيقية + trigger rules + interface down)
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

            // تسجيل في Audit Log
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

            // NOTE: The old ProtectionService.onAttackEnded() would auto-revert
            // iptables rules when an attack ended. The new Playbook system is
            // fire-and-forget — rules stay in place until operator removes them
            // manually (via Terminal page) or runs a "cleanup" playbook.
            // This is intentional: admins often want to keep defensive rules
            // active even after the attack stops, to prevent immediate relapse.
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Escalation: ترقية مستوى الإنذار (كل 60 ثانية)
    // ═══════════════════════════════════════════════════════════

    /**
     * Escalade تلقائي:
     *   Level 1 → فوري (Administrateur réseau)
     *   Level 2 → بعد 5 دقائق بدون Acknowledge
     *   Level 3 → بعد 15 دقيقة بدون Acknowledge
     *
     * الأوقات قابلة للتعديل لاحقاً من Settings.

     @Scheduled(fixedRate = 60000) // كل دقيقة
     public void escalateUnacknowledged() {
     List<AttackSession> unacked = sessionRepo
     .findUnacknowledgedNonTransit(SessionStatus.ACTIVE);

     LocalDateTime now = LocalDateTime.now();

     for (AttackSession session : unacked) {
     // transit لا يحتاج escalation (Option B)
     if (isTransit(session.getAttackType())) continue;

     long minutesSinceStart = java.time.Duration.between(session.getStartedAt(), now).toMinutes();
     int newLevel = session.getEscalationLevel();

     if (minutesSinceStart >= 15 && session.getEscalationLevel() < 3) {
     newLevel = 3;
     } else if (minutesSinceStart >= 5 && session.getEscalationLevel() < 2) {
     newLevel = 2;
     }

     if (newLevel != session.getEscalationLevel()) {
     int oldLevel = session.getEscalationLevel();
     session.setEscalationLevel(newLevel);
     sessionRepo.save(session);

     // Notification
     Map<String, Object> notification = new HashMap<>();
     notification.put("type", "ESCALATION");
     notification.put("sessionId", session.getId());
     notification.put("oldLevel", oldLevel);
     notification.put("newLevel", newLevel);
     notification.put("deviceName", session.getDeviceName());
     notification.put("attackType", session.getAttackType());
     messagingTemplate.convertAndSend("/topic/notifications",(Object) notification);

     // Audit Log
     AuditLog log = new AuditLog();
     log.setType(AuditLog.AuditType.ESCALATION);
     log.setDescription("Alert escalated Level " + oldLevel + " → Level " + newLevel
     + ": " + session.getAttackType() + " on " + session.getDeviceName());
     log.setDeviceName(session.getDeviceName());
     auditLogRepo.save(log);
     }
     }
     }
     */

    // ═══════════════════════════════════════════════════════════
    //  API: Acknowledge
    // ═══════════════════════════════════════════════════════════

    public AttackSession acknowledge(Long sessionId) {
        AttackSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found: " + sessionId));
        session.setAcknowledged(true);
        session.setAcknowledgedAt(LocalDateTime.now());

        // Audit
        AuditLog log = new AuditLog();
        log.setType(AuditLog.AuditType.ACKNOWLEDGE);
        log.setDescription("Admin acknowledged " + session.getAttackType()
                + " alert on " + session.getDeviceName());
        log.setDeviceName(session.getDeviceName());
        auditLogRepo.save(log);

        return sessionRepo.save(session);
    }

    // ═══════════════════════════════════════════════════════════
    //  API: Queries for Dashboard
    // ═══════════════════════════════════════════════════════════

    /** Live Tab: sessions actives */
    public List<AttackSession> getActiveSessions() {
        return sessionRepo.findByStatus(SessionStatus.ACTIVE);
    }

    /** History Tab (Security page): sessions terminées uniquement.
     *  Uses overlap-aware queries so a session that ended inside the window
     *  but started before it still appears. */
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

    /** Timeline data (Home page): ACTIVE + ENDED + MITIGATED, overlap-aware.
     *  Use for Problem Timeline bars so currently-ongoing attacks also show. */
    public List<AttackSession> getTimelineSessions(String period) {
        List<SessionStatus> allStatuses = List.of(
                SessionStatus.ACTIVE, SessionStatus.ENDED, SessionStatus.MITIGATED);
        LocalDateTime after = parsePeriod(period);
        if (after == null) {
            return sessionRepo.findByStatusInOrderByStartedAtDesc(allStatuses);
        }
        return sessionRepo.findOverlappingByStatusIn(allStatuses, after);
    }

    /** Home: آخر 10 sessions */
    public List<AttackSession> getRecentSessions() {
        return sessionRepo.findTop10ByOrderByStartedAtDesc();
    }

    /** Notifications: sessions غير معترف بها (بدون transit) */
    public List<AttackSession> getUnacknowledged() {
        return sessionRepo.findUnacknowledgedNonTransit(SessionStatus.ACTIVE);
    }

    /** Stats for History cards */
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

    /** SLA: downtime per device */
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

    // ═══════════════════════════════════════════════════════════
    //  Helper
    // ═══════════════════════════════════════════════════════════

    private LocalDateTime parsePeriod(String period) {
        if (period == null) return null;
        return switch (period) {
            case "1h" -> LocalDateTime.now().minusHours(1);
            case "24h" -> LocalDateTime.now().minusHours(24);
            case "7d" -> LocalDateTime.now().minusDays(7);
            case "30d" -> LocalDateTime.now().minusDays(30);
            default -> null; // "all" → no filter
        };
    }

    // ═══════════════════════════════════════════════════════════
    //  Attack Impact — for Attack Detail Panel
    // ═══════════════════════════════════════════════════════════

    /**
     * Returns the session + metrics before, during, after the attack
     * + audit log events during the attack window.
     *
     * Used by: GET /api/attack-sessions/{id}/impact
     *
     * The frontend uses this data to draw charts showing how each metric
     * changed in reaction to the attack (CPU, half-open connections, etc.)
     */
    public Map<String, Object> getImpact(Long sessionId) {
        AttackSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            return null;
        }

        LocalDateTime startedAt = session.getStartedAt();
        // For active sessions, use "now" as the end boundary
        LocalDateTime endedAt = session.getEndedAt() != null
                ? session.getEndedAt()
                : LocalDateTime.now();

        // 2-minute windows before and after the attack
        LocalDateTime beforeStart = startedAt.minusMinutes(2);
        LocalDateTime afterEnd = endedAt.plusMinutes(2);

        // Don't look into the future
        LocalDateTime now = LocalDateTime.now();
        if (afterEnd.isAfter(now)) {
            afterEnd = now;
        }

        // Fetch all metrics in the full window [before → after] in one query
        List<MetricData> allMetrics = metricRepository
                .findByDeviceNameAndTimestampBetweenOrderByTimestampAsc(
                        session.getDeviceName(), beforeStart, afterEnd);

        // Split into 3 phases
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

        // Audit log events during the attack window (with small margin).
        // Filter down to events that are GENUINELY about this attack:
        //   - Must be for this specific device (null deviceName = global, usually Settings; skip)
        //   - Must be AI type (attacks) or DEVICE_CONTROL for this device
        //   - Must contain attack-related keywords
        // Global settings changes (e.g. "Trigger rule created") have no device scope
        // and should NOT pollute this panel.
        List<AuditLog> auditEvents = auditLogRepo.findByTimestampAfterOrderByTimestampDesc(
                beforeStart);
        final LocalDateTime windowEnd = afterEnd;
        final String targetDevice = session.getDeviceName();
        final String attackType = session.getAttackType();

        auditEvents.removeIf(e -> {
            if (e.getTimestamp() == null) return true;
            if (e.getTimestamp().isAfter(windowEnd)) return true;

            // Must be for THIS device — device-agnostic events (null) = global config, skip
            if (e.getDeviceName() == null) return true;
            if (!e.getDeviceName().equals(targetDevice)) return true;

            // Keep only attack / mitigation / device-control events for this device.
            // Trigger rule firing on a DIFFERENT rule for this device also gets filtered
            // since this panel is about ONE attack.
            AuditLog.AuditType type = e.getType();
            if (type == AuditLog.AuditType.AI) {
                // AI audit entries — keep only those mentioning this attackType
                // (avoids showing a "transit" event on an unrelated interface, etc.)
                String desc = e.getDescription() == null ? "" : e.getDescription();
                return !desc.contains(attackType);
            }
            if (type == AuditLog.AuditType.PROTECTION) {
                // Mitigation events for this device — always relevant
                return false;
            }
            if (type == AuditLog.AuditType.DEVICE_CONTROL) {
                // Power button press during attack — useful context
                return false;
            }
            // SETTINGS and other types → drop
            return true;
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("session", session);
        result.put("metricsBefore", metricsBefore);
        result.put("metricsDuring", metricsDuring);
        result.put("metricsAfter", metricsAfter);
        result.put("auditLogs", auditEvents);

        // Mitigation actions taken on this session (replaces legacy session.protectionAction).
        // Each entry: { actionName, deviceName, status, executedAt, output, ... }
        try {
            result.put("protectionActions",
                    protectionActionRepository.findByAttackSessionId(sessionId));
        } catch (Exception e) {
            result.put("protectionActions", java.util.Collections.emptyList());
        }

        // Window boundaries for frontend (ISO strings)
        Map<String, String> window = new LinkedHashMap<>();
        window.put("beforeStart", beforeStart.toString());
        window.put("attackStart", startedAt.toString());
        window.put("attackEnd", endedAt.toString());
        window.put("afterEnd", afterEnd.toString());
        result.put("window", window);

        return result;
    }
}