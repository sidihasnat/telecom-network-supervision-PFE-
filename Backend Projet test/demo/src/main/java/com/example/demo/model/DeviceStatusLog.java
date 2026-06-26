package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * DeviceStatusLog — يسجل فترات online/offline لكل جهاز.
 *
 * الاستخدام:
 *   - SLA: حساب downtime الحقيقي (offline + attack)
 *   - Timeline: عرض بصري لحالة الأجهزة عبر الزمن
 *   - Sidebar: تأكيد offline من Backend وليس فقط Frontend
 *
 * المنطق:
 *   - كل مرة جهاز يرسل metric → deviceHeartbeat() → يغلق أي OFFLINE مفتوح
 *   - Scheduled task كل 15 ثانية → لو جهاز ما أرسل 30+ ثانية → يفتح OFFLINE
 */
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

    private LocalDateTime endedAt;  // null = still in this state

    private Long durationSeconds;   // computed when endedAt is set

    @PrePersist
    public void setDefaults() {
        if (this.startedAt == null) this.startedAt = LocalDateTime.now();
    }
}
