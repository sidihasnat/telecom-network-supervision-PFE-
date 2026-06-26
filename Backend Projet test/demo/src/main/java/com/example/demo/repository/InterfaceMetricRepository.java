package com.example.demo.repository;

import com.example.demo.model.InterfaceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InterfaceMetricRepository extends JpaRepository<InterfaceMetric, Long> {

    // ── Dataset: all labeled interfaces ─────────────────────────
    List<InterfaceMetric> findByAttackLabelIsNotNull();

    // ── Dataset: filter by specific label ───────────────────────
    List<InterfaceMetric> findByAttackLabel(String label);

    // ── Dataset stats: count per label ──────────────────────────
    @Query("SELECT im.attackLabel, COUNT(im) FROM InterfaceMetric im " +
            "WHERE im.attackLabel IS NOT NULL " +
            "GROUP BY im.attackLabel")
    List<Object[]> countByAttackLabel();

    // ── AI Predictions: latest interfaces with predictions ──────
    List<InterfaceMetric> findTop50ByAiResultPredictedAttackIsNotNullOrderByIdDesc();

    // ── AI Predictions: larger fetch for deduplication in getLatestPredictions ──
    List<InterfaceMetric> findTop100ByAiResultPredictedAttackIsNotNullOrderByIdDesc();
}