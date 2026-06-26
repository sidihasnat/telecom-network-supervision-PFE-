package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * AttackSession — تجميع الهجمات المتكررة في session واحد.
 *
 * Updated April 2026: Now also tracks non-attack incidents:
 *   - AI             → AI detected an attack (original behavior)
 *   - TRIGGER_RULE   → An AlertRule threshold was crossed (e.g. cpuUsage > 80)
 *   - INTERFACE_DOWN → A network interface went down (routers only)
 *
 * This lets the Dashboard use ONE unified system for all incidents:
 *   - Same Timeline (Home)
 *   - Same Security Live tab
 *   - Same TopologyMap coloring (warning/attack)
 *   - Same SLA downtime tracking
 *   - Same AuditLog trail
 */
@Data
@Entity
@Table(name = "attack_sessions")
public class AttackSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Target identification ──────────────────────────────────
    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String interfaceName;

    @Column(nullable = false)
    private String attackType;        // "synflood", "dos", "trigger:cpuUsage>80", "interface_down"

    // ── Status ─────────────────────────────────────────────────
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;

    public enum SessionStatus {
        ACTIVE, ENDED, MITIGATED
    }

    // 🆕 Session Source — what generated this incident
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionSource sessionSource = SessionSource.AI;

    public enum SessionSource {
        AI,              // AI model detected an attack
        TRIGGER_RULE,    // Admin-defined AlertRule threshold crossed
        INTERFACE_DOWN,  // A router interface went down
        DEVICE_OFFLINE   // A device stopped sending metrics (crash/network — NOT manual pause)
    }


    @Column
    @Enumerated(EnumType.STRING)
    private Severity severity = Severity.CRITICAL;

    public enum Severity {
        WARNING, CRITICAL
    }

    private Long ruleId;

    // ── Timing ─────────────────────────────────────────────────
    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime lastSeenAt;   // last time the condition still applied

    private LocalDateTime mitigatedAt;  // when protection kicked in

    // ── Confidence (AI only) ──────────────────────────────────
    // For TRIGGER_RULE: avgConfidence = the actual metric value when fired
    //                  (useful for showing "cpuUsage: 85 (limit 80)")
    private Double avgConfidence;
    private Double maxConfidence;
    private Integer predictionCount = 0;

    // ── AI Details ─────────────────────────────────────────────
    @Column(length = 2048)
    private String topFeatures;       // JSON string

    // ── Protection ─────────────────────────────────────────────
    private String protectionAction;

    // ── Acknowledgement & Escalation ───────────────────────────
    private Boolean acknowledged = false;
    private LocalDateTime acknowledgedAt;

    private Integer escalationLevel = 1;

    // ── Relation: AI predictions linked to this session ────────
    @OneToMany(mappedBy = "attackSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("attackSession")
    private List<AiResult> predictions = new ArrayList<>();

    // ── Computed: duration ──────────────────────────────────────
    @Transient
    public Long getDurationSeconds() {
        if (startedAt == null) return 0L;
        LocalDateTime end = (endedAt != null) ? endedAt : LocalDateTime.now();
        return Duration.between(startedAt, end).getSeconds();
    }

    @Transient
    public String getDurationFormatted() {
        long seconds = getDurationSeconds();
        if (seconds < 60) return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}