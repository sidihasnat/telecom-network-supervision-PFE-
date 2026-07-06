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

    private String deviceName;
    private String deviceType;

    private Double  cpuUsage;
    private Double  memoryUsage;
    private Integer connections;


    private Double  diskUsage;
    private Double  diskTotalGb;
    private Double  diskUsedGb;
    private Double  diskFreeGb;

    private String status;

    @Column(name = "created_at")
    private LocalDateTime timestamp;


    @OneToMany(mappedBy = "metricData", cascade = CascadeType.ALL)
    private List<InterfaceMetric> interfaces;

    @OneToOne(mappedBy = "metricData", cascade = CascadeType.ALL)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private TcpStats tcpStats;

    @OneToOne(mappedBy = "metricData", cascade = CascadeType.ALL)
    private SecurityMetric securityMetric;

    @OneToMany(mappedBy = "metricData", cascade = CascadeType.ALL)
    private List<ArpEntry> arpTable;


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