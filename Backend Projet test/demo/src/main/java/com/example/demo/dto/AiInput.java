package com.example.demo.dto;

import lombok.Data;

/**
 * Flat DTO that combines ALL features from all tables for ONE interface.
 *
 * Used for:
 *   1. Dataset export (CSV) → for AI training
 *   2. Real-time prediction → sent to Flask AI Engine every 10 seconds
 *
 * Each row = one interface at one timestamp
 *   = InterfaceMetric fields
 *   + TcpStats fields        (device-level, repeated per interface)
 *   + SecurityMetric fields   (device-level, repeated per interface)
 *   + MetricData fields       (device-level, repeated per interface)
 *
 * Fields NOT sent to AI model:
 *   - interfaceMetricId  → only used to save AiResult back to correct interface
 *   - deviceName         → AI must learn patterns, not device names
 *   - interfaceName      → same reason
 *
 * Fields sent to AI model:
 *   - All numeric features (56 features)
 *   - attackLabel (only during training, not during production)
 */
@Data
public class AiInput {

    // ── Identifiers (NOT sent to AI model) ──────────────────────
    // Used only to link prediction back to the correct interface
    private Long interfaceMetricId;

    // ── From MetricData (device-level) ──────────────────────────
    private Double  cpuUsage;
    private Double  memoryUsage;
    private Integer connections;

    // ── From InterfaceMetric (interface-level) ──────────────────
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

    // ── From TcpStats (device-level) ────────────────────────────
    // Raw counters
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

    // Computed rates
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

    // Router metrics
    private Integer routingTableSize;
    private Integer forwardedPackets;
    private Integer inDiscards;
    private Integer noRoutePackets;

    // ── From SecurityMetric (device-level) ──────────────────────
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

    // ── Label (only for training dataset) ───────────────────────
    // During dataset collection: "synflood", "normal", "fault", ...
    // During production: null
    private String attackLabel;
}