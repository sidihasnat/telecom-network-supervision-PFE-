package com.example.demo.Service;

import com.example.demo.model.AttackSession;
import com.example.demo.model.AuditLog;
import com.example.demo.model.MetricData;
import com.example.demo.model.PlaybookRule;
import com.example.demo.model.PlaybookRule.TriggerType;
import com.example.demo.model.ProtectionAction;
import com.example.demo.repository.MetricRepository;
import com.example.demo.repository.PlaybookRuleRepository;
import com.example.demo.repository.ProtectionActionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class PlaybookExecutionService {

    @Autowired
    private PlaybookRuleRepository playbookRepo;

    @Autowired
    private ProtectionActionRepository protActionRepo;

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private AuditLogService auditLogService;

    // For marking the session as MITIGATED + broadcasting status change to UI
    @Autowired
    private com.example.demo.repository.AttackSessionRepository sessionRepo;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SshService sshService;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ══════════════════════════════════════════════════════════════
    //  Public API
    // ══════════════════════════════════════════════════════════════

    /**
     * Run the top-priority enabled playbook matching this session.
     * Called from PlaybookController for the manual "Mitigate" button.
     *
     * Returns the playbook name, or null if nothing matched.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String runMatchingFor(AttackSession session) {
        try {
            PlaybookRule chosen = pickPlaybook(session, false);
            if (chosen == null) return null;
            executePlaybook(chosen, session, false);
            return chosen.getName();
        } catch (Throwable t) {
            // 🔬 DIAGNOSTIC LOGGING — print full stack trace so the real cause
            // is visible in docker logs. Once we know what's failing, we can
            // address the root cause and tighten this back up.
            System.err.println("════════════════════════════════════════════════════════");
            System.err.println("🔴 runMatchingFor FAILED");
            System.err.println("   session id  = " + (session != null ? session.getId() : "null"));
            System.err.println("   device      = " + (session != null ? session.getDeviceName() : "null"));
            System.err.println("   attackType  = " + (session != null ? session.getAttackType() : "null"));
            System.err.println("   throwable   = " + t.getClass().getName());
            System.err.println("   message     = " + t.getMessage());
            System.err.println("   stack trace:");
            t.printStackTrace();
            System.err.println("════════════════════════════════════════════════════════");
            return null;
        }
    }

    /**
     * Run an auto-execute playbook if one exists. Called automatically from the
     * live prediction path when a new attack is first detected.
     *
     * No-op if no playbook has autoExecute=true for this trigger.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String runAutoExecFor(AttackSession session) {
        try {
            PlaybookRule chosen = pickPlaybook(session, true);
            if (chosen == null) return null;
            executePlaybook(chosen, session, true);
            return chosen.getName();
        } catch (Throwable t) {
            // 🔬 DIAGNOSTIC LOGGING — see runMatchingFor for rationale.
            System.err.println("════════════════════════════════════════════════════════");
            System.err.println("🔴 runAutoExecFor FAILED");
            System.err.println("   session id  = " + (session != null ? session.getId() : "null"));
            System.err.println("   device      = " + (session != null ? session.getDeviceName() : "null"));
            System.err.println("   attackType  = " + (session != null ? session.getAttackType() : "null"));
            System.err.println("   throwable   = " + t.getClass().getName());
            System.err.println("   message     = " + t.getMessage());
            System.err.println("   stack trace:");
            t.printStackTrace();
            System.err.println("════════════════════════════════════════════════════════");
            return null;
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  Internals
    // ══════════════════════════════════════════════════════════════

    /**
     * Figure out trigger type + value from the session, then query the repo.
     */
    private PlaybookRule pickPlaybook(AttackSession session, boolean autoOnly) {
        TriggerType triggerType;
        String triggerValue;

        if (session.getSessionSource() == AttackSession.SessionSource.TRIGGER_RULE) {
            triggerType = TriggerType.ALERT;
            // attackType for trigger rules looks like "trigger:cpuUsage>80"
            triggerValue = session.getAttackType();
        } else {
            // AI attacks + anything else
            triggerType = TriggerType.ATTACK;
            triggerValue = session.getAttackType();
        }

        List<PlaybookRule> matches = autoOnly
                ? playbookRepo.findAutoExecMatching(triggerType, triggerValue)
                : playbookRepo.findMatching(triggerType, triggerValue);

        return matches.isEmpty() ? null : matches.get(0);
    }

    /**
     * Execute each command line in the playbook on the resolved target.
     * Records one ProtectionAction row for the whole playbook.
     *
     * Commands referencing {attackerIp} / {attackerIps} are skipped if the
     * corresponding data isn't available for this attack (e.g. SYN floods
     * usually have spoofed IPs so `topAttackersDetail` comes back empty).
     *
     * @param autoMode  true if this run came from auto-exec path, false if manual click
     */
    private void executePlaybook(PlaybookRule playbook, AttackSession session, boolean autoMode) {
        // 🔬 DIAGNOSTIC: trace every phase so we can identify exactly where it blows up.
        System.out.println("▶ executePlaybook START | playbook='" + playbook.getName()
                + "' session=" + session.getId() + " auto=" + autoMode);

        String targetDevice = resolveTarget(playbook, session);
        if (targetDevice == null) {
            System.out.println("  ⚠ resolveTarget returned null — skipping");
            auditLogService.log(AuditLog.AuditType.PROTECTION,
                    "Playbook '" + playbook.getName() + "' skipped — could not resolve target");
            return;
        }
        System.out.println("  ✓ targetDevice = " + targetDevice);

        // Fetch attacker IPs once for this playbook run. Empty list means either
        // the attack type doesn't produce visible IPs (spoofed SYN flood, UDP
        // reflection, …) or no metric has been recorded yet.
        List<String> attackerIps;
        try {
            attackerIps = getAttackerIps(session.getDeviceName());
            System.out.println("  ✓ getAttackerIps returned " + attackerIps.size() + " IPs");
        } catch (Throwable t) {
            System.err.println("  🔴 getAttackerIps FAILED: " + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            attackerIps = new ArrayList<>();
        }

        String[] lines = playbook.getCommands().split("\\r?\\n");
        StringBuilder output = new StringBuilder();
        boolean anySuccess = false;
        boolean anyFailure = false;

        for (String rawCmd : lines) {
            String cmd = rawCmd.trim();
            if (cmd.isEmpty() || cmd.startsWith("#")) continue;

            boolean needsIp  = cmd.contains("{attackerIp}");
            boolean needsIps = cmd.contains("{attackerIps}");

            // Skip command if it needs attacker IPs and we don't have any
            if ((needsIp || needsIps) && attackerIps.isEmpty()) {
                output.append("$ ").append(cmd).append("\n")
                        .append("SKIPPED: no attacker IPs available ")
                        .append("(likely spoofed / UDP / or no metric yet).\n");
                continue;
            }

            // If command needs {attackerIp} (singular) and we have multiple IPs,
            // expand it into one command per IP — so a block rule fires for each.
            List<String> expanded = new ArrayList<>();
            if (needsIp && !needsIps) {
                for (String ip : attackerIps) {
                    expanded.add(cmd.replace("{attackerIp}", ip));
                }
            } else {
                String finalCmd = cmd;
                if (needsIps) {
                    finalCmd = finalCmd.replace("{attackerIps}", String.join(" ", attackerIps));
                }
                expanded.add(finalCmd);
            }

            for (String c : expanded) {
                String finalCmd = substitutePlaceholders(c, session);
                System.out.println("  ▶ executing: " + finalCmd);
                try {
                    SshService.CommandResult res = sshService.executeCommand(targetDevice, finalCmd);
                    String result = res.getCombinedOutput();
                    output.append("$ ").append(finalCmd).append("\n")
                            .append(result).append("\n");
                    anySuccess = true;
                    System.out.println("    ✓ command success");
                } catch (Exception e) {
                    output.append("$ ").append(finalCmd).append("\n")
                            .append("ERROR: ").append(e.getMessage()).append("\n");
                    anyFailure = true;
                    System.err.println("    🔴 command failed: " + e.getMessage());
                }
            }
        }
        System.out.println("  ✓ commands done. anySuccess=" + anySuccess + " anyFailure=" + anyFailure);

        // Record as ProtectionAction
        try {
            ProtectionAction action = new ProtectionAction();
            action.setAttackSession(session);
            action.setActionName(playbook.getName());
            action.setDeviceName(targetDevice);
            action.setCommand(playbook.getCommands());
            action.setOutput(output.toString());
            action.setStatus(anyFailure ? (anySuccess ? "PARTIAL" : "FAILED") : "SUCCESS");
            action.setSuccess(!anyFailure);
            action.setTriggerMode(autoMode
                    ? ProtectionAction.TriggerMode.AUTO
                    : ProtectionAction.TriggerMode.MANUAL);
            action.setExecutedAt(LocalDateTime.now());
            protActionRepo.save(action);
            System.out.println("  ✓ ProtectionAction saved id=" + action.getId());
        } catch (Throwable t) {
            System.err.println("  🔴 protActionRepo.save FAILED: "
                    + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            throw t; // rethrow to abort — caller's try/catch will swallow
        }

        try {
            auditLogService.log(AuditLog.AuditType.PROTECTION,
                    "Playbook '" + playbook.getName() + "' executed on "
                            + targetDevice + " (" + (anyFailure ? (anySuccess ? "PARTIAL" : "FAILED") : "SUCCESS") + ")");
            System.out.println("  ✓ AuditLog written");
        } catch (Throwable t) {
            System.err.println("  🔴 auditLogService.log FAILED: "
                    + t.getClass().getName() + ": " + t.getMessage());
            t.printStackTrace();
            // Don't rethrow — audit log is non-essential
        }

        // ── Update session status ───────────────────────────────────
        // For MANUAL mitigation (operator clicked Mitigate):
        //   - Stamp mitigatedAt = now (proves the operator acted)
        //   - DO NOT change status — stays ACTIVE
        //   - DO NOT set endedAt — the attack may still be ongoing!
        //
        // Why: the attacker doesn't stop just because we clicked Mitigate.
        // Closing the session prematurely makes the next AI prediction create
        // a SECOND session for the same attack (duplicate row in history).
        //
        // The natural lifecycle takes over from here:
        //   - As long as AI keeps detecting the attack, processAttackPrediction
        //     finds the still-ACTIVE session and refreshes it (predictionCount++).
        //   - When the attack truly stops, AI stops sending predictions and the
        //     @Scheduled closeExpiredSessions() (every 10s) closes the session.
        //   - That scheduler already does the right thing:
        //       if (mitigatedAt != null) status = MITIGATED;
        //       else                     status = ENDED;
        //
        // For AUTO mitigation (auto-exec during detection): same — leave status
        // alone, just stamp mitigatedAt so the timeline shows when protection
        // kicked in.
        if (session.getStatus() == AttackSession.SessionStatus.ACTIVE
                && (anySuccess || !anyFailure)) {
            try {
                LocalDateTime now = LocalDateTime.now();
                session.setMitigatedAt(now);
                sessionRepo.save(session);
                System.out.println("  ✓ session.mitigatedAt stamped");

                if (!autoMode) {
                    try {
                        Map<String, Object> notif = new HashMap<>();
                        notif.put("type", "ATTACK_MITIGATED");
                        notif.put("sessionId", session.getId());
                        notif.put("deviceName", session.getDeviceName());
                        notif.put("attackType", session.getAttackType());
                        notif.put("playbook", playbook.getName());
                        notif.put("timestamp", now.toString());
                        messagingTemplate.convertAndSend("/topic/notifications", (Object) notif);
                        System.out.println("  ✓ ATTACK_MITIGATED broadcast");
                    } catch (Throwable t) {
                        System.err.println("  ⚠ broadcast failed (non-critical): " + t.getMessage());
                    }
                }
            } catch (Throwable t) {
                System.err.println("  🔴 session.setMitigatedAt FAILED: "
                        + t.getClass().getName() + ": " + t.getMessage());
                t.printStackTrace();
                throw t; // rethrow — caller's try/catch will swallow
            }
        }
        System.out.println("▶ executePlaybook END | playbook='" + playbook.getName() + "'");
    }


    /**
     * Resolve the target container name from the playbook's TargetType + session.
     *
     * VICTIM   → the attacked device (session.deviceName)
     * ATTACKER → runs on the VICTIM too, but the commands typically use
     *            {attackerIp} to target the source (e.g. iptables -s {attackerIp})
     * ROUTER   → upstream edge-router for network-wide blocks
     * CUSTOM   → arbitrary device named in playbook.targetDevice
     */
    private String resolveTarget(PlaybookRule playbook, AttackSession session) {
        return switch (playbook.getTargetType()) {
            case VICTIM   -> session.getDeviceName();
            case ATTACKER -> session.getDeviceName();
            case ROUTER   -> "edge-router";
            case CUSTOM   -> playbook.getTargetDevice();
        };
    }

    /**
     * Fetch the top attacker IPs observed on a device's most recent metric.
     *
     * Source: SecurityMetric.topAttackersDetail — collected by metrics.py from
     * `ss -tn` output (established TCP connections). Returns IPs sorted by
     * connection count, descending.
     *
     * Limitations (admin should know when writing playbooks):
     *   - SYN floods usually have spoofed source IPs → list often empty
     *   - UDP / ICMP floods → list empty (ss shows TCP only)
     *   - Legitimate high-traffic clients may appear here too — an admin
     *     writing an auto-block playbook should add their own whitelist.
     *
     * For port scans, HTTP floods, SSH brute force → reliable.
     */
    private List<String> getAttackerIps(String deviceName) {
        try {
            List<MetricData> recent = metricRepository
                    .findTop20ByDeviceNameOrderByTimestampDesc(deviceName);
            if (recent == null || recent.isEmpty()) return Collections.emptyList();

            MetricData latest = recent.get(0);
            if (latest.getSecurityMetric() == null) return Collections.emptyList();

            String json = latest.getSecurityMetric().getTopAttackersDetail();
            if (json == null || json.isBlank() || json.equals("{}")) {
                return Collections.emptyList();
            }

            // JSON shape: { "1.2.3.4": {"80": 10, "443": 5}, "5.6.7.8": {...} }
            // We only need the keys (the IPs), ordered as returned (already sorted
            // by total count desc in metrics.py).
            Map<String, Object> parsed = JSON.readValue(json, Map.class);
            List<String> ips = new ArrayList<>();
            for (String ip : parsed.keySet()) {
                // Skip loopback / link-local / obviously non-attacker entries
                if (ip == null || ip.isBlank()) continue;
                if (ip.equals("127.0.0.1") || ip.equals("::1") || ip.equals("*")) continue;
                ips.add(ip);
            }
            return ips;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    /**
     * Substitute placeholders that apply to EVERY command.
     * {attackerIp} and {attackerIps} are handled separately in executePlaybook
     * so we can skip whole commands if IPs aren't available.
     *
     * Supported here:
     *   {device}    → session.deviceName (the attacked device)
     *   {interface} → session.interfaceName (for interface_down sessions)
     */
    private String substitutePlaceholders(String cmd, AttackSession session) {
        String result = cmd;
        result = result.replace("{device}",
                session.getDeviceName() == null ? "" : session.getDeviceName());
        result = result.replace("{interface}",
                session.getInterfaceName() == null ? "" : session.getInterfaceName());
        return result;
    }



}