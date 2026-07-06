package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditType type;

    public enum AuditType {
        PROTECTION,
        ESCALATION,
        ACKNOWLEDGE,
        TERMINAL,
        AI,
        SETTINGS,
        AUTH,
        DEVICE_CONTROL,
        EMAIL
    }

    @Column(nullable = false, length = 500)
    private String description;

    private String deviceName;

    private String username;

    @PrePersist
    public void setTimestampIfNull() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
