package com.example.demo.repository;

import com.example.demo.model.InterfaceMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InterfaceMetricRepository extends JpaRepository<InterfaceMetric, Long> {

    List<InterfaceMetric> findByAttackLabelIsNotNull();

    List<InterfaceMetric> findByAttackLabel(String label);

    @Query("SELECT im.attackLabel, COUNT(im) FROM InterfaceMetric im " +
            "WHERE im.attackLabel IS NOT NULL " +
            "GROUP BY im.attackLabel")
    List<Object[]> countByAttackLabel();

    List<InterfaceMetric> findTop50ByAiResultPredictedAttackIsNotNullOrderByIdDesc();

    List<InterfaceMetric> findTop100ByAiResultPredictedAttackIsNotNullOrderByIdDesc();
}