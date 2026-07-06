package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Data
@Entity
@Table(name = "attack_sessions")
public class AttackSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    private String interfaceName;

    @Column(nullable = false)
    private String attackType;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status = SessionStatus.ACTIVE;

    public enum SessionStatus {
        ACTIVE, ENDED, MITIGATED
    }

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionSource sessionSource = SessionSource.AI;

    public enum SessionSource {
        AI,
        TRIGGER_RULE,
        INTERFACE_DOWN,
        DEVICE_OFFLINE
    }


    @Column
    @Enumerated(EnumType.STRING)
    private Severity severity = Severity.CRITICAL;

    public enum Severity {
        WARNING, CRITICAL
    }

    private Long ruleId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private LocalDateTime lastSeenAt;

    private LocalDateTime mitigatedAt;


    private Double avgConfidence;
    private Double maxConfidence;
    private Integer predictionCount = 0;

    @Column(length = 2048)
    private String topFeatures;       // JSON string

    private String protectionAction;

    @Column(length = 2048)
    private String attackerIps;
    private Boolean acknowledged = false;
    private LocalDateTime acknowledgedAt;

    private Integer escalationLevel = 1;

    @OneToMany(mappedBy = "attackSession", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties("attackSession")
    private List<AiResult> predictions = new ArrayList<>();

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