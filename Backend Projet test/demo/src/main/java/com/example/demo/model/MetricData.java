package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "metrics")
public class MetricData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Device Info ───────────────────────────────────────────────
    private String deviceName;
    private String deviceType;

    // ── System (stays here — general device health) ───────────────
    private Double  cpuUsage;
    private Double  memoryUsage;
    private Integer connections;

    // 🆕 Disk Usage (added April 2026)
    //   diskUsage   → percent used (0-100)
    //   diskTotalGb → total size (GB)
    //   diskUsedGb  → used size (GB)
    //   diskFreeGb  → free size (GB)
    private Double  diskUsage;
    private Double  diskTotalGb;
    private Double  diskUsedGb;
    private Double  diskFreeGb;

    // ── Status ────────────────────────────────────────────────────
    // "normal" | "warning" | "critical"
    private String status;

    // ── Timestamp ─────────────────────────────────────────────────
    @Column(name = "created_at")
    private LocalDateTime timestamp;

    // ── Relations ─────────────────────────────────────────────────

    // One MetricData → Many InterfaceMetric (one per network interface)
    // Example: router has br-wan + eth2 → 2 InterfaceMetric rows
    // Each InterfaceMetric has its own AiResult
    @OneToMany(mappedBy = "metricData", cascade = CascadeType.ALL)
    private List<InterfaceMetric> interfaces;

    // One MetricData → One TcpStats (TCP/ICMP counters for this reading)
    @OneToOne(mappedBy = "metricData", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TcpStats tcpStats;

    // One MetricData → One SecurityMetric (all security indicators)
    @OneToOne(mappedBy = "metricData", cascade = CascadeType.ALL)
    private SecurityMetric securityMetric;

    // One MetricData → Many ArpEntry (all IPs in ARP table)
    @OneToMany(mappedBy = "metricData", cascade = CascadeType.ALL)
    private List<ArpEntry> arpTable;

    // ── AiResult REMOVED from here ──────────────────────────────
    // AiResult is now per InterfaceMetric, not per MetricData
    // Access via: metric.getInterfaces().get(0).getAiResult()

    // ── Neighbors (topology) ──────────────────────────────────────
    // Stored as comma-separated string: "10.0.0.1,10.0.1.1"
    @Column(name = "neighbors", length = 512)
    private String neighborsRaw;

    @Transient
    private List<String> neighbors;

    @PrePersist
    @PreUpdate
    public void serializeNeighbors() {
        if (neighbors != null && !neighbors.isEmpty()) {
            this.neighborsRaw = String.join(",", neighbors);
        }
    }

    @PostLoad
    public void deserializeNeighbors() {
        if (neighborsRaw != null && !neighborsRaw.isBlank()) {
            this.neighbors = List.of(neighborsRaw.split(","));
        }
    }
}