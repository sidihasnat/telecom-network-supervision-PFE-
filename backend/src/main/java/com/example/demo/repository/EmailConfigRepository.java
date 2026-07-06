package com.example.demo.repository;

import com.example.demo.model.EmailConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EmailConfigRepository extends JpaRepository<EmailConfig, Long> {
}
