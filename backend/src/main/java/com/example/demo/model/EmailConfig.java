package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.DayOfWeek;
import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "email_config")
public class EmailConfig {

    @Id
    private Long id = 1L;  // singleton — always row 1

    private Boolean enabled = false;

    private String recipient;
    private String senderEmail;
    private String senderName = "TelecomAI";

    private String smtpHost = "smtp.gmail.com";
    private Integer smtpPort = 587;
    private String smtpUser;
    @Column(length = 255)
    private String smtpPassword;

    @Enumerated(EnumType.STRING)
    private DigestFrequency frequency = DigestFrequency.DAILY;

    public enum DigestFrequency {
        DAILY,
        EVERY_2_DAYS,
        WEEKLY
    }

    @Enumerated(EnumType.STRING)
    private DayOfWeek weeklyDay = DayOfWeek.MONDAY;

    private String digestTime = "08:00";

    private Boolean instantOnAiAttack = true;
    private Boolean instantOnDeviceOffline = true;
    private Boolean instantOnTriggerRule = false;

    private Boolean sectionExecutiveSummary = true;
    private Boolean sectionResourceUsage = true;
    private Boolean sectionSlaReport = true;
    private Boolean sectionLatestEvents = true;
    private Boolean sectionMitigations = true;

    private LocalDateTime lastDigestSentAt;

    private LocalDateTime lastInstantSentAt;
}
