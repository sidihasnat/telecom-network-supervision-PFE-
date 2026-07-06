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

    @Autowired
    private com.example.demo.repository.AttackSessionRepository sessionRepo;

    @Autowired
    private org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    @Autowired
    private SshService sshService;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String runMatchingFor(AttackSession session) {
        try {
            PlaybookRule chosen = pickPlaybook(session, false);
            if (chosen == null) return null;
            executePlaybook(chosen, session, false);
            return chosen.getName();
        } catch (Throwable t) {

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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String runAutoExecFor(AttackSession session) {
        try {
            PlaybookRule chosen = pickPlaybook(session, true);
            if (chosen == null) return null;
            executePlaybook(chosen, session, true);
            return chosen.getName();
        } catch (Throwable t) {

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

    private PlaybookRule pickPlaybook(AttackSession session, boolean autoOnly) {
        TriggerType triggerType;
        String triggerValue;

        if (session.getSessionSource() == AttackSession.SessionSource.TRIGGER_RULE) {
            triggerType = TriggerType.ALERT;

            triggerValue = session.getAttackType();
        } else {

            triggerType = TriggerType.ATTACK;
            triggerValue = session.getAttackType();
        }

        List<PlaybookRule> matches = autoOnly
                ? playbookRepo.findAutoExecMatching(triggerType, triggerValue)
                : playbookRepo.findMatching(triggerType, triggerValue);

        return matches.isEmpty() ? null : matches.get(0);
    }

    private void executePlaybook(PlaybookRule playbook, AttackSession session, boolean autoMode) {

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

        List<String> attackerIps;
        try {
            attackerIps = getAttackerIps(session);
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

            if ((needsIp || needsIps) && attackerIps.isEmpty()) {
                output.append("$ ").append(cmd).append("\n")
                        .append("SKIPPED: no attacker IPs available ")
                        .append("(likely spoofed / UDP / or no metric yet).\n");
                continue;
            }

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
            throw t;
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

        }

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
                throw t;
            }
        }
        System.out.println("▶ executePlaybook END | playbook='" + playbook.getName() + "'");
    }

    private String resolveTarget(PlaybookRule playbook, AttackSession session) {
        return switch (playbook.getTargetType()) {
            case VICTIM   -> session.getDeviceName();
            case ATTACKER -> session.getDeviceName();
            case ROUTER   -> "edge-router";
            case CUSTOM   -> playbook.getTargetDevice();
        };
    }

    private List<String> getAttackerIps(AttackSession session) {
        if (session.getAttackerIps() == null || session.getAttackerIps().isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.asList(session.getAttackerIps().split(","));
    }

    private String substitutePlaceholders(String cmd, AttackSession session) {
        String result = cmd;
        result = result.replace("{device}",
                session.getDeviceName() == null ? "" : session.getDeviceName());
        result = result.replace("{interface}",
                session.getInterfaceName() == null ? "" : session.getInterfaceName());
        return result;
    }

}
