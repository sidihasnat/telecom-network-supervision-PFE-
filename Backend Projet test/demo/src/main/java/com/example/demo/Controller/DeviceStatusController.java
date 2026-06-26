package com.example.demo.Controller;

import com.example.demo.Service.DeviceStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * DeviceStatusController — endpoints لحالة الأجهزة.
 *
 * GET /api/device-status/timeline?period=6h     → لـ Problem Timeline
 * GET /api/device-status/downtime?period=24h    → لـ SLA (offline فقط)
 * GET /api/device-status/offline                → الأجهزة المطفأة حالياً
 */
@RestController
@RequestMapping("/api/device-status")
public class DeviceStatusController {

    @Autowired
    private DeviceStatusService deviceStatusService;

    /**
     * Timeline — all status change events for visualization.
     * Used by Problem Timeline in Home page.
     */
    @GetMapping("/timeline")
    public List<Map<String, Object>> getTimeline(
            @RequestParam(defaultValue = "6h") String period) {
        return deviceStatusService.getTimeline(period);
    }

    /**
     * Offline downtime per device (seconds).
     * Used by SLA tab — add to attack downtime for total.
     */
    @GetMapping("/downtime")
    public Map<String, Long> getOfflineDowntime(
            @RequestParam(defaultValue = "24h") String period) {
        return deviceStatusService.getOfflineDowntime(period);
    }

    /**
     * Currently offline devices.
     */
    @GetMapping("/offline")
    public List<String> getOfflineDevices() {
        return deviceStatusService.getCurrentlyOfflineDevices();
    }
}
