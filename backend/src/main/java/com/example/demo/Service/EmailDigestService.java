package com.example.demo.Service;

import com.example.demo.model.AttackSession;
import com.example.demo.model.AttackSession.SessionSource;
import com.example.demo.model.AttackSession.SessionStatus;
import com.example.demo.model.AuditLog;
import com.example.demo.model.DeviceStatusLog;
import com.example.demo.model.EmailConfig;
import com.example.demo.model.MetricData;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.DeviceStatusLogRepository;
import com.example.demo.repository.EmailConfigRepository;
import com.example.demo.repository.MetricRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class EmailDigestService {

    private static final DateTimeFormatter DT_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter HM_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private static final Duration INSTANT_COOLDOWN = Duration.ofMinutes(5);

    private static final Duration DIGEST_COOLDOWN = Duration.ofHours(23);

    @Autowired
    private EmailConfigRepository configRepo;

    @Autowired
    private AttackSessionRepository attackRepo;

    @Autowired
    private DeviceStatusLogRepository deviceLogRepo;

    @Autowired
    private MetricRepository metricRepo;

    @Autowired
    private AuditLogService auditLogService;

    private static final List<String> ALL_DEVICES = List.of(
            "edge-router", "core-router",
            "web-server", "dns-server",
            "ftp-server", "db-server",
            "supervision-app"
    );

    @Scheduled(fixedRate = 60_000)
    public void checkAndSendDigest() {
        try {
            EmailConfig cfg = configRepo.findById(1L).orElse(null);
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;
            if (cfg.getRecipient() == null || cfg.getRecipient().isBlank()) return;
            if (cfg.getDigestTime() == null) return;

            LocalDateTime now = LocalDateTime.now();

            if (cfg.getLastDigestSentAt() != null) {
                Duration sinceLast = Duration.between(cfg.getLastDigestSentAt(), now);
                if (sinceLast.compareTo(DIGEST_COOLDOWN) < 0) return;
            }

            LocalTime targetTime;
            try {
                targetTime = LocalTime.parse(cfg.getDigestTime(), HM_FMT);
            } catch (Exception e) {
                return;
            }
            if (now.toLocalTime().getHour() != targetTime.getHour()) return;
            if (now.toLocalTime().getMinute() != targetTime.getMinute()) return;

            EmailConfig.DigestFrequency freq = cfg.getFrequency();
            if (freq == null) freq = EmailConfig.DigestFrequency.DAILY;

            switch (freq) {
                case DAILY:

                    break;
                case EVERY_2_DAYS:
                    if (cfg.getLastDigestSentAt() != null) {
                        long hours = Duration.between(cfg.getLastDigestSentAt(), now).toHours();
                        if (hours < 47) return;
                    }
                    break;
                case WEEKLY:
                    if (cfg.getWeeklyDay() == null) return;
                    if (now.getDayOfWeek() != cfg.getWeeklyDay()) return;
                    break;
            }

            sendDigestNow(cfg, now, periodHoursFor(freq));

        } catch (Exception e) {
            try {
                auditLogService.log(AuditLog.AuditType.EMAIL,
                        "Digest scheduler error: " + safeMsg(e));
            } catch (Exception ignored) {}
        }
    }

    private long periodHoursFor(EmailConfig.DigestFrequency freq) {
        return switch (freq) {
            case EVERY_2_DAYS -> 48L;
            case WEEKLY -> 24L * 7L;
            default -> 24L;
        };
    }

    private void sendDigestNow(EmailConfig cfg, LocalDateTime now, long periodHours) {
        LocalDateTime since = now.minusHours(periodHours);
        String html = buildDigestHtml(cfg, since, now);
        String plain = buildDigestPlain(cfg, since, now);

        String subject = buildDigestSubject(cfg, periodHours);

        try {
            sendEmail(cfg, subject, plain, html);
            cfg.setLastDigestSentAt(now);
            configRepo.save(cfg);
            auditLogService.log(AuditLog.AuditType.EMAIL,
                    "Digest sent to " + cfg.getRecipient() + " (period=" + periodHours + "h)");
        } catch (Exception e) {
            try {
                auditLogService.log(AuditLog.AuditType.EMAIL,
                        "Digest send FAILED: " + safeMsg(e));
            } catch (Exception ignored) {}
        }
    }

    public String sendTestDigest() throws MessagingException {
        EmailConfig cfg = configRepo.findById(1L).orElseThrow(
                () -> new IllegalStateException("Email configuration not found"));

        validateConfigForSending(cfg);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime since = now.minusHours(24);

        String html = buildDigestHtml(cfg, since, now);
        String plain = buildDigestPlain(cfg, since, now);
        String subject = "[TEST] " + buildDigestSubject(cfg, 24);

        sendEmail(cfg, subject, plain, html);

        try {
            auditLogService.log(AuditLog.AuditType.EMAIL,
                    "Test digest sent to " + cfg.getRecipient());
        } catch (Exception ignored) {}

        return "Test email sent successfully to " + cfg.getRecipient();
    }

    public void sendInstantAiAttack(AttackSession session) {
        try {
            EmailConfig cfg = configRepo.findById(1L).orElse(null);
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;
            if (!Boolean.TRUE.equals(cfg.getInstantOnAiAttack())) return;
            if (session == null) return;

            if ("transit".equalsIgnoreCase(session.getAttackType())) return;
            if (!withinCooldown(cfg)) return;

            String subject = "attack detected: "
                    + session.getAttackType() + " on " + session.getDeviceName();

            String html = buildInstantHtml(
                    "AI Attack Detected",
                    "#dc2626",
                    Map.of(
                            "Device",       safe(session.getDeviceName()),
                            "Interface",    safe(session.getInterfaceName()),
                            "Attack type",  safe(session.getAttackType()),
                            "Severity",     session.getSeverity() != null ? session.getSeverity().name() : "CRITICAL",
                            "Confidence",   session.getMaxConfidence() != null
                                    ? String.format(Locale.ROOT, "%.1f%%", session.getMaxConfidence() * 100.0) : "—",
                            "Detected at",  session.getStartedAt() != null ? session.getStartedAt().format(DT_FMT) : "—"
                    )
            );

            String plain = "AI attack: " + session.getAttackType()
                    + " on " + session.getDeviceName() + "::" + session.getInterfaceName()
                    + " at " + (session.getStartedAt() != null ? session.getStartedAt().format(DT_FMT) : "now");

            sendAndLog(cfg, subject, plain, html, "AI attack alert: " + session.getAttackType());
        } catch (Exception e) {
            tryLog("Instant AI alert failed: " + safeMsg(e));
        }
    }

    public void sendInstantDeviceOffline(String deviceName) {
        try {
            EmailConfig cfg = configRepo.findById(1L).orElse(null);
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;
            if (!Boolean.TRUE.equals(cfg.getInstantOnDeviceOffline())) return;
            if (deviceName == null || deviceName.isBlank()) return;
            if (!withinCooldown(cfg)) return;

            String subject = "Device offline: " + deviceName;

            String html = buildInstantHtml(
                    "Device Went Offline",
                    "#6b7280",
                    Map.of(
                            "Device",      deviceName,
                            "Status",      "OFFLINE",
                            "Detected at", LocalDateTime.now().format(DT_FMT),
                            "Note",        "No metrics received for 40+ seconds. The device may have crashed, lost network, or been powered off externally."
                    )
            );

            String plain = "Device offline: " + deviceName + " at " + LocalDateTime.now().format(DT_FMT);

            sendAndLog(cfg, subject, plain, html, "Device offline alert: " + deviceName);
        } catch (Exception e) {
            tryLog("Instant device-offline alert failed: " + safeMsg(e));
        }
    }

    public void sendInstantTriggerRule(AttackSession session, Double actualValue) {
        try {
            EmailConfig cfg = configRepo.findById(1L).orElse(null);
            if (cfg == null || !Boolean.TRUE.equals(cfg.getEnabled())) return;
            if (!Boolean.TRUE.equals(cfg.getInstantOnTriggerRule())) return;
            if (session == null) return;
            if (!withinCooldown(cfg)) return;

            String subject = "Trigger rule fired: "
                    + session.getAttackType() + " on " + session.getDeviceName();

            String severityColor = session.getSeverity() == AttackSession.Severity.CRITICAL
                    ? "#dc2626" : "#eab308";

            String html = buildInstantHtml(
                    "Trigger Rule Fired",
                    severityColor,
                    Map.of(
                            "Device",       safe(session.getDeviceName()),
                            "Rule",         safe(session.getAttackType()),
                            "Severity",     session.getSeverity() != null ? session.getSeverity().name() : "WARNING",
                            "Actual value", actualValue != null ? String.format(Locale.ROOT, "%.2f", actualValue) : "—",
                            "Triggered at", session.getStartedAt() != null ? session.getStartedAt().format(DT_FMT) : "—"
                    )
            );

            String plain = "Trigger fired: " + session.getAttackType()
                    + " on " + session.getDeviceName()
                    + " (actual=" + actualValue + ")";

            sendAndLog(cfg, subject, plain, html, "Trigger alert: " + session.getAttackType());
        } catch (Exception e) {
            tryLog("Instant trigger alert failed: " + safeMsg(e));
        }
    }

    private boolean withinCooldown(EmailConfig cfg) {
        LocalDateTime last = cfg.getLastInstantSentAt();
        if (last == null) return true;
        Duration since = Duration.between(last, LocalDateTime.now());
        return since.compareTo(INSTANT_COOLDOWN) >= 0;
    }

    private void sendAndLog(EmailConfig cfg, String subject, String plain, String html, String auditMsg) {
        try {
            sendEmail(cfg, subject, plain, html);
            cfg.setLastInstantSentAt(LocalDateTime.now());
            configRepo.save(cfg);
            auditLogService.log(AuditLog.AuditType.EMAIL, auditMsg + " → sent to " + cfg.getRecipient());
        } catch (Exception e) {
            tryLog(auditMsg + " → SMTP failed: " + safeMsg(e));
        }
    }

    private void sendEmail(EmailConfig cfg, String subject, String plainText, String htmlText)
            throws MessagingException {
        validateConfigForSending(cfg);

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(cfg.getSmtpHost());
        sender.setPort(cfg.getSmtpPort() != null ? cfg.getSmtpPort() : 587);
        sender.setUsername(cfg.getSmtpUser());
        sender.setPassword(cfg.getSmtpPassword());
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");

        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, true, StandardCharsets.UTF_8.name());
        helper.setTo(cfg.getRecipient());

        String fromName = (cfg.getSenderName() == null || cfg.getSenderName().isBlank())
                ? "TelecomAI" : cfg.getSenderName();
        try {
            helper.setFrom(new InternetAddress(cfg.getSenderEmail(), fromName, "UTF-8"));
        } catch (Exception e) {
            helper.setFrom(cfg.getSenderEmail());
        }
        helper.setSubject(subject);
        helper.setText(plainText, htmlText);

        sender.send(msg);
    }

    private void validateConfigForSending(EmailConfig cfg) {
        if (cfg.getSmtpHost() == null || cfg.getSmtpHost().isBlank())
            throw new IllegalStateException("SMTP host is missing");
        if (cfg.getSmtpUser() == null || cfg.getSmtpUser().isBlank())
            throw new IllegalStateException("SMTP user is missing");
        if (cfg.getSmtpPassword() == null || cfg.getSmtpPassword().isBlank())
            throw new IllegalStateException("SMTP password is missing — set it in Settings");
        if (cfg.getSenderEmail() == null || cfg.getSenderEmail().isBlank())
            throw new IllegalStateException("Sender email is missing");
        if (cfg.getRecipient() == null || cfg.getRecipient().isBlank())
            throw new IllegalStateException("Recipient is missing");
    }

    private String buildDigestSubject(EmailConfig cfg, long periodHours) {
        String label = (periodHours == 24) ? "Daily" :
                (periodHours == 48) ? "2-Day" :
                (periodHours == 168) ? "Weekly" : "Periodic";
        return "" + label + " Security Digest — " + LocalDate.now();
    }

    private String buildDigestHtml(EmailConfig cfg, LocalDateTime since, LocalDateTime now) {
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html><head>")
          .append("<meta charset='UTF-8'>")
          .append("<title>TelecomAI Digest</title>")
          .append("</head>")
          .append("<body style='margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#1f2937;'>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0'><tr><td align='center'>")
          .append("<table role='presentation' width='640' cellpadding='0' cellspacing='0' ")
          .append("style='max-width:640px;background:#ffffff;margin:24px;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;'>");

        sb.append("<tr><td style='background:#0f172a;color:#fff;padding:20px 24px;'>")
          .append("<div style='font-size:20px;font-weight:bold;'>Security Digest</div>")
          .append("<div style='font-size:13px;color:#94a3b8;margin-top:4px;'>Period: ")
          .append(since.format(DT_FMT)).append(" → ").append(now.format(DT_FMT))
          .append("</div></td></tr>");

        DigestData data = collectDigestData(since, now);

        sb.append("<tr><td style='padding:24px;'>");

        if (Boolean.TRUE.equals(cfg.getSectionExecutiveSummary())) {
            appendExecutiveSummary(sb, data);
        }
        if (Boolean.TRUE.equals(cfg.getSectionResourceUsage())) {
            appendResourceUsage(sb, data);
        }
        if (Boolean.TRUE.equals(cfg.getSectionSlaReport())) {
            appendSlaReport(sb, data);
        }
        if (Boolean.TRUE.equals(cfg.getSectionLatestEvents())) {
            appendLatestEvents(sb, data);
        }
        if (Boolean.TRUE.equals(cfg.getSectionMitigations())) {
            appendMitigations(sb, data);
        }

        sb.append("</td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    private String buildDigestPlain(EmailConfig cfg, LocalDateTime since, LocalDateTime now) {
        DigestData data = collectDigestData(since, now);
        StringBuilder sb = new StringBuilder();
        sb.append("Period: ").append(since.format(DT_FMT)).append(" -> ").append(now.format(DT_FMT)).append("\n\n");

        sb.append("EXECUTIVE SUMMARY\n");
        sb.append("  Total incidents:        ").append(data.totalIncidents).append("\n");
        sb.append("  AI attacks:             ").append(data.aiAttacks).append("\n");
        sb.append("  Trigger alerts:         ").append(data.triggerAlerts).append("\n");
        sb.append("  Device offline events:  ").append(data.offlineEvents).append("\n");
        sb.append(String.format(Locale.ROOT, "  Network availability:   %.2f%%%n%n", data.availability));

        sb.append("RESOURCE USAGE (avg / max)\n");
        for (ResourceRow r : data.resourceRows) {
            sb.append(String.format(Locale.ROOT,
                    "  %-15s  CPU avg=%.1f%%  max=%.1f%%  Mem avg=%.1f%%%n",
                    r.device, r.avgCpu, r.maxCpu, r.avgMem));
        }
        sb.append("\n");

        sb.append("SLA\n");
        for (SlaRow s : data.slaRows) {
            sb.append(String.format(Locale.ROOT,
                    "  %-15s  uptime=%.2f%%  downtime=%s%n",
                    s.device, s.uptime, formatDuration(s.downtimeSeconds)));
        }
        sb.append("\n");

        sb.append("LATEST EVENTS\n");
        for (EventRow e : data.events) {
            sb.append("  ").append(e.time).append("  ").append(e.device)
              .append("  ").append(e.source).append("  ").append(e.attackType).append("\n");
        }
        return sb.toString();
    }

    private void appendExecutiveSummary(StringBuilder sb, DigestData d) {
        sb.append("<h2 style='font-size:14px;color:#0f172a;margin:0 0 12px;'>🚨 Executive Summary</h2>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' ")
          .append("style='border-collapse:collapse;border:1px solid #e5e7eb;border-radius:6px;margin-bottom:24px;'>");

        kvRow(sb, "Total incidents", String.valueOf(d.totalIncidents), "#0f172a");
        kvRow(sb, "AI-detected attacks", String.valueOf(d.aiAttacks), d.aiAttacks > 0 ? "#dc2626" : "#0f172a");
        kvRow(sb, "Trigger alerts", String.valueOf(d.triggerAlerts), d.triggerAlerts > 0 ? "#eab308" : "#0f172a");
        kvRow(sb, "Device offline events", String.valueOf(d.offlineEvents), d.offlineEvents > 0 ? "#6b7280" : "#0f172a");
        kvRow(sb, "Network availability",
                String.format(Locale.ROOT, "%.2f%%", d.availability),
                d.availability >= 99.0 ? "#16a34a" : (d.availability >= 95.0 ? "#eab308" : "#dc2626"));

        sb.append("</table>");
    }

    private void kvRow(StringBuilder sb, String label, String value, String valueColor) {
        sb.append("<tr>")
          .append("<td style='padding:10px 14px;border-bottom:1px solid #e5e7eb;color:#374151;font-size:13px;'>").append(label).append("</td>")
          .append("<td style='padding:10px 14px;border-bottom:1px solid #e5e7eb;color:").append(valueColor).append(";font-size:13px;font-weight:bold;text-align:right;'>")
          .append(value).append("</td>")
          .append("</tr>");
    }

    private void appendResourceUsage(StringBuilder sb, DigestData d) {
        sb.append("<h2 style='font-size:14px;color:#0f172a;margin:0 0 12px;'>📊 Resource Usage</h2>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' ")
          .append("style='border-collapse:collapse;border:1px solid #e5e7eb;margin-bottom:24px;font-size:13px;'>")
          .append("<tr style='background:#f9fafb;'>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Device</th>")
          .append("<th align='right' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Avg CPU</th>")
          .append("<th align='right' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Max CPU</th>")
          .append("<th align='right' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Avg Mem</th>")
          .append("</tr>");

        for (ResourceRow r : d.resourceRows) {
            String maxColor = r.maxCpu > 80 ? "#dc2626" : (r.maxCpu > 60 ? "#eab308" : "#374151");
            sb.append("<tr>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>").append(r.device).append("</td>")
              .append("<td align='right' style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>").append(fmtPct(r.avgCpu)).append("</td>")
              .append("<td align='right' style='padding:8px 12px;border-bottom:1px solid #f3f4f6;color:").append(maxColor).append(";font-weight:bold;'>")
              .append(fmtPct(r.maxCpu)).append("</td>")
              .append("<td align='right' style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>").append(fmtPct(r.avgMem)).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendSlaReport(StringBuilder sb, DigestData d) {
        sb.append("<h2 style='font-size:14px;color:#0f172a;margin:0 0 12px;'>⚙️ SLA Report</h2>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' ")
          .append("style='border-collapse:collapse;border:1px solid #e5e7eb;margin-bottom:24px;font-size:13px;'>")
          .append("<tr style='background:#f9fafb;'>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Device</th>")
          .append("<th align='right' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Uptime %</th>")
          .append("<th align='right' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Downtime</th>")
          .append("</tr>");

        for (SlaRow s : d.slaRows) {
            String upColor = s.uptime >= 99 ? "#16a34a" : (s.uptime >= 95 ? "#eab308" : "#dc2626");
            sb.append("<tr>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>").append(s.device).append("</td>")
              .append("<td align='right' style='padding:8px 12px;border-bottom:1px solid #f3f4f6;color:").append(upColor).append(";font-weight:bold;'>")
              .append(String.format(Locale.ROOT, "%.2f%%", s.uptime)).append("</td>")
              .append("<td align='right' style='padding:8px 12px;border-bottom:1px solid #f3f4f6;color:#6b7280;'>")
              .append(formatDuration(s.downtimeSeconds)).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendLatestEvents(StringBuilder sb, DigestData d) {
        sb.append("<h2 style='font-size:14px;color:#0f172a;margin:0 0 12px;'>📋 Latest Events</h2>");
        if (d.events.isEmpty()) {
            sb.append("<p style='color:#6b7280;font-size:13px;margin:0 0 24px;'>No events in this period — clean 🎉</p>");
            return;
        }

        sb.append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' ")
          .append("style='border-collapse:collapse;border:1px solid #e5e7eb;margin-bottom:24px;font-size:13px;'>")
          .append("<tr style='background:#f9fafb;'>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Time</th>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Device</th>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Source</th>")
          .append("<th align='left' style='padding:8px 12px;border-bottom:1px solid #e5e7eb;'>Type</th>")
          .append("</tr>");

        for (EventRow e : d.events) {
            sb.append("<tr>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;color:#6b7280;'>").append(e.time).append("</td>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>").append(e.device).append("</td>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;'>")
              .append("<span style='padding:2px 8px;border-radius:4px;font-size:11px;font-weight:bold;background:")
              .append(e.sourceColor).append(";color:#fff;'>").append(e.source).append("</span></td>")
              .append("<td style='padding:8px 12px;border-bottom:1px solid #f3f4f6;color:#374151;'>").append(e.attackType).append("</td>")
              .append("</tr>");
        }
        sb.append("</table>");
    }

    private void appendMitigations(StringBuilder sb, DigestData d) {
        sb.append("<h2 style='font-size:14px;color:#0f172a;margin:0 0 12px;'>🛡️ Auto-Mitigations Executed</h2>");
        if (d.mitigatedSessions.isEmpty()) {
            sb.append("<p style='color:#6b7280;font-size:13px;margin:0 0 24px;'>No automatic mitigations were executed in this period.</p>");
            return;
        }
        sb.append("<ul style='margin:0 0 24px;padding-left:20px;font-size:13px;color:#374151;'>");
        for (AttackSession s : d.mitigatedSessions) {
            sb.append("<li style='margin-bottom:6px;'>")
              .append(s.getMitigatedAt() != null ? s.getMitigatedAt().format(DT_FMT) : "—")
              .append(" — ")
              .append(safe(s.getProtectionAction()))
              .append(" on <b>").append(safe(s.getDeviceName())).append("</b>")
              .append("</li>");
        }
        sb.append("</ul>");
    }

    private String buildInstantHtml(String title, String accentColor, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset='UTF-8'></head>")
          .append("<body style='margin:0;padding:0;background:#f3f4f6;font-family:Arial,Helvetica,sans-serif;color:#1f2937;'>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0'><tr><td align='center'>")
          .append("<table role='presentation' width='560' cellpadding='0' cellspacing='0' ")
          .append("style='max-width:560px;background:#ffffff;margin:24px;border-radius:8px;overflow:hidden;border:1px solid #e5e7eb;'>");

        sb.append("<tr><td style='background:").append(accentColor)
          .append(";color:#ffffff;padding:18px 24px;font-size:18px;font-weight:bold;'>")
          .append(title).append("</td></tr>");

        sb.append("<tr><td style='padding:20px 24px;'>")
          .append("<table role='presentation' width='100%' cellpadding='0' cellspacing='0' style='font-size:13px;'>");

        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append("<tr>")
              .append("<td style='padding:8px 0;color:#6b7280;width:130px;vertical-align:top;'>").append(e.getKey()).append("</td>")
              .append("<td style='padding:8px 0;color:#1f2937;font-weight:500;'>").append(safe(e.getValue())).append("</td>")
              .append("</tr>");
        }

        sb.append("</table></td></tr>");

        sb.append("</table></td></tr></table></body></html>");
        return sb.toString();
    }

    private DigestData collectDigestData(LocalDateTime since, LocalDateTime now) {
        DigestData d = new DigestData();

        List<AttackSession> sessions;
        try {
            sessions = attackRepo.findOverlappingByStatusIn(
                    List.of(SessionStatus.ACTIVE, SessionStatus.ENDED, SessionStatus.MITIGATED),
                    since
            );
        } catch (Exception e) {
            sessions = new ArrayList<>();
        }

        for (AttackSession s : sessions) {
            if (s == null) continue;
            if ("transit".equalsIgnoreCase(s.getAttackType())) continue;

            SessionSource src = s.getSessionSource();
            if (src == null) src = SessionSource.AI;
            switch (src) {
                case AI -> d.aiAttacks++;
                case TRIGGER_RULE -> d.triggerAlerts++;
                case INTERFACE_DOWN -> {  }
                case DEVICE_OFFLINE -> d.offlineEvents++;
            }
        }
        d.totalIncidents = d.aiAttacks + d.triggerAlerts + d.offlineEvents;

        List<AttackSession> sortedDesc = new ArrayList<>(sessions);
        sortedDesc.removeIf(s -> "transit".equalsIgnoreCase(s.getAttackType()));
        sortedDesc.sort(Comparator.comparing(
                AttackSession::getStartedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));
        int limit = Math.min(10, sortedDesc.size());
        for (int i = 0; i < limit; i++) {
            AttackSession s = sortedDesc.get(i);
            EventRow row = new EventRow();
            row.time = s.getStartedAt() != null ? s.getStartedAt().format(HM_FMT) : "—";
            row.device = safe(s.getDeviceName());
            SessionSource src = s.getSessionSource() == null ? SessionSource.AI : s.getSessionSource();
            switch (src) {
                case AI -> { row.source = "AI"; row.sourceColor = "#dc2626"; }
                case TRIGGER_RULE -> { row.source = "Trigger"; row.sourceColor = "#eab308"; }
                case INTERFACE_DOWN -> { row.source = "Interface"; row.sourceColor = "#6b7280"; }
                case DEVICE_OFFLINE -> { row.source = "Offline"; row.sourceColor = "#6b7280"; }
            }
            row.attackType = safe(s.getAttackType());
            d.events.add(row);
        }

        for (AttackSession s : sessions) {
            if (s.getProtectionAction() != null && !s.getProtectionAction().isBlank()) {
                d.mitigatedSessions.add(s);
            }
        }
        d.mitigatedSessions.sort(Comparator.comparing(
                AttackSession::getMitigatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())
        ));

        for (String device : ALL_DEVICES) {
            ResourceRow r = new ResourceRow();
            r.device = device;
            try {
                List<MetricData> rows = metricRepo
                        .findByDeviceNameAndTimestampBetweenOrderByTimestampAsc(device, since, now);
                double sumCpu = 0, sumMem = 0;
                double maxCpu = 0;
                int countCpu = 0, countMem = 0;
                for (MetricData m : rows) {
                    if (m.getCpuUsage() != null) {
                        sumCpu += m.getCpuUsage();
                        if (m.getCpuUsage() > maxCpu) maxCpu = m.getCpuUsage();
                        countCpu++;
                    }
                    if (m.getMemoryUsage() != null) {
                        sumMem += m.getMemoryUsage();
                        countMem++;
                    }
                }
                r.avgCpu = countCpu > 0 ? sumCpu / countCpu : 0;
                r.maxCpu = maxCpu;
                r.avgMem = countMem > 0 ? sumMem / countMem : 0;
            } catch (Exception e) {

            }
            d.resourceRows.add(r);
        }

        long periodSeconds = Duration.between(since, now).getSeconds();
        if (periodSeconds <= 0) periodSeconds = 1;

        Map<String, Long> offlineByDevice = new HashMap<>();
        try {
            List<DeviceStatusLog> logs = deviceLogRepo.findOverlappingWindow(since);
            for (DeviceStatusLog log : logs) {
                if (log.getStatus() != DeviceStatusLog.Status.OFFLINE) continue;
                LocalDateTime s = log.getStartedAt() != null && log.getStartedAt().isAfter(since)
                        ? log.getStartedAt() : since;
                LocalDateTime e = log.getEndedAt() != null ? log.getEndedAt() : now;
                if (e.isAfter(now)) e = now;
                long secs = Duration.between(s, e).getSeconds();
                if (secs > 0) {
                    offlineByDevice.merge(log.getDeviceName(), secs, Long::sum);
                }
            }
        } catch (Exception ignored) {}

        long totalUp = 0;
        long totalDown = 0;
        for (String device : ALL_DEVICES) {
            SlaRow s = new SlaRow();
            s.device = device;
            long down = offlineByDevice.getOrDefault(device, 0L);
            if (down > periodSeconds) down = periodSeconds;
            s.downtimeSeconds = down;
            long up = periodSeconds - down;
            s.uptime = (up * 100.0) / periodSeconds;
            d.slaRows.add(s);
            totalUp += up;
            totalDown += down;
        }
        long totalPeriod = totalUp + totalDown;
        d.availability = totalPeriod > 0 ? (totalUp * 100.0) / totalPeriod : 100.0;

        return d;
    }

    private static String fmtPct(double v) {
        return String.format(Locale.ROOT, "%.1f%%", v);
    }

    private static String formatDuration(long seconds) {
        if (seconds <= 0) return "0m";
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }

    private static String safeMsg(Exception e) {
        if (e == null) return "unknown";

        String m = e.getMessage();
        if (m == null) return e.getClass().getSimpleName();
        return m.replaceAll("(?i)password[^\\s,;]*", "password=****");
    }

    private void tryLog(String msg) {
        try {
            auditLogService.log(AuditLog.AuditType.EMAIL, msg);
        } catch (Exception ignored) {

            System.err.println("[EmailDigestService] " + msg);
        }
    }

    private static class DigestData {
        int totalIncidents;
        int aiAttacks;
        int triggerAlerts;
        int offlineEvents;
        double availability = 100.0;
        List<ResourceRow> resourceRows = new ArrayList<>();
        List<SlaRow> slaRows = new ArrayList<>();
        List<EventRow> events = new ArrayList<>();
        List<AttackSession> mitigatedSessions = new ArrayList<>();
    }

    private static class ResourceRow {
        String device;
        double avgCpu;
        double maxCpu;
        double avgMem;
    }

    private static class SlaRow {
        String device;
        double uptime;
        long downtimeSeconds;
    }

    private static class EventRow {
        String time;
        String device;
        String source;
        String sourceColor;
        String attackType;
    }
}
