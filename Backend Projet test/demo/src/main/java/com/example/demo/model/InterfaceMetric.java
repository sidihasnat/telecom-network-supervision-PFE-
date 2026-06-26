package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "interface_metrics")
public class InterfaceMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String interfaceName;

    // 🆕 Interface operational state (from `ip link show`)
    //    true  = interface is UP (or no info → assume up for backward compat)
    //    false = interface is DOWN (detected by metrics.py)
    //
    // Used by InterfaceStatusService to create INTERFACE_DOWN AttackSessions
    // when a router interface goes offline.
    private Boolean isUp = true;

    // ── Traffic ───────────────────────────────────────────────────
    private Double  throughputOut;       // Mbps outgoing
    private Double  throughputIn;        // Mbps incoming
    private Integer packetsPerSecSent;
    private Integer packetsPerSecRecv;

    // ── Quality ───────────────────────────────────────────────────
    private Double errorRate;
    private Double dropRate;
    private Double latency;
    private Double packetLoss;
    private Double jitter;

    // ── DDoS indicators ───────────────────────────────────────────
    // Normal traffic:  packets are large  (500-1500 bytes)
    // DDoS/DoS flood:  packets are tiny   (40-64 bytes)
    private Double bytesPerPacketOut;
    private Double bytesPerPacketIn;

    private Double bandwidthUtilOut;
    private Double bandwidthUtilIn;

    // ── Dataset Label ─────────────────────────────────────────────
    // Set by attack_simulator during dataset collection
    // Values: "normal" | "dos" | "synflood" | "transit" | "fault" | ...
    // null = normal monitoring mode (not collecting dataset)
    @Column(nullable = true)
    private String attackLabel;

    // ── Relations ─────────────────────────────────────────────────

    @ManyToOne
    @JoinColumn(name = "metric_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private MetricData metricData;

    // One InterfaceMetric → One AiResult (AI prediction for THIS interface)
    @OneToOne(mappedBy = "interfaceMetric", cascade = CascadeType.ALL)
    private AiResult aiResult;
}