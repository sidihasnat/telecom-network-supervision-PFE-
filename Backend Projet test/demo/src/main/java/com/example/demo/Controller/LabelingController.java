package com.example.demo.Controller;

import com.example.demo.Service.LabelingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

/**
 * Controls the "labeling mode" during dataset collection.
 *
 * API:
 *   POST /api/label/start  { "label": "bruteforce", "deviceName": "ssh-server" }
 *   POST /api/label/start  { "label": "fault", "deviceName": "edge-router", "interfaceName": "br-wan" }
 *   POST /api/label/stop   { "deviceName": "ssh-server" }
 *   POST /api/label/stop   { "deviceName": "edge-router", "interfaceName": "br-wan" }
 *   POST /api/label/stop-all
 *   GET  /api/label/status
 *
 * Labels:
 *   normal | dos | ddos | synflood | portscan | bruteforce | fault | transit
 */
@RestController
@RequestMapping("/api/label")
public class LabelingController {

    @Autowired
    private LabelingService labelingService;

    // ── Start labeling ────────────────────────────────────────────
    // Body: {
    //   "label"        : "bruteforce",   (required)
    //   "deviceName"   : "ssh-server",   (required)
    //   "interfaceName": "br-wan"         (optional — for interface-level)
    // }
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startLabeling(
            @RequestBody Map<String, String> body) {

        String label         = body.get("label");
        String deviceName    = body.get("deviceName");
        String interfaceName = body.get("interfaceName");

        if (label == null || label.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "label is required"));
        }
        if (deviceName == null || deviceName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "deviceName is required"));
        }

        if (interfaceName != null && !interfaceName.isBlank()) {
            // Interface-level label
            labelingService.setInterfaceLabel(deviceName, interfaceName, label);
            System.out.println("🏷Label started: " + deviceName + "::" + interfaceName + " → " + label);
            return ResponseEntity.ok(Map.of(
                    "status",        "labeling_started",
                    "label",         label,
                    "deviceName",    deviceName,
                    "interfaceName", interfaceName
            ));
        } else {
            // Device-level label
            labelingService.setDeviceLabel(deviceName, label);
            System.out.println("🏷️  Label started: " + deviceName + " → " + label);
            return ResponseEntity.ok(Map.of(
                    "status",     "labeling_started",
                    "label",      label,
                    "deviceName", deviceName
            ));
        }
    }

    // ── Stop labeling ─────────────────────────────────────────────
    // Body: {
    //   "deviceName"   : "ssh-server",  (required)
    //   "interfaceName": "br-wan"        (optional)
    // }
    @PostMapping("/stop")
    public ResponseEntity<Map<String, String>> stopLabeling(
            @RequestBody Map<String, String> body) {

        String deviceName    = body.get("deviceName");
        String interfaceName = body.get("interfaceName");

        if (deviceName == null || deviceName.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "deviceName is required"));
        }

        if (interfaceName != null && !interfaceName.isBlank()) {
            labelingService.clearInterfaceLabel(deviceName, interfaceName);
            System.out.println("🏷️  Label stopped: " + deviceName + "::" + interfaceName);
        } else {
            labelingService.clearDeviceLabel(deviceName);
            System.out.println("🏷️  Label stopped: " + deviceName);
        }

        return ResponseEntity.ok(Map.of(
                "status",     "labeling_stopped",
                "deviceName", deviceName
        ));
    }

    // ── Stop ALL labels ───────────────────────────────────────────
    @PostMapping("/stop-all")
    public ResponseEntity<Map<String, String>> stopAll() {
        labelingService.clearAllLabels();
        System.out.println("🏷️  All labels cleared");
        return ResponseEntity.ok(Map.of("status", "all_labels_cleared"));
    }

    // ── Status ────────────────────────────────────────────────────
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLabelStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("deviceLabels",    labelingService.getAllDeviceLabels());
        response.put("interfaceLabels", labelingService.getAllInterfaceLabels());
        response.put("isLabeling",      labelingService.isLabeling());
        return ResponseEntity.ok(response);
    }
}