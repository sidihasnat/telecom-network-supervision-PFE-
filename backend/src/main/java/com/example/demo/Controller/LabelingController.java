package com.example.demo.Controller;

import com.example.demo.Service.LabelingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/label")
public class LabelingController {

    @Autowired
    private LabelingService labelingService;

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
            labelingService.setInterfaceLabel(deviceName, interfaceName, label);
            System.out.println("🏷Label started: " + deviceName + "::" + interfaceName + " → " + label);
            return ResponseEntity.ok(Map.of(
                    "status",        "labeling_started",
                    "label",         label,
                    "deviceName",    deviceName,
                    "interfaceName", interfaceName
            ));
        } else {
            labelingService.setDeviceLabel(deviceName, label);
            System.out.println("🏷️  Label started: " + deviceName + " → " + label);
            return ResponseEntity.ok(Map.of(
                    "status",     "labeling_started",
                    "label",      label,
                    "deviceName", deviceName
            ));
        }
    }


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

    @PostMapping("/stop-all")
    public ResponseEntity<Map<String, String>> stopAll() {
        labelingService.clearAllLabels();
        System.out.println("🏷️  All labels cleared");
        return ResponseEntity.ok(Map.of("status", "all_labels_cleared"));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getLabelStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("deviceLabels",    labelingService.getAllDeviceLabels());
        response.put("interfaceLabels", labelingService.getAllInterfaceLabels());
        response.put("isLabeling",      labelingService.isLabeling());
        return ResponseEntity.ok(response);
    }
}