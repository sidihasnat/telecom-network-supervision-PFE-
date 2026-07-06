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


    private Boolean isUp = true;

    private Double  throughputOut;       // Mbps outgoing
    private Double  throughputIn;        // Mbps incoming
    private Integer packetsPerSecSent;
    private Integer packetsPerSecRecv;

    private Double errorRate;
    private Double dropRate;
    private Double latency;
    private Double packetLoss;
    private Double jitter;

    private Double bytesPerPacketOut;
    private Double bytesPerPacketIn;

    private Double bandwidthUtilOut;
    private Double bandwidthUtilIn;


    @Column(nullable = true)
    private String attackLabel;


    @ManyToOne
    @JoinColumn(name = "metric_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private MetricData metricData;

    @OneToOne(mappedBy = "interfaceMetric", cascade = CascadeType.ALL)
    private AiResult aiResult;
}