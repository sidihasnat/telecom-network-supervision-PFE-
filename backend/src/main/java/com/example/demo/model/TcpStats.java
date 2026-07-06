package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "tcp_stats")
public class TcpStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer passiveOpens;

    private Integer tcpRetransmissions;

    private Integer icmpInMsgs;

    private Integer activeOpens;

    private Integer attemptFails;

    private Integer estabResets;

    private Integer currEstab;

    private Long inSegs;

    private Long outSegs;

    private Integer inErrs;

    private Integer outRsts;

    private Integer icmpInErrors;


    private Long udpInDatagrams;


    private Long udpOutDatagrams;


    private Integer udpInErrors;


    private Integer udpNoPorts;


    private Double passiveOpensRate;

    private Double retransRate;

    private Double icmpInRate;

    private Double attemptFailsRate;

    private Double estabResetsRate;

    private Double outRstsRate;

    private Double inSegsRate;

    private Double outSegsRate;

    private Double activeOpensRate;

    private Double inOutSegRatio;

    private Double rstPerConnection;


    @Column(name = "udp_in_rate")
    private Double udpInRate;

    @Column(name = "udp_out_rate")
    private Double udpOutRate;

    @Column(name = "udp_error_rate")
    private Double udpErrorRate;

    @Column(name = "udp_no_port_rate")
    private Double udpNoPortRate;

    @Column(name = "udp_in_out_ratio")
    private Double udpInOutRatio;

    private Integer routingTableSize;
    private Integer forwardedPackets;
    private Integer inDiscards;
    private Integer noRoutePackets;

    @OneToOne
    @JoinColumn(name = "metric_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MetricData metricData;
}