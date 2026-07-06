package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "ai_results")
public class AiResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Column(nullable = true)
    private String predictedAttack;


    @Column(nullable = true)
    private Double predictionConfidence;

    @Column(nullable = true)
    private Double anomalyScore;

    @Column(nullable = true)
    private Double faultProbability;


    @Column(length = 2048, nullable = true)
    private String topFeatures;  // JSON string


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attack_session_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private AttackSession attackSession;


    @OneToOne
    @JoinColumn(name = "interface_metric_id")
    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private InterfaceMetric interfaceMetric;
}