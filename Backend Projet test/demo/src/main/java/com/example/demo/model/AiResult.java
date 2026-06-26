package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "ai_results")
public class AiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── AI prediction (set by AI Engine after training) ───────────
    // What the AI thinks is happening on this interface right now
    // Values: "normal" | "dos" | "synflood" | "portscan" | "bruteforce" | "fault" | "transit"
    // null = AI not yet trained or not yet called
    @Column(nullable = true)
    private String predictedAttack;

    // Confidence score: 0.0 to 1.0
    // Example: 0.94 means AI is 94% sure it is a DoS attack
    @Column(nullable = true)
    private Double predictionConfidence;

    // ── Anomaly score (from Isolation Forest) ─────────────────────
    // Negative value = anomaly, positive = normal
    // The more negative, the more anomalous
    @Column(nullable = true)
    private Double anomalyScore;

    // ── Fault prediction (from LSTM) ──────────────────────────────
    // Probability that a fault will occur in the next N minutes
    // 0.0 = no risk, 1.0 = fault almost certain
    @Column(nullable = true)
    private Double faultProbability;


    // في AiResult.java
    @Column(length = 2048, nullable = true)
    private String topFeatures;  // JSON string


    // ── Relation to AttackSession ────────────────────────────────
// كل AiResult يمكن أن ينتمي لـ AttackSession
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attack_session_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AttackSession attackSession;

    // ── Relation ──────────────────────────────────────────────────
    // AiResult belongs to ONE InterfaceMetric (not MetricData)
    // This means AI predicts per interface, not per device
    @OneToOne
    @JoinColumn(name = "interface_metric_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private InterfaceMetric interfaceMetric;
}