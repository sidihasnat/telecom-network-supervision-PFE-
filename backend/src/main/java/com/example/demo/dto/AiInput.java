package com.example.demo.dto;

import lombok.Data;


@Data
public class AiInput {


    private Long interfaceMetricId;

    private Double  cpuUsage;
    private Double  memoryUsage;
    private Integer connections;

    private Double  throughputOut;
    private Double  throughputIn;
    private Integer packetsPerSecSent;
    private Integer packetsPerSecRecv;
    private Double  errorRate;
    private Double  dropRate;
    private Double  latency;
    private Double  packetLoss;
    private Double  jitter;
    private Double  bytesPerPacketOut;
    private Double  bytesPerPacketIn;
    private Double  bandwidthUtilOut;
    private Double  bandwidthUtilIn;

    private Integer passiveOpens;
    private Integer activeOpens;
    private Integer attemptFails;
    private Integer estabResets;
    private Integer currEstab;
    private Long    inSegs;
    private Long    outSegs;
    private Integer inErrs;
    private Integer outRsts;
    private Integer tcpRetransmissions;
    private Integer icmpInMsgs;
    private Integer icmpInErrors;
    private Long    udpInDatagrams;
    private Long    udpOutDatagrams;
    private Integer udpInErrors;
    private Integer udpNoPorts;

    private Double passiveOpensRate;
    private Double activeOpensRate;
    private Double attemptFailsRate;
    private Double estabResetsRate;
    private Double outRstsRate;
    private Double inSegsRate;
    private Double outSegsRate;
    private Double retransRate;
    private Double icmpInRate;
    private Double inOutSegRatio;
    private Double rstPerConnection;
    private Double udpInRate;
    private Double udpOutRate;
    private Double udpErrorRate;
    private Double udpNoPortRate;
    private Double udpInOutRatio;

    private Integer routingTableSize;
    private Integer forwardedPackets;
    private Integer inDiscards;
    private Integer noRoutePackets;

    private Integer halfOpenConnections;
    private Integer uniqueSourceIPs;
    private Integer failedLogins;
    private Double  failedLoginsRate;
    private Integer sshConnectionAttempts;
    private Integer topAttackerRepeat;
    private Integer uniqueDestinationPorts;
    private Integer sensitivePortsHit;
    private Integer timeWaitConnections;
    private Double  avgConnectionDuration;
    private Double  maxConnectionDuration;
    private Double  longConnectionRatio;


    private String attackLabel;
}