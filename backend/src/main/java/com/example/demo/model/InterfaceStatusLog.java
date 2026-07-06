package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "interface_status_logs",
        indexes = {
                @Index(name = "idx_ifstat_device_iface", columnList = "device_name, interface_name"),
                @Index(name = "idx_ifstat_started", columnList = "started_at")
        })
public class InterfaceStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_name", nullable = false)
    private String deviceName;

    @Column(name = "interface_name", nullable = false)
    private String interfaceName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private InterfaceState status;

    public enum InterfaceState {
        UP, DOWN
    }

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;


    @Transient
    public Long getDurationSeconds() {
        if (startedAt == null) return 0L;
        LocalDateTime end = (endedAt != null) ? endedAt : LocalDateTime.now();
        return Duration.between(startedAt, end).getSeconds();
    }

    @Transient
    public String getDurationFormatted() {
        long s = getDurationSeconds();
        if (s < 60) return s + "s";
        if (s < 3600) return (s / 60) + "m " + (s % 60) + "s";
        return (s / 3600) + "h " + ((s % 3600) / 60) + "m";
    }
}