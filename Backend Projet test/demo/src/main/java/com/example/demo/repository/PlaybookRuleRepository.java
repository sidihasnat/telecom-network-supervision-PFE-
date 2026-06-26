package com.example.demo.repository;

import com.example.demo.model.PlaybookRule;
import com.example.demo.model.PlaybookRule.TriggerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlaybookRuleRepository extends JpaRepository<PlaybookRule, Long> {

    List<PlaybookRule> findAllByOrderByPriorityDescIdDesc();

    // Find enabled playbooks matching a given trigger. Ordered by priority DESC so
    // the first one is the winner when AttackSessionService.mitigate() picks one.
    @Query("SELECT p FROM PlaybookRule p " +
            "WHERE p.enabled = true " +
            "AND p.triggerType = :triggerType " +
            "AND p.triggerValue = :triggerValue " +
            "ORDER BY p.priority DESC, p.id DESC")
    List<PlaybookRule> findMatching(@Param("triggerType") TriggerType triggerType,
                                    @Param("triggerValue") String triggerValue);

    // Auto-exec playbooks for an attack type — used by live detection path
    @Query("SELECT p FROM PlaybookRule p " +
            "WHERE p.enabled = true AND p.autoExecute = true " +
            "AND p.triggerType = :triggerType " +
            "AND p.triggerValue = :triggerValue " +
            "ORDER BY p.priority DESC, p.id DESC")
    List<PlaybookRule> findAutoExecMatching(@Param("triggerType") TriggerType triggerType,
                                            @Param("triggerValue") String triggerValue);
}