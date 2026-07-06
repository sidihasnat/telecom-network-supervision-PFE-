package com.example.demo.repository;

import com.example.demo.model.ProtectionAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProtectionActionRepository extends JpaRepository<ProtectionAction, Long> {

    List<ProtectionAction> findTop50ByOrderByExecutedAtDesc();

    List<ProtectionAction> findByAttackSessionId(Long sessionId);
}
