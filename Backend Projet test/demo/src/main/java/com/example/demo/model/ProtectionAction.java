package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * ProtectionAction — audit log row for every executed mitigation.
 *
 * Updated April 2026 (Batch 4):
 *   - The old ActionType enum (SYN_COOKIES, RATE_LIMITING, …) is gone.
 *     The field is now a plain String that stores the playbook's admin-chosen
 *     name (e.g. "SYN Cookies Protection", "Rate-limit attacker IP").
 *   - TriggerMode enum kept (AUTO vs MANUAL) — still meaningful.
 *   - attackSession stays as ManyToOne.
 *
 * One row per playbook execution. The `output` field contains the stdout of
 * every command the playbook ran, one after another.
 */
@Data
@Entity
@Table(name = "protection_actions")
public class ProtectionAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Admin-chosen playbook name (not an enum anymore)
    // e.g. "SYN Cookies Protection", "Block attacker IP"
    @Column(nullable = false, length = 100)
    private String actionName;

    // Device where the command was run (resolved from playbook's targetType)
    @Column(nullable = false)
    private String deviceName;

    // Full command text that was run (may be multi-line for multi-step playbooks)
    @Column(length = 4000)
    private String command;

    // Aggregated stdout/stderr from every command line
    @Column(length = 4000)
    private String output;

    // Status of the overall playbook execution
    //   "SUCCESS" — every command ran without error
    //   "PARTIAL" — some commands succeeded, others failed
    //   "FAILED"  — all commands failed
    @Column(length = 20)
    private String status;

    // Legacy boolean — kept for backward compat with older rows.
    // New rows may leave this null; prefer `status`.
    private Boolean success;

    @Column(nullable = false)
    private LocalDateTime executedAt;

    // AUTO = ran automatically because the playbook has autoExecute=true
    // MANUAL = ran because operator clicked Mitigate
    @Column
    @Enumerated(EnumType.STRING)
    private TriggerMode triggerMode;

    public enum TriggerMode {
        AUTO, MANUAL
    }

    // Link to the attack this action was in response to
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