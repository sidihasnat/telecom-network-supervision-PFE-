package com.example.demo.Controller;

import com.example.demo.Service.DeviceStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;


@RestController
@RequestMapping("/api/device-status")
public class DeviceStatusController {

    @Autowired
    private DeviceStatusService deviceStatusService;


    @GetMapping("/timeline")
    public List<Map<String, Object>> getTimeline(
            @RequestParam(defaultValue = "6h") String period) {
        return deviceStatusService.getTimeline(period);
    }

    @GetMapping("/downtime")
    public Map<String, Long> getOfflineDowntime(
            @RequestParam(defaultValue = "24h") String period) {
        return deviceStatusService.getOfflineDowntime(period);
    }

    @GetMapping("/offline")
    public List<String> getOfflineDevices() {
        return deviceStatusService.getCurrentlyOfflineDevices();
    }
}
