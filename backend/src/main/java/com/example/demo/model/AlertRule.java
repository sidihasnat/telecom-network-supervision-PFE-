package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


@Data
@Entity
@Table(name = "alert_rules")
public class AlertRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String metric;

    @Column(nullable = false)
    private String conditionOp;

    @Column(nullable = false)
    private Double value;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AlertSeverity severity;

    public enum AlertSeverity {
        WARNING, CRITICAL
    }


    @Column(length = 500)
    private String deviceNames;

    private Boolean enabled = true;

    private String name;   // "High ICMP Rate", "Latency Threshold"


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

    @Transient
    public boolean appliesToDevice(String device) {
        List<String> list = getDeviceList();
        if (list.isEmpty()) return true;   // no scope = all devices
        return list.contains(device);
    }
}