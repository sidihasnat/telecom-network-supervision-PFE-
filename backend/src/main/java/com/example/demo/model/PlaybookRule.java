package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;


@Data
@Entity
@Table(name = "playbook_rules")
public class PlaybookRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TriggerType triggerType;

    public enum TriggerType {
        ATTACK,
        ALERT
    }


    @Column(length = 200)
    private String triggerValue;


    @Column(nullable = false, length = 4000)
    private String commands;


    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TargetType targetType;

    public enum TargetType {
        VICTIM, ATTACKER, ROUTER, CUSTOM
    }

    private String targetDevice;


    @Column(nullable = false)
    private Integer priority = 1;


    @Column(nullable = false)
    private Boolean autoExecute = false;

    @Column(nullable = false)
    private Boolean enabled = true;


    @Column(length = 500)
    private String description;
}