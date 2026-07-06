package com.example.demo.Controller;

import com.example.demo.Service.AttackSessionService;
import com.example.demo.model.AttackSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attack-sessions")
public class AttackSessionController {

    @Autowired
    private AttackSessionService sessionService;

    @GetMapping("/active")
    public List<AttackSession> getActive() {
        return sessionService.getActiveSessions();
    }

    @GetMapping("/history")
    public List<AttackSession> getHistory(
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String period) {
        return sessionService.getHistory(device, period);
    }

    @GetMapping("/timeline")
    public List<AttackSession> getTimeline(
            @RequestParam(required = false) String period) {
        return sessionService.getTimelineSessions(period);
    }

    @GetMapping("/{id}/impact")
    public Map<String, Object> getImpact(@PathVariable Long id) {
        return sessionService.getImpact(id);
    }

    @GetMapping("/recent")
    public List<AttackSession> getRecent() {
        return sessionService.getRecentSessions();
    }

    @GetMapping("/unacknowledged")
    public List<AttackSession> getUnacknowledged() {
        return sessionService.getUnacknowledged();
    }

    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AttackSession> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.acknowledge(id));
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return sessionService.getStats();
    }

    @GetMapping("/downtime")
    public Map<String, Long> getDowntime(
            @RequestParam(required = false, defaultValue = "30d") String period) {
        return sessionService.getDowntime(period);
    }
}