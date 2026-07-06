package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.Service.PlaybookExecutionService;
import com.example.demo.model.AttackSession;
import com.example.demo.model.AuditLog;
import com.example.demo.model.PlaybookRule;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.PlaybookRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/playbooks")
public class PlaybookController {

    @Autowired
    private PlaybookRuleRepository repo;

    @Autowired
    private AttackSessionRepository sessionRepo;

    @Autowired
    private PlaybookExecutionService execService;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public List<PlaybookRule> getAll() {
        return repo.findAllByOrderByPriorityDescIdDesc();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlaybookRule> create(@RequestBody PlaybookRule rule) {
        PlaybookRule saved = repo.save(rule);
        auditLogService.log(AuditLog.AuditType.SETTINGS,
                "Playbook created: " + rule.getName());
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PlaybookRule> update(@PathVariable Long id,
                                               @RequestBody PlaybookRule rule) {
        return repo.findById(id).map(existing -> {
            existing.setName(rule.getName());
            existing.setTriggerType(rule.getTriggerType());
            existing.setTriggerValue(rule.getTriggerValue());
            existing.setCommands(rule.getCommands());
            existing.setTargetType(rule.getTargetType());
            existing.setTargetDevice(rule.getTargetDevice());
            existing.setPriority(rule.getPriority() != null ? rule.getPriority() : 1);
            existing.setAutoExecute(rule.getAutoExecute() != null ? rule.getAutoExecute() : false);
            existing.setEnabled(rule.getEnabled() != null ? rule.getEnabled() : true);
            existing.setDescription(rule.getDescription());
            PlaybookRule saved = repo.save(existing);
            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Playbook updated: " + rule.getName());
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (repo.existsById(id)) {
            repo.deleteById(id);
            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Playbook deleted (id=" + id + ")");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }


    @PostMapping("/run/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> runForSession(@PathVariable Long sessionId) {
        AttackSession session = sessionRepo.findById(sessionId).orElse(null);
        if (session == null) {
            return ResponseEntity.status(404).body(
                    Map.of("status", "error", "message", "Session not found"));
        }

        String result;
        try {
            result = execService.runMatchingFor(session);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("status", "error", "message", e.getMessage()));
        }

        if (result == null) {
            return ResponseEntity.ok(Map.of(
                    "status", "no_match",
                    "message", "No enabled playbook matches " + session.getAttackType(),
                    "sessionId", sessionId,
                    "attackType", session.getAttackType()
            ));
        }

        return ResponseEntity.ok(Map.of(
                "status", "executed",
                "playbook", result,
                "sessionId", sessionId,
                "attackType", session.getAttackType(),
                "message", "Executed: " + result
        ));
    }
}