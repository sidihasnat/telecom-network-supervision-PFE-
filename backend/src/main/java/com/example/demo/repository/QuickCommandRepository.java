package com.example.demo.repository;

import com.example.demo.model.QuickCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuickCommandRepository extends JpaRepository<QuickCommand, Long> {

    List<QuickCommand> findByEnabledTrueOrderBySortOrderAsc();

    List<QuickCommand> findAllByOrderBySortOrderAsc();
}
