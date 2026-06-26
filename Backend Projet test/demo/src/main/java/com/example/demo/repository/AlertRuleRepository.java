package com.example.demo.repository;

import com.example.demo.model.AlertRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AlertRuleRepository extends JpaRepository<AlertRule, Long> {

    // ── كل القواعد المفعلة ─────────────────────────────────────
    List<AlertRule> findByEnabledTrue();

}
