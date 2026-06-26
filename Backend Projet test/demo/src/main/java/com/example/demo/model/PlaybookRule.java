package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * PlaybookRule — a named mitigation playbook for a specific threat.
 *
 * REDESIGNED April 2026 (replaces the old Protection System enum approach):
 *
 *   The old system had two separate things:
 *     1. ProtectionAction enum (SYN_COOKIES, RATE_LIMIT...) that was hardcoded.
 *     2. PlaybookRule steps that were organized as multiple rows per attack type.
 *   You couldn't add a new protection without touching Java code, and the
 *   relationship between the two was confusing.
 *
 *   New design:
 *     - Each PlaybookRule is ONE named playbook (e.g. "SYN Cookies Protection").
 *     - It contains ALL its commands in one field as a newline-separated list.
 *     - It has ONE trigger: either an attackType (AI) or a trigger rule name (alert).
 *     - Multiple playbooks can exist for the same trigger, but only one executes
 *       (the enabled one with highest priority).
 *     - No more enum. Admin defines everything from Settings UI.
 *
 * Lifecycle at mitigation time:
 *   1. AttackSession.mitigate() looks up all enabled playbooks matching the trigger.
 *   2. Picks the one with highest priority (tiebreak: most recently updated).
 *   3. Runs each command in order via PlaybookExecutionService.
 *   4. Result is stored in ProtectionAction (existing entity, reused as audit log).
 *
 * Auto-execute: if autoExecute=true AND the trigger fires, mitigation happens
 * immediately without operator input. Otherwise waits for Security page "Mitigate" click.
 */
@Data
@Entity
@Table(name = "playbook_rules")
public class PlaybookRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Display name (admin-defined, no enum). Shown in Security "Mitigate" result
    // and in the Settings list.
    @Column(nullable = false, length = 100)
    private String name;  // e.g. "SYN Cookies Protection", "Rate-limit attacker IP"

    // ── Trigger: WHAT activates this playbook ──

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    public enum TriggerType {
        ATTACK,     // fires for an AI-detected attack type
        ALERT       // fires for a trigger rule (metric threshold)
    }

    // When triggerType=ATTACK, this holds the attackType e.g. "synflood"
    // When triggerType=ALERT, this holds the alert rule name / attackType string
    //                         (format same as AttackSession.attackType: "trigger:cpuUsage>80")
    @Column(length = 200)
    private String triggerValue;

    // ── Action: WHAT to run ──

    // One or more shell commands, newline-separated. Executed in order.
    // Each line is run as-is via the terminal agent on the target container.
    @Column(nullable = false, length = 4000)
    private String commands;

    // Where the commands run:
    //   VICTIM   = the attacked device (from AttackSession.deviceName)
    //   ATTACKER = requires session.sourceIPs, runs on VICTIM but substitutes {attackerIp}
    //   ROUTER   = runs on edge-router (upstream block)
    //   CUSTOM   = runs on the device named in targetDevice
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    public enum TargetType {
        VICTIM, ATTACKER, ROUTER, CUSTOM
    }

    private String targetDevice;  // only used when targetType=CUSTOM

    // ── Config ──

    // If multiple playbooks match the same trigger, the one with highest priority runs.
    // Ties broken by most recently updated.
    @Column(nullable = false)
    private Integer priority = 1;

    // If true, playbook runs automatically the instant the trigger fires.
    // If false, it only runs when operator clicks "Mitigate" in Security page.
    @Column(nullable = false)
    private Boolean autoExecute = false;

    @Column(nullable = false)
    private Boolean enabled = true;

    // ── Metadata ──

    @Column(length = 500)
    private String description;
}