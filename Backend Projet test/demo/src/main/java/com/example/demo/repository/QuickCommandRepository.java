package com.example.demo.repository;

import com.example.demo.model.QuickCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuickCommandRepository extends JpaRepository<QuickCommand, Long> {

    // For Terminal page: only enabled commands, sorted by display order
    List<QuickCommand> findByEnabledTrueOrderBySortOrderAsc();

    // For Settings page: all commands (including disabled) so admin can toggle them
    List<QuickCommand> findAllByOrderBySortOrderAsc();
}
