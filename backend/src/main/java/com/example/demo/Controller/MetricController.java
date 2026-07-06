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

    @PostMapping
    public ResponseEntity<String> receiveMetric(
            @RequestBody MetricData metric) {
        metricService.saveMetric(metric);
        return ResponseEntity.ok("Saved");
    }

    @GetMapping("/latest")
    public List<MetricData> getLatest() {
        return metricService.getLatest();
    }

    @GetMapping("/device/{name}")
    public List<MetricData> getByDevice(@PathVariable String name) {
        return metricService.getByDevice(name);
    }


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


    @GetMapping("/device/{name}/arp")
    public List<Map<String, String>> getDeviceArpTable(@PathVariable String name) {
        return metricService.getLatestArpTable(name);
    }


    @GetMapping("/dataset")
    public List<AiInput> getDataset(
            @RequestParam(required = false) String label) {
        return metricService.getDataset(label);
    }


    @GetMapping("/dataset/stats")
    public ResponseEntity<Map<String, Long>> getDatasetStats() {
        return ResponseEntity.ok(metricService.getDatasetStats());
    }

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


    @GetMapping("/predictions")
    public List<Map<String, Object>> getLatestPredictions() {
        return metricService.getLatestPredictions();
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        return ((Number) val).doubleValue();
    }

    private Long toLong(Object val) {
        if (val == null) return null;
        return ((Number) val).longValue();
    }
}