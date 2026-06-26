package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.model.AuditLog;
import com.example.demo.model.QuickCommand;
import com.example.demo.repository.QuickCommandRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * QuickCommandController — CRUD endpoints for Terminal quick-command buttons.
 *
 *  GET    /api/quick-commands              → list all (admin view, includes disabled)
 *  GET    /api/quick-commands/enabled      → list enabled only (Terminal page)
 *  POST   /api/quick-commands              → create (ADMIN only)
 *  PUT    /api/quick-commands/{id}         → update (ADMIN only)
 *  DELETE /api/quick-commands/{id}         → delete (ADMIN only)
 */
@RestController
@RequestMapping("/api/quick-commands")
public class QuickCommandController {

    @Autowired
    private QuickCommandRepository repo;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public List<QuickCommand> getAll() {
        return repo.findAllByOrderBySortOrderAsc();
    }

    @GetMapping("/enabled")
    public List<QuickCommand> getEnabled() {
        return repo.findByEnabledTrueOrderBySortOrderAsc();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QuickCommand> create(@RequestBody QuickCommand cmd) {
        QuickCommand saved = repo.save(cmd);
        auditLogService.log(AuditLog.AuditType.SETTINGS,
                "Quick command created: " + cmd.getName() + " → " + cmd.getCommand());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<QuickCommand> update(@PathVariable Long id,
                                                @RequestBody QuickCommand cmd) {
        return repo.findById(id).map(existing -> {
            existing.setName(cmd.getName());
            existing.setCommand(cmd.getCommand());
            existing.setDeviceType(cmd.getDeviceType());
            existing.setSortOrder(cmd.getSortOrder() != null ? cmd.getSortOrder() : 0);
            existing.setEnabled(cmd.getEnabled() != null ? cmd.getEnabled() : true);
            QuickCommand saved = repo.save(existing);
            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Quick command updated: " + cmd.getName());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (repo.existsById(id)) {
            repo.deleteById(id);
            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Quick command deleted (id=" + id + ")");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
