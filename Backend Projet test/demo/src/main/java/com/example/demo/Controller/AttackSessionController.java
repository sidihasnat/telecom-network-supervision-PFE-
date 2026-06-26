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

    // ── Live Tab: sessions actives ────────────────────────────
    // GET /api/attack-sessions/active
    @GetMapping("/active")
    public List<AttackSession> getActive() {
        return sessionService.getActiveSessions();
    }

    // ── History Tab: sessions terminées ───────────────────────
    // GET /api/attack-sessions/history
    // GET /api/attack-sessions/history?device=web-server
    // GET /api/attack-sessions/history?period=24h
    // GET /api/attack-sessions/history?device=web-server&period=1h
    @GetMapping("/history")
    public List<AttackSession> getHistory(
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String period) {
        return sessionService.getHistory(device, period);
    }

    // Home "Problem Timeline" needs active + ended, overlap-aware.
    // Separate from /history so Security page's History tab stays ended-only.
    // GET /api/attack-sessions/timeline?period=6h
    @GetMapping("/timeline")
    public List<AttackSession> getTimeline(
            @RequestParam(required = false) String period) {
        return sessionService.getTimelineSessions(period);
    }

    // AttackDetailPanel uses this to render the side drawer when a row is
    // clicked in History. Returns the session + matching audit events +
    // protection actions + a window of metrics around the attack.
    // GET /api/attack-sessions/{id}/impact
    @GetMapping("/{id}/impact")
    public Map<String, Object> getImpact(@PathVariable Long id) {
        return sessionService.getImpact(id);
    }

    // ── Home: آخر 10 sessions ────────────────────────────────
    // GET /api/attack-sessions/recent
    @GetMapping("/recent")
    public List<AttackSession> getRecent() {
        return sessionService.getRecentSessions();
    }

    // ── Notifications: غير معترف بها ──────────────────────────
    // GET /api/attack-sessions/unacknowledged
    @GetMapping("/unacknowledged")
    public List<AttackSession> getUnacknowledged() {
        return sessionService.getUnacknowledged();
    }

    // ── Acknowledge ──────────────────────────────────────────
    // POST /api/attack-sessions/5/acknowledge
    @PostMapping("/{id}/acknowledge")
    public ResponseEntity<AttackSession> acknowledge(@PathVariable Long id) {
        return ResponseEntity.ok(sessionService.acknowledge(id));
    }

    // ── Stats ────────────────────────────────────────────────
    // GET /api/attack-sessions/stats
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        return sessionService.getStats();
    }

    // ── SLA: downtime per device ─────────────────────────────
    // GET /api/attack-sessions/downtime?period=24h
    @GetMapping("/downtime")
    public Map<String, Long> getDowntime(
            @RequestParam(required = false, defaultValue = "30d") String period) {
        return sessionService.getDowntime(period);
    }
}