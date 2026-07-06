package com.example.demo.Service;

import com.example.demo.dto.AiInput;
import com.example.demo.model.*;
import com.example.demo.repository.AlertRuleRepository;
import com.example.demo.repository.AttackSessionRepository;
import com.example.demo.repository.DeviceStatusLogRepository;
import com.example.demo.repository.InterfaceMetricRepository;
import com.example.demo.repository.MetricRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MetricService {

    @Autowired
    private DeviceStatusService deviceStatusService;

    @Autowired
    private DeviceStatusLogRepository deviceStatusLogRepository;

    @Autowired
    private AttackSessionService attackSessionService;

    @Autowired
    private MetricRepository metricRepository;

    @Autowired
    private InterfaceMetricRepository interfaceMetricRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private LabelingService labelingService;

    @Autowired
    private AlertRuleRepository alertRuleRepository;

    @Autowired
    private AttackSessionRepository attackSessionRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private InterfaceStatusService interfaceStatusService;

    public void saveMetric(MetricData metric) {

        metric.setTimestamp(LocalDateTime.now());

        if (metric.getInterfaces() != null) {
            for (InterfaceMetric iface : metric.getInterfaces()) {
                iface.setMetricData(metric);
            }
        }
        if (metric.getTcpStats() != null) {
            metric.getTcpStats().setMetricData(metric);
        }
        if (metric.getSecurityMetric() != null) {
            metric.getSecurityMetric().setMetricData(metric);
        }
        if (metric.getArpTable() != null) {
            for (ArpEntry arp : metric.getArpTable()) {
                arp.setMetricData(metric);
            }
        }

        if (labelingService.isLabeling() && metric.getInterfaces() != null) {
            String deviceName = metric.getDeviceName();
            for (InterfaceMetric iface : metric.getInterfaces()) {
                String label = labelingService.resolveLabel(
                        deviceName, iface.getInterfaceName());
                if (label != null) {
                    iface.setAttackLabel(label);
                }
            }
        }

        metricRepository.save(metric);
        deviceStatusService.recordHeartbeat(metric.getDeviceName());
        evaluateTriggerRules(metric);
        interfaceStatusService.processMetric(metric);
        messagingTemplate.convertAndSend("/topic/metrics", (Object) metric);
    }

    public List<MetricData> getLatest() {
        return metricRepository.findTop50ByOrderByTimestampDesc();
    }

    public List<MetricData> getByDevice(String deviceName) {
        return metricRepository
                .findTop20ByDeviceNameOrderByTimestampDesc(deviceName);
    }

    public List<AiInput> getDataset(String label) {
        List<InterfaceMetric> interfaces;
        if (label != null && !label.isBlank()) {
            interfaces = interfaceMetricRepository.findByAttackLabel(label);
        } else {
            interfaces = interfaceMetricRepository.findByAttackLabelIsNotNull();
        }
        List<AiInput> dataset = new ArrayList<>();
        for (InterfaceMetric iface : interfaces) {
            AiInput input = buildAiInput(iface);
            if (input != null) dataset.add(input);
        }
        return dataset;
    }

    public Map<String, Long> getDatasetStats() {
        Map<String, Long> stats = new HashMap<>();
        List<Object[]> rows = interfaceMetricRepository.countByAttackLabel();
        for (Object[] row : rows) {
            stats.put(
                    row[0] != null ? (String) row[0] : "unlabeled",
                    (Long) row[1]
            );
        }
        return stats;
    }

    @Transactional
    public void savePrediction(Long interfaceMetricId,
                               String predictedAttack,
                               Double confidence,
                               Double anomalyScore,
                               Double faultProbability,
                               String topFeatures) {

        interfaceMetricRepository.findById(interfaceMetricId).ifPresent(iface -> {

            AiResult aiResult = iface.getAiResult();
            if (aiResult == null) {
                aiResult = new AiResult();
                aiResult.setInterfaceMetric(iface);
                iface.setAiResult(aiResult);
            }
            aiResult.setPredictedAttack(predictedAttack);
            aiResult.setPredictionConfidence(confidence);
            aiResult.setAnomalyScore(anomalyScore);
            aiResult.setFaultProbability(faultProbability);
            aiResult.setTopFeatures(topFeatures);
            interfaceMetricRepository.save(iface);

            String deviceName = iface.getMetricData().getDeviceName();
            String interfaceName = iface.getInterfaceName();

            if (predictedAttack != null && !predictedAttack.equals("normal")) {
                try {
                    String topAttackersJson = null;
                    if (iface.getMetricData().getSecurityMetric() != null) {
                        topAttackersJson = iface.getMetricData().getSecurityMetric().getTopAttackersDetail();
                    }

                    attackSessionService.processAttackPrediction(
                            deviceName, interfaceName, predictedAttack,
                            confidence, topFeatures, null, topAttackersJson);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            Map<String, Object> prediction = new HashMap<>();
            prediction.put("deviceName", deviceName);
            prediction.put("interfaceName", interfaceName);
            prediction.put("predictedAttack", predictedAttack);
            prediction.put("confidence", confidence);
            prediction.put("anomalyScore", anomalyScore);
            prediction.put("faultProbability", faultProbability);
            prediction.put("topFeatures", topFeatures);
            messagingTemplate.convertAndSend("/topic/predictions", (Object) prediction);
        });
    }

    public List<Map<String, Object>> getLatestPredictions() {
        List<InterfaceMetric> interfaces = interfaceMetricRepository
                .findTop100ByAiResultPredictedAttackIsNotNullOrderByIdDesc();

        Map<String, Map<String, Object>> latestPerInterface = new HashMap<>();

        for (InterfaceMetric iface : interfaces) {
            String key = iface.getMetricData().getDeviceName() + "::" + iface.getInterfaceName();
            if (latestPerInterface.containsKey(key)) continue;

            Map<String, Object> entry = new HashMap<>();
            entry.put("deviceName", iface.getMetricData().getDeviceName());
            entry.put("interfaceName", iface.getInterfaceName());
            entry.put("predictedAttack", iface.getAiResult().getPredictedAttack());
            entry.put("confidence", iface.getAiResult().getPredictionConfidence());
            entry.put("anomalyScore", iface.getAiResult().getAnomalyScore());
            entry.put("faultProbability", iface.getAiResult().getFaultProbability());
            entry.put("topFeatures", iface.getAiResult().getTopFeatures());

            latestPerInterface.put(key, entry);
        }

        return new ArrayList<>(latestPerInterface.values());
    }

    public AiInput buildAiInput(InterfaceMetric iface) {
        MetricData metric = iface.getMetricData();
        if (metric == null) return null;

        AiInput input = new AiInput();
        input.setInterfaceMetricId(iface.getId());

        input.setCpuUsage(metric.getCpuUsage());
        input.setMemoryUsage(metric.getMemoryUsage());
        input.setConnections(metric.getConnections());

        input.setThroughputOut(iface.getThroughputOut());
        input.setThroughputIn(iface.getThroughputIn());
        input.setPacketsPerSecSent(iface.getPacketsPerSecSent());
        input.setPacketsPerSecRecv(iface.getPacketsPerSecRecv());
        input.setErrorRate(iface.getErrorRate());
        input.setDropRate(iface.getDropRate());
        input.setLatency(iface.getLatency());
        input.setPacketLoss(iface.getPacketLoss());
        input.setJitter(iface.getJitter());
        input.setBytesPerPacketOut(iface.getBytesPerPacketOut());
        input.setBytesPerPacketIn(iface.getBytesPerPacketIn());
        input.setBandwidthUtilOut(iface.getBandwidthUtilOut());
        input.setBandwidthUtilIn(iface.getBandwidthUtilIn());

        TcpStats tcp = metric.getTcpStats();
        if (tcp != null) {
            input.setPassiveOpens(tcp.getPassiveOpens());
            input.setActiveOpens(tcp.getActiveOpens());
            input.setAttemptFails(tcp.getAttemptFails());
            input.setEstabResets(tcp.getEstabResets());
            input.setCurrEstab(tcp.getCurrEstab());
            input.setInSegs(tcp.getInSegs());
            input.setOutSegs(tcp.getOutSegs());
            input.setInErrs(tcp.getInErrs());
            input.setOutRsts(tcp.getOutRsts());
            input.setTcpRetransmissions(tcp.getTcpRetransmissions());
            input.setIcmpInMsgs(tcp.getIcmpInMsgs());
            input.setIcmpInErrors(tcp.getIcmpInErrors());
            input.setUdpInDatagrams(tcp.getUdpInDatagrams());
            input.setUdpOutDatagrams(tcp.getUdpOutDatagrams());
            input.setUdpInErrors(tcp.getUdpInErrors());
            input.setUdpNoPorts(tcp.getUdpNoPorts());
            input.setPassiveOpensRate(tcp.getPassiveOpensRate());
            input.setActiveOpensRate(tcp.getActiveOpensRate());
            input.setAttemptFailsRate(tcp.getAttemptFailsRate());
            input.setEstabResetsRate(tcp.getEstabResetsRate());
            input.setOutRstsRate(tcp.getOutRstsRate());
            input.setInSegsRate(tcp.getInSegsRate());
            input.setOutSegsRate(tcp.getOutSegsRate());
            input.setRetransRate(tcp.getRetransRate());
            input.setIcmpInRate(tcp.getIcmpInRate());
            input.setInOutSegRatio(tcp.getInOutSegRatio());
            input.setRstPerConnection(tcp.getRstPerConnection());
            input.setUdpInRate(tcp.getUdpInRate());
            input.setUdpOutRate(tcp.getUdpOutRate());
            input.setUdpErrorRate(tcp.getUdpErrorRate());
            input.setUdpNoPortRate(tcp.getUdpNoPortRate());
            input.setUdpInOutRatio(tcp.getUdpInOutRatio());
            input.setRoutingTableSize(tcp.getRoutingTableSize());
            input.setForwardedPackets(tcp.getForwardedPackets());
            input.setInDiscards(tcp.getInDiscards());
            input.setNoRoutePackets(tcp.getNoRoutePackets());
        }

        SecurityMetric sec = metric.getSecurityMetric();
        if (sec != null) {
            input.setHalfOpenConnections(sec.getHalfOpenConnections());
            input.setUniqueSourceIPs(sec.getUniqueSourceIPs());
            input.setFailedLogins(sec.getFailedLogins());
            input.setFailedLoginsRate(sec.getFailedLoginsRate());
            input.setSshConnectionAttempts(sec.getSshConnectionAttempts());
            input.setTopAttackerRepeat(sec.getTopAttackerRepeat());
            input.setUniqueDestinationPorts(sec.getUniqueDestinationPorts());
            input.setSensitivePortsHit(sec.getSensitivePortsHit());
            input.setTimeWaitConnections(sec.getTimeWaitConnections());
            input.setAvgConnectionDuration(sec.getAvgConnectionDuration());
            input.setMaxConnectionDuration(sec.getMaxConnectionDuration());
            input.setLongConnectionRatio(sec.getLongConnectionRatio());
        }

        input.setAttackLabel(iface.getAttackLabel());
        return input;
    }

    private void evaluateTriggerRules(MetricData metric) {
        List<AlertRule> rules = alertRuleRepository.findByEnabledTrue();
        if (rules.isEmpty()) return;

        String deviceName = metric.getDeviceName();

        boolean aiHandling = attackSessionRepository
                .findByDeviceNameAndStatus(deviceName, AttackSession.SessionStatus.ACTIVE)
                .stream()
                .anyMatch(s -> s.getSessionSource() == null
                        || s.getSessionSource() == AttackSession.SessionSource.AI);

        for (AlertRule rule : rules) {

            if (!rule.appliesToDevice(deviceName)) {
                continue;
            }

            Double actualValue = extractMetricValue(metric, rule.getMetric());
            if (actualValue == null) continue;

            boolean fired = false;
            switch (rule.getConditionOp()) {
                case ">" -> fired = actualValue > rule.getValue();
                case ">=" -> fired = actualValue >= rule.getValue();
                case "<" -> fired = actualValue < rule.getValue();
                case "<=" -> fired = actualValue <= rule.getValue();
            }

            if (fired) {

                attackSessionService.processTriggerRuleFired(rule, deviceName, actualValue);
            }
        }
    }

    private Double extractMetricValue(MetricData metric, String metricName) {
        try {
            return switch (metricName) {
                case "cpuUsage" -> metric.getCpuUsage();
                case "memoryUsage" -> metric.getMemoryUsage();
                case "diskUsage" -> metric.getDiskUsage();
                case "diskTotalGb" -> metric.getDiskTotalGb();
                case "diskUsedGb" -> metric.getDiskUsedGb();
                case "diskFreeGb" -> metric.getDiskFreeGb();
                case "connections" -> metric.getConnections() != null
                        ? metric.getConnections().doubleValue() : null;
                default -> {
                    if (metric.getTcpStats() != null) {
                        try {
                            var field = metric.getTcpStats().getClass().getDeclaredField(metricName);
                            field.setAccessible(true);
                            Object val = field.get(metric.getTcpStats());
                            if (val instanceof Number) yield ((Number) val).doubleValue();
                        } catch (NoSuchFieldException ignored) {}
                    }
                    if (metric.getSecurityMetric() != null) {
                        try {
                            var field = metric.getSecurityMetric().getClass().getDeclaredField(metricName);
                            field.setAccessible(true);
                            Object val = field.get(metric.getSecurityMetric());
                            if (val instanceof Number) yield ((Number) val).doubleValue();
                        } catch (NoSuchFieldException ignored) {}
                    }

                    if (metric.getInterfaces() != null && !metric.getInterfaces().isEmpty()) {
                        Double maxVal = null;
                        for (InterfaceMetric iface : metric.getInterfaces()) {
                            try {
                                var field = iface.getClass().getDeclaredField(metricName);
                                field.setAccessible(true);
                                Object val = field.get(iface);
                                if (val instanceof Number) {
                                    double v = ((Number) val).doubleValue();
                                    if (maxVal == null || v > maxVal) maxVal = v;
                                }
                            } catch (NoSuchFieldException ignored) { break; }
                        }
                        if (maxVal != null) yield maxVal;
                    }
                    yield null;
                }
            };
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, Object> getDeviceMetricHistory(String deviceName,
                                                      String metricName,
                                                      String period) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime after = switch (period) {
            case "1h"  -> now.minusHours(1);
            case "6h"  -> now.minusHours(6);
            case "7d"  -> now.minusDays(7);
            case "30d" -> now.minusDays(30);
            default    -> now.minusHours(24);
        };
        return getDeviceMetricHistoryRange(deviceName, metricName, after, now, period);
    }

    public Map<String, Object> getDeviceMetricHistoryRange(String deviceName,
                                                           String metricName,
                                                           LocalDateTime from,
                                                           LocalDateTime to,
                                                           String periodLabel) {

        List<MetricData> rows = metricRepository
                .findByDeviceNameAndTimestampBetweenOrderByTimestampAsc(deviceName, from, to);

        List<double[]> raw = new ArrayList<>();

        List<LocalDateTime> timestamps = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        double sum = 0.0;
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        int count = 0;

        for (MetricData m : rows) {
            Double v = extractMetricValue(m, metricName);
            if (v == null) continue;
            timestamps.add(m.getTimestamp());
            values.add(v);
            sum += v;
            if (v < min) min = v;
            if (v > max) max = v;
            count++;
        }

        final int TARGET_POINTS = 200;
        List<Map<String, Object>> points = new ArrayList<>();

        if (values.size() <= TARGET_POINTS) {

            for (int i = 0; i < values.size(); i++) {
                Map<String, Object> p = new HashMap<>();
                p.put("timestamp", timestamps.get(i).toString());
                p.put("value", values.get(i));
                points.add(p);
            }
        } else {

            int bucketSize = (int) Math.ceil((double) values.size() / TARGET_POINTS);
            for (int bucketStart = 0; bucketStart < values.size(); bucketStart += bucketSize) {
                int bucketEnd = Math.min(bucketStart + bucketSize, values.size());
                double bucketMax = -Double.MAX_VALUE;
                int maxIdx = bucketStart;
                for (int j = bucketStart; j < bucketEnd; j++) {
                    if (values.get(j) > bucketMax) {
                        bucketMax = values.get(j);
                        maxIdx = j;
                    }
                }
                Map<String, Object> p = new HashMap<>();
                p.put("timestamp", timestamps.get(maxIdx).toString());
                p.put("value", bucketMax);
                points.add(p);
            }
        }

        Map<String, Object> stats = new HashMap<>();
        if (count > 0) {
            stats.put("min", min);
            stats.put("max", max);
            stats.put("avg", sum / count);
            stats.put("count", count);
        } else {
            stats.put("min", 0);
            stats.put("max", 0);
            stats.put("avg", 0);
            stats.put("count", 0);
        }

        Map<String, Object> resp = new HashMap<>();
        resp.put("metric", metricName);
        resp.put("period", periodLabel);
        resp.put("from", from.toString());
        resp.put("to", to.toString());
        resp.put("points", points);
        resp.put("stats", stats);

        return resp;
    }

    public List<Map<String, String>> getLatestArpTable(String deviceName) {
        List<MetricData> recent = metricRepository
                .findTop20ByDeviceNameOrderByTimestampDesc(deviceName);
        if (recent == null || recent.isEmpty()) return List.of();

        MetricData latest = recent.get(0);
        if (latest.getArpTable() == null || latest.getArpTable().isEmpty()) {
            return List.of();
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (var arp : latest.getArpTable()) {
            Map<String, String> entry = new HashMap<>();
            entry.put("ip", arp.getIp() == null ? "" : arp.getIp());
            entry.put("mac", arp.getMac() == null ? "" : arp.getMac());
            result.add(entry);
        }
        return result;
    }
}
