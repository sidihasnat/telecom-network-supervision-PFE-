package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * EmailConfig — singleton entity (id=1) holding email notification settings.
 *
 * Two notification systems coexist:
 *   1. Daily Digest (scheduled): periodic summary email (daily / every 2 days / weekly)
 *   2. Instant Critical Alerts: immediate email when AI attack / device offline / trigger fires
 *
 * Both share the same SMTP credentials and recipient.
 *
 * The password is stored as-is in DB. The REST GET response masks it as "***".
 * Only PUT requests with a non-mask value update the stored password.
 */
@Data
@Entity
@Table(name = "email_config")
public class EmailConfig {

    @Id
    private Long id = 1L;  // singleton — always row 1

    // ── Master switch ──────────────────────────────────────────
    private Boolean enabled = false;

    // ── Recipient & Sender ─────────────────────────────────────
    private String recipient;        // e.g. "admin@company.com"
    private String senderEmail;      // e.g. "telecomai.pfe@gmail.com"
    private String senderName = "TelecomAI";

    // ── SMTP Configuration ─────────────────────────────────────
    private String smtpHost = "smtp.gmail.com";
    private Integer smtpPort = 587;
    private String smtpUser;         // usually = senderEmail
    @Column(length = 255)
    private String smtpPassword;     // Gmail App Password (16 chars, no spaces)

    // ── Daily Digest Settings ──────────────────────────────────
    @Enumerated(EnumType.STRING)
    private DigestFrequency frequency = DigestFrequency.DAILY;

    public enum DigestFrequency {
        DAILY,         // every day at digestTime
        EVERY_2_DAYS,  // every 48h+ from lastDigestSentAt
        WEEKLY         // only on weeklyDay at digestTime
    }

    @Enumerated(EnumType.STRING)
    private DayOfWeek weeklyDay = DayOfWeek.MONDAY;

    /** Time of day to send digest, format "HH:mm" (e.g. "08:00"). */
    private String digestTime = "08:00";

    // ── Instant Critical Alerts ────────────────────────────────
    private Boolean instantOnAiAttack = true;
    private Boolean instantOnDeviceOffline = true;
    private Boolean instantOnTriggerRule = false;

    // ── Digest Sections (checkboxes) ───────────────────────────
    private Boolean sectionExecutiveSummary = true;
    private Boolean sectionResourceUsage = true;
    private Boolean sectionSlaReport = true;
    private Boolean sectionLatestEvents = true;
    private Boolean sectionMitigations = true;

    // ── Tracking (auto-managed, not user-editable) ─────────────
    /** Last successful digest send — prevents duplicate sends within ~23h. */
    private LocalDateTime lastDigestSentAt;

    /** Last instant alert send — used for rate-limiting (5-min cooldown). */
    private LocalDateTime lastInstantSentAt;
}
