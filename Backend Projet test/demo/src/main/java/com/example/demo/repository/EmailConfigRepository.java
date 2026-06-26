package com.example.demo.repository;

import com.example.demo.model.EmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * EmailConfigRepository — singleton row (id=1).
 *
 * Use findById(1L) to load. If empty (first run), the controller/service
 * creates a default row.
 */
@Repository
public interface EmailConfigRepository extends JpaRepository<EmailConfig, Long> {
}
