package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "device_status_log")
public class DeviceStatusLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String deviceName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status {
        ONLINE,
        OFFLINE
    }

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private Long durationSeconds;

    @PrePersist
    public void setDefaults() {
        if (this.startedAt == null) this.startedAt = LocalDateTime.now();
    }
}
