package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.Service.EmailDigestService;
import com.example.demo.model.AuditLog;
import com.example.demo.model.EmailConfig;
import com.example.demo.repository.EmailConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * EmailConfigController — REST endpoints for email notification settings.
 *
 *   GET  /api/email-config            — load current config (any authenticated user)
 *   PUT  /api/email-config            — update config (ADMIN only)
 *   POST /api/email-config/test       — send test digest now (ADMIN only)
 *
 * Password masking:
 *   GET responses always return password as "***" so the secret is never
 *   exposed to the frontend after the first save. PUT requests carrying the
 *   sentinel "***" leave the stored password unchanged.
 */
@RestController
@RequestMapping("/api/email-config")
public class EmailConfigController {

    /** Sentinel value used in GET responses and accepted as "no change" on PUT. */
    private static final String PASSWORD_MASK = "***";

    @Autowired
    private EmailConfigRepository configRepo;

    @Autowired
    private EmailDigestService digestService;

    @Autowired
    private AuditLogService auditLogService;

    // ══════════════════════════════════════════════════════════════
    //  GET — load config (any authenticated user can read settings,
    //        but the password is masked)
    // ══════════════════════════════════════════════════════════════
    @GetMapping
    public EmailConfig get() {
        EmailConfig cfg = configRepo.findById(1L).orElseGet(() -> {
            // First run — create a default disabled row so the UI has something to render
            EmailConfig fresh = new EmailConfig();
            fresh.setId(1L);
            return configRepo.save(fresh);
        });
        // Mask password before returning
        if (cfg.getSmtpPassword() != null && !cfg.getSmtpPassword().isEmpty()) {
            cfg.setSmtpPassword(PASSWORD_MASK);
        }
        return cfg;
    }

    // ══════════════════════════════════════════════════════════════
    //  PUT — update config (ADMIN only)
    // ══════════════════════════════════════════════════════════════
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailConfig> update(@RequestBody EmailConfig incoming) {
        EmailConfig existing = configRepo.findById(1L).orElseGet(() -> {
            EmailConfig fresh = new EmailConfig();
            fresh.setId(1L);
            return fresh;
        });

        // ── Copy fields from incoming → existing ─────────────────
        existing.setEnabled(nz(incoming.getEnabled(), false));
        existing.setRecipient(incoming.getRecipient());
        existing.setSenderEmail(incoming.getSenderEmail());
        existing.setSenderName(nzStr(incoming.getSenderName(), "TelecomAI"));

        existing.setSmtpHost(nzStr(incoming.getSmtpHost(), "smtp.gmail.com"));
        existing.setSmtpPort(nz(incoming.getSmtpPort(), 587));
        existing.setSmtpUser(incoming.getSmtpUser());

        // Password: only update if not the mask sentinel.
        // This lets the frontend send back the masked value without overwriting.
        if (incoming.getSmtpPassword() != null
                && !PASSWORD_MASK.equals(incoming.getSmtpPassword())) {
            existing.setSmtpPassword(incoming.getSmtpPassword());
        }

        // Daily digest
        if (incoming.getFrequency() != null) {
            existing.setFrequency(incoming.getFrequency());
        }
        if (incoming.getWeeklyDay() != null) {
            existing.setWeeklyDay(incoming.getWeeklyDay());
        }
        existing.setDigestTime(nzStr(incoming.getDigestTime(), "08:00"));

        // Instant alerts
        existing.setInstantOnAiAttack(nz(incoming.getInstantOnAiAttack(), true));
        existing.setInstantOnDeviceOffline(nz(incoming.getInstantOnDeviceOffline(), true));
        existing.setInstantOnTriggerRule(nz(incoming.getInstantOnTriggerRule(), false));

        // Sections
        existing.setSectionExecutiveSummary(nz(incoming.getSectionExecutiveSummary(), true));
        existing.setSectionResourceUsage(nz(incoming.getSectionResourceUsage(), true));
        existing.setSectionSlaReport(nz(incoming.getSectionSlaReport(), true));
        existing.setSectionLatestEvents(nz(incoming.getSectionLatestEvents(), true));
        existing.setSectionMitigations(nz(incoming.getSectionMitigations(), true));

        // NOTE: lastDigestSentAt + lastInstantSentAt are NOT touched — they're
        // tracking fields managed by the service itself.

        EmailConfig saved = configRepo.save(existing);

        try {
            auditLogService.log(AuditLog.AuditType.EMAIL,
                    "Email configuration updated (enabled=" + saved.getEnabled() + ")");
        } catch (Exception ignored) {}

        // Mask password before returning
        if (saved.getSmtpPassword() != null && !saved.getSmtpPassword().isEmpty()) {
            saved.setSmtpPassword(PASSWORD_MASK);
        }
        return ResponseEntity.ok(saved);
    }

    // ══════════════════════════════════════════════════════════════
    //  POST /test — send a real digest covering the last 24h to verify SMTP
    // ══════════════════════════════════════════════════════════════
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendTest() {
        Map<String, Object> response = new HashMap<>();
        try {
            String message = digestService.sendTestDigest();
            response.put("status", "OK");
            response.put("message", message);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("status", "ERROR");
            // Surface the underlying message so the admin can debug SMTP issues
            response.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return ResponseEntity.ok(response); // 200 with error payload — frontend reads {status}
        }
    }

    // ── small helpers to avoid NPEs from JSON missing fields ────────────────
    private static <T> T nz(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String nzStr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
