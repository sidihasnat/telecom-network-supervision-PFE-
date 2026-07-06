package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;


@Data
@Entity
@Table(name = "protection_actions")
public class ProtectionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = false, length = 100)
    private String actionName;

    @Column(nullable = false)
    private String deviceName;

    @Column(length = 4000)
    private String command;

    @Column(length = 4000)
    private String output;


    @Column(length = 20)
    private String status;

    private Boolean success;

    @Column(nullable = false)
    private LocalDateTime executedAt;


    @Column
    @Enumerated(EnumType.STRING)
    private TriggerMode triggerMode;

    public enum TriggerMode {
        AUTO, MANUAL
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attack_session_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AttackSession attackSession;

    @PrePersist
    public void setTimestampIfNull() {
        if (this.executedAt == null) {
            this.executedAt = LocalDateTime.now();
        }
    }
}