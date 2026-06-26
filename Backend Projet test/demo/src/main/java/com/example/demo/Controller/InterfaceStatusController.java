package com.example.demo.Controller;

import com.example.demo.model.InterfaceStatusLog;
import com.example.demo.repository.InterfaceStatusLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InterfaceStatusController — endpoints for interface UP/DOWN timeline.
 *
 * GET /api/interface-status/timeline/{device}?period=24h
 *   Returns overlap-aware logs so an UP period starting before the window
 *   still shows up (clipped to the window by the frontend).
 */
@RestController
@RequestMapping("/api/interface-status")
public class InterfaceStatusController {

    @Autowired
    private InterfaceStatusLogRepository logRepo;

    @GetMapping("/timeline/{deviceName}")
    public List<Map<String, Object>> getTimeline(
            @PathVariable String deviceName,
            @RequestParam(defaultValue = "24h") String period) {

        LocalDateTime after = parseAfter(period);
        // Use overlap query so long-running UP logs that started before the window
        // are still returned (e.g., an interface that's been UP for 3 days when
        // the user asks for "last 1h" — we still want to show that bar).
        List<InterfaceStatusLog> logs = logRepo.findOverlappingWindow(deviceName, after);

        return logs.stream().map(l -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", l.getId());
            m.put("deviceName", l.getDeviceName());
            m.put("interfaceName", l.getInterfaceName());
            m.put("status", l.getStatus().name());
            m.put("startedAt", l.getStartedAt() != null ? l.getStartedAt().toString() : null);
            m.put("endedAt", l.getEndedAt() != null ? l.getEndedAt().toString() : null);
            m.put("durationSeconds", l.getDurationSeconds());
            m.put("durationFormatted", l.getDurationFormatted());
            return m;
        }).toList();
    }

    private LocalDateTime parseAfter(String period) {
        LocalDateTime now = LocalDateTime.now();
        return switch (period) {
            case "1h"  -> now.minusHours(1);
            case "6h"  -> now.minusHours(6);
            case "7d"  -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default    -> now.minusHours(24);
        };
    }
}