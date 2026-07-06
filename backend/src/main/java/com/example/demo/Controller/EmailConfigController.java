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

@RestController
@RequestMapping("/api/email-config")
public class EmailConfigController {

    private static final String PASSWORD_MASK = "***";

    @Autowired
    private EmailConfigRepository configRepo;

    @Autowired
    private EmailDigestService digestService;

    @Autowired
    private AuditLogService auditLogService;


    @GetMapping
    public EmailConfig get() {
        EmailConfig cfg = configRepo.findById(1L).orElseGet(() -> {
            EmailConfig fresh = new EmailConfig();
            fresh.setId(1L);
            return configRepo.save(fresh);
        });
        if (cfg.getSmtpPassword() != null && !cfg.getSmtpPassword().isEmpty()) {
            cfg.setSmtpPassword(PASSWORD_MASK);
        }
        return cfg;
    }


    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<EmailConfig> update(@RequestBody EmailConfig incoming) {
        EmailConfig existing = configRepo.findById(1L).orElseGet(() -> {
            EmailConfig fresh = new EmailConfig();
            fresh.setId(1L);
            return fresh;
        });

        existing.setEnabled(nz(incoming.getEnabled(), false));
        existing.setRecipient(incoming.getRecipient());
        existing.setSenderEmail(incoming.getSenderEmail());
        existing.setSenderName(nzStr(incoming.getSenderName(), "TelecomAI"));

        existing.setSmtpHost(nzStr(incoming.getSmtpHost(), "smtp.gmail.com"));
        existing.setSmtpPort(nz(incoming.getSmtpPort(), 587));
        existing.setSmtpUser(incoming.getSmtpUser());


        if (incoming.getSmtpPassword() != null
                && !PASSWORD_MASK.equals(incoming.getSmtpPassword())) {
            existing.setSmtpPassword(incoming.getSmtpPassword());
        }


        if (incoming.getFrequency() != null) {
            existing.setFrequency(incoming.getFrequency());
        }
        if (incoming.getWeeklyDay() != null) {
            existing.setWeeklyDay(incoming.getWeeklyDay());
        }
        existing.setDigestTime(nzStr(incoming.getDigestTime(), "08:00"));

        existing.setInstantOnAiAttack(nz(incoming.getInstantOnAiAttack(), true));
        existing.setInstantOnDeviceOffline(nz(incoming.getInstantOnDeviceOffline(), true));
        existing.setInstantOnTriggerRule(nz(incoming.getInstantOnTriggerRule(), false));

        existing.setSectionExecutiveSummary(nz(incoming.getSectionExecutiveSummary(), true));
        existing.setSectionResourceUsage(nz(incoming.getSectionResourceUsage(), true));
        existing.setSectionSlaReport(nz(incoming.getSectionSlaReport(), true));
        existing.setSectionLatestEvents(nz(incoming.getSectionLatestEvents(), true));
        existing.setSectionMitigations(nz(incoming.getSectionMitigations(), true));



        EmailConfig saved = configRepo.save(existing);

        try {
            auditLogService.log(AuditLog.AuditType.EMAIL,
                    "Email configuration updated (enabled=" + saved.getEnabled() + ")");
        } catch (Exception ignored) {}

        if (saved.getSmtpPassword() != null && !saved.getSmtpPassword().isEmpty()) {
            saved.setSmtpPassword(PASSWORD_MASK);
        }
        return ResponseEntity.ok(saved);
    }

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
            response.put("message", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return ResponseEntity.ok(response);
        }
    }

    private static <T> T nz(T value, T fallback) {
        return value == null ? fallback : value;
    }

    private static String nzStr(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
