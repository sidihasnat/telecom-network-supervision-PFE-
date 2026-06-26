package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AlertRule — قواعد الـ Trigger System (مستوحى من Zabbix).
 *
 * المراقب يضبط شروط تطلق إنذارات:
 *   IF icmpInRate > 1000     → CRITICAL
 *   IF latency > 200         → WARNING
 *   IF passiveOpensRate > 50 → WARNING
 *
 * يُدار من: Settings → Trigger Rules (CRUD)
 * يُفحص: عند كل metric جديد يصل من Python agent
 *
 * ── Device Scoping ──
 * A rule can be scoped to:
 *   - deviceNames = null or empty → ALL devices
 *   - deviceNames = "web-server"   → only that device
 *   - deviceNames = "web-server,db-server,edge-router" → those devices only
 */
@Data
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ── Rule definition ───────────────────────────────────────
    @Column(nullable = false)
    private String metric;         // "icmpInRate", "latency", "cpuUsage", etc.

    @Column(nullable = false)
    private String conditionOp;    // ">", "<", ">=", "<=", "=="

    @Column(nullable = false)
    private Double value;          // الحد الذي يطلق الإنذار

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    public enum AlertSeverity {
        WARNING, CRITICAL
    }

    // ── Device Scope ──────────────────────────────────────────
    // Comma-separated device names:
    //   null or empty → apply to ALL devices
    //   "web-server"  → single device
    //   "web-server,db-server" → multiple
    @Column(length = 500)
    private String deviceNames;

    // ── Status ─────────────────────────────────────────────────
    private Boolean enabled = true;

    // ── Display name (اختياري) ─────────────────────────────────
    private String name;   // "High ICMP Rate", "Latency Threshold"

    // ── Helper methods ─────────────────────────────────────────

    /**
     * Returns the list of device names this rule applies to.
     * Empty list means: applies to all devices.
     */
    @Transient
    public List<String> getDeviceList() {
        if (deviceNames == null || deviceNames.isBlank()) {
            return new ArrayList<>();  // empty = apply to all
        }
        List<String> list = new ArrayList<>();
        for (String d : deviceNames.split(",")) {
            String trimmed = d.trim();
            if (!trimmed.isEmpty()) list.add(trimmed);
        }
        return list;
    }

    /**
     * Returns true if this rule should run for the given device.
     * Empty device list = applies to all devices.
     */
    @Transient
    public boolean appliesToDevice(String device) {
        List<String> list = getDeviceList();
        if (list.isEmpty()) return true;   // no scope = all devices
        return list.contains(device);
    }
}