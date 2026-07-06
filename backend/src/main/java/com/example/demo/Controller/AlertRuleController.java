package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.model.AlertRule;
import com.example.demo.model.AuditLog;
import com.example.demo.repository.AlertRuleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
public class AlertRuleController {

    @Autowired
    private AlertRuleRepository ruleRepo;

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public List<AlertRule> getAll() {
        return ruleRepo.findAll();
    }

    @PostMapping
    public ResponseEntity<AlertRule> create(@RequestBody AlertRule rule) {
        AlertRule saved = ruleRepo.save(rule);
        auditLogService.log(AuditLog.AuditType.SETTINGS,
                "Trigger rule created: IF " + rule.getMetric() + " "
                        + rule.getConditionOp() + " " + rule.getValue()
                        + " → " + rule.getSeverity()
                        + scopeSuffix(rule));
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AlertRule> update(@PathVariable Long id,
                                            @RequestBody AlertRule rule) {
        return ruleRepo.findById(id).map(existing -> {
            existing.setMetric(rule.getMetric());
            existing.setConditionOp(rule.getConditionOp());
            existing.setValue(rule.getValue());
            existing.setSeverity(rule.getSeverity());
            existing.setDeviceNames(rule.getDeviceNames());
            existing.setEnabled(rule.getEnabled());
            existing.setName(rule.getName());
            AlertRule saved = ruleRepo.save(existing);

            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Trigger rule updated: IF " + rule.getMetric() + " "
                            + rule.getConditionOp() + " " + rule.getValue()
                            + scopeSuffix(rule));
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (ruleRepo.existsById(id)) {
            ruleRepo.deleteById(id);
            auditLogService.log(AuditLog.AuditType.SETTINGS,
                    "Trigger rule deleted (id=" + id + ")");
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    private String scopeSuffix(AlertRule rule) {
        List<String> devs = rule.getDeviceList();
        if (devs.isEmpty()) return " (scope: all devices)";
        return " (scope: " + String.join(", ", devs) + ")";
    }
}