package com.example.demo.Controller;

import com.example.demo.Service.MetricService;
import com.example.demo.dto.AiInput;
import com.example.demo.model.MetricData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricController {

    @Autowired
    private MetricService metricService;

    // ── Receive metrics from Python agent ─────────────────────────
    @PostMapping
    public ResponseEntity<String> receiveMetric(
            @RequestBody MetricData metric) {
        metricService.saveMetric(metric);
        return ResponseEntity.ok("Saved");
    }

    // ── Dashboard ─────────────────────────────────────────────────
    @GetMapping("/latest")
    public List<MetricData> getLatest() {
        return metricService.getLatest();
    }

    @GetMapping("/device/{name}")
    public List<MetricData> getByDevice(@PathVariable String name) {
        return metricService.getByDevice(name);
    }

    // 🆕 Historical metric chart for SidePanel "History" tab
    // GET /api/metrics/device/{name}/history?metric=cpuUsage&period=24h
    // GET /api/metrics/device/{name}/history?metric=cpuUsage&from=2026-04-20T14:00&to=2026-04-20T20:00
    //
    // Either "period" (preset) or "from"+"to" (custom range) can be used.
    // When "from" and "to" are both provided, they take precedence over "period".
    // Returns: {
    //   "metric": "cpuUsage", "period": "24h" or "custom",
    //   "from": "...", "to": "...",
    //   "points": [ { "timestamp": "...", "value": 12.5 }, ... ],
    //   "stats":  { "min": 2.1, "max": 85.3, "avg": 34.7, "count": 8640 }
    // }
    @GetMapping("/device/{name}/history")
    public Map<String, Object> getDeviceMetricHistory(
            @PathVariable String name,
            @RequestParam String metric,
            @RequestParam(defaultValue = "24h") String period,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            java.time.LocalDateTime fromDt = java.time.LocalDateTime.parse(from);
            java.time.LocalDateTime toDt   = java.time.LocalDateTime.parse(to);
            return metricService.getDeviceMetricHistoryRange(name, metric, fromDt, toDt, "custom");
        }
        return metricService.getDeviceMetricHistory(name, metric, period);
    }

    // 🆕 ARP table for SidePanel Properties tab.
    // Returns the ARP entries from the device's MOST RECENT metric only
    // (not a history) — that's the current Layer-2 neighbors snapshot.
    // Keeps the response small even though arp_entries table may be huge.
    //
    // GET /api/metrics/device/{name}/arp
    // Returns: [ {"ip": "10.0.1.1", "mac": "aa:bb:cc:dd:ee:01"}, ... ]
    @GetMapping("/device/{name}/arp")
    public List<Map<String, String>> getDeviceArpTable(@PathVariable String name) {
        return metricService.getLatestArpTable(name);
    }

    // ══════════════════════════════════════════════════════════════
    //  AI: Dataset
    // ══════════════════════════════════════════════════════════════

    // ── Dataset export as flat AiInput list ──────────────────────
    // GET /api/metrics/dataset           → all labeled records
    // GET /api/metrics/dataset?label=dos → only "dos" records
    //
    // Returns: List of AiInput (flat objects with ALL features joined)
    // Each AiInput = one interface + MetricData + TcpStats + SecurityMetric
    @GetMapping("/dataset")
    public List<AiInput> getDataset(
            @RequestParam(required = false) String label) {
        return metricService.getDataset(label);
    }

    // ── Dataset statistics ──────────────────────────────────────
    // GET /api/metrics/dataset/stats
    // Returns: { "normal": 150, "synflood": 45, "fault": 32, ... }
    @GetMapping("/dataset/stats")
    public ResponseEntity<Map<String, Long>> getDatasetStats() {
        return ResponseEntity.ok(metricService.getDatasetStats());
    }

    // ══════════════════════════════════════════════════════════════
    //  AI: Predictions (per interface)
    // ══════════════════════════════════════════════════════════════

    // ── Receive prediction from AI Engine ────────────────────────
    // POST /api/metrics/prediction
    // Body: {
    //   "interfaceMetricId": 4521,
    //   "predictedAttack": "synflood",
    //   "confidence": 0.94,
    //   "anomalyScore": -0.31,
    //   "faultProbability": 0.05
    // }
    //
    // AI Engine sends one prediction per interface.
    // Dashboard receives: device + interface + attack type
    @PostMapping("/prediction")
    public ResponseEntity<String> savePrediction(
            @RequestBody Map<String, Object> body) {
        metricService.savePrediction(
                toLong(body.get("interfaceMetricId")),
                (String) body.get("predictedAttack"),
                toDouble(body.get("confidence")),
                toDouble(body.get("anomalyScore")),
                toDouble(body.get("faultProbability")),
                (String) body.get("topFeatures")
        );
        return ResponseEntity.ok("Prediction saved");
    }

    // ── Latest predictions ──────────────────────────────────────
    // GET /api/metrics/predictions
    // Returns: [
    //   { "deviceName": "web-server", "interfaceName": "eth1",
    //     "predictedAttack": "synflood", "confidence": 0.94, ... },
    //   ...
    // ]
    @GetMapping("/predictions")
    public List<Map<String, Object>> getLatestPredictions() {
        return metricService.getLatestPredictions();
    }

    // ── Helpers ──────────────────────────────────────────────────
    private Double toDouble(Object val) {
        if (val == null) return null;
        return ((Number) val).doubleValue();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return ((Number) val).longValue();
    }
}