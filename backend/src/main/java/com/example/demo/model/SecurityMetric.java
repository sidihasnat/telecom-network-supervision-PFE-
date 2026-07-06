package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "security_metrics")
public class SecurityMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer halfOpenConnections;
    private Integer uniqueSourceIPs;
    private Integer failedLogins;
    private Double failedLoginsRate;
    private Integer sshConnectionAttempts;
    private Integer topAttackerRepeat;
    private Integer uniqueDestinationPorts;
    private Integer timeWaitConnections;

    private Integer sensitivePortsHit;

    @Column(length = 1024)
    private String portAccessCount;

    @Column(length = 2048)
    private String topAttackersDetail;

    private Double avgConnectionDuration;
    private Double maxConnectionDuration;
    private Double longConnectionRatio;

    @OneToOne
    @JoinColumn(name = "metric_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private MetricData metricData;
}