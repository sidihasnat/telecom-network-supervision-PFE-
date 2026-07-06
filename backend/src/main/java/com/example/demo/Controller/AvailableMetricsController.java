package com.example.demo.Controller;

import com.example.demo.model.MetricData;
import com.example.demo.model.SecurityMetric;
import com.example.demo.model.TcpStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.util.*;

@RestController
@RequestMapping("/api/alert-rules")
public class AvailableMetricsController {

    private static final Set<String> DIRECT_METRIC_DATA_FIELDS = Set.of(
            "cpuUsage", "memoryUsage", "connections",
            "diskUsage", "diskTotalGb", "diskUsedGb", "diskFreeGb"
    );

    private static final Map<String, String> LABELS = Map.ofEntries(
            // MetricData
            Map.entry("cpuUsage", "CPU Usage"),
            Map.entry("memoryUsage", "Memory Usage"),
            Map.entry("connections", "Active Connections"),
            Map.entry("diskUsage", "Disk Usage"),
            Map.entry("diskTotalGb", "Disk Total"),
            Map.entry("diskUsedGb", "Disk Used"),
            Map.entry("diskFreeGb", "Disk Free"),

            Map.entry("passiveOpensRate", "SYN Rate (incoming)"),
            Map.entry("activeOpensRate", "Active Connection Rate (outgoing)"),
            Map.entry("retransRate", "TCP Retransmissions Rate"),
            Map.entry("icmpInRate", "ICMP Packets Rate"),
            Map.entry("attemptFailsRate", "Failed Connection Attempts Rate"),
            Map.entry("estabResetsRate", "Connection Reset Rate"),
            Map.entry("outRstsRate", "Outgoing RST Rate"),
            Map.entry("inSegsRate", "Incoming TCP Segments Rate"),
            Map.entry("outSegsRate", "Outgoing TCP Segments Rate"),
            Map.entry("udpInRate", "UDP Packets Received Rate"),
            Map.entry("udpOutRate", "UDP Packets Sent Rate"),
            Map.entry("udpErrorRate", "UDP Error Rate"),
            Map.entry("udpNoPortRate", "UDP No-Port Rate"),

            Map.entry("inOutSegRatio", "In/Out Segment Ratio"),
            Map.entry("rstPerConnection", "RST per Connection"),
            Map.entry("udpInOutRatio", "UDP In/Out Ratio"),

            Map.entry("currEstab", "Currently Established Connections"),
            Map.entry("routingTableSize", "Routing Table Size"),
            Map.entry("forwardedPackets", "Forwarded Packets"),
            Map.entry("inDiscards", "Incoming Discards"),
            Map.entry("noRoutePackets", "No-Route Packets"),

            Map.entry("halfOpenConnections", "Half-Open Connections"),
            Map.entry("uniqueSourceIPs", "Unique Source IPs"),
            Map.entry("failedLogins", "Failed Logins (total)"),
            Map.entry("failedLoginsRate", "Failed Logins Rate"),
            Map.entry("sshConnectionAttempts", "SSH Connection Attempts"),
            Map.entry("topAttackerRepeat", "Top Attacker Request Count"),
            Map.entry("uniqueDestinationPorts", "Unique Destination Ports"),
            Map.entry("timeWaitConnections", "TIME_WAIT Connections"),
            Map.entry("sensitivePortsHit", "Sensitive Ports Hit"),
            Map.entry("avgConnectionDuration", "Avg Connection Duration"),
            Map.entry("maxConnectionDuration", "Max Connection Duration"),
            Map.entry("longConnectionRatio", "Long Connection Ratio")
    );

    private static final Map<String, String> UNITS = Map.ofEntries(
            Map.entry("cpuUsage", "%"),
            Map.entry("memoryUsage", "%"),
            Map.entry("diskUsage", "%"),
            Map.entry("diskTotalGb", "GB"),
            Map.entry("diskUsedGb", "GB"),
            Map.entry("diskFreeGb", "GB"),
            Map.entry("passiveOpensRate", "/s"),
            Map.entry("activeOpensRate", "/s"),
            Map.entry("retransRate", "/s"),
            Map.entry("icmpInRate", "/s"),
            Map.entry("attemptFailsRate", "/s"),
            Map.entry("estabResetsRate", "/s"),
            Map.entry("outRstsRate", "/s"),
            Map.entry("inSegsRate", "/s"),
            Map.entry("outSegsRate", "/s"),
            Map.entry("udpInRate", "/s"),
            Map.entry("udpOutRate", "/s"),
            Map.entry("udpErrorRate", "/s"),
            Map.entry("udpNoPortRate", "/s"),
            Map.entry("failedLoginsRate", "/s"),
            Map.entry("avgConnectionDuration", "s"),
            Map.entry("maxConnectionDuration", "s")
    );


    @GetMapping("/available-metrics")
    public Map<String, List<MetricInfo>> getAvailableMetrics() {
        Map<String, List<MetricInfo>> result = new LinkedHashMap<>();
        List<MetricInfo> systemMetrics = new ArrayList<>();
        for (String name : DIRECT_METRIC_DATA_FIELDS) {
            if (hasField(MetricData.class, name)) {
                systemMetrics.add(build(name));
            }
        }
        result.put("system", systemMetrics);

        List<MetricInfo> tcpMetrics = extractNumericFields(TcpStats.class);
        result.put("tcp", tcpMetrics);

        List<MetricInfo> securityMetrics = extractNumericFields(SecurityMetric.class);
        result.put("security", securityMetrics);

        return result;
    }


    private List<MetricInfo> extractNumericFields(Class<?> clazz) {
        List<MetricInfo> metrics = new ArrayList<>();
        for (Field field : clazz.getDeclaredFields()) {
            String name = field.getName();
            Class<?> type = field.getType();

            boolean isNumeric = type == Integer.class || type == int.class
                    || type == Long.class || type == long.class
                    || type == Double.class || type == double.class
                    || type == Float.class || type == float.class;

            if (!isNumeric) continue;
            if (name.equals("id")) continue;

            metrics.add(build(name));
        }

        metrics.sort(Comparator.comparing(m -> m.name));
        return metrics;
    }

    private MetricInfo build(String name) {
        MetricInfo info = new MetricInfo();
        info.name = name;
        info.label = LABELS.getOrDefault(name, toTitleCase(name));
        info.unit = UNITS.getOrDefault(name, "");
        return info;
    }

    private String toTitleCase(String camelCase) {
        StringBuilder sb = new StringBuilder();
        sb.append(Character.toUpperCase(camelCase.charAt(0)));
        for (int i = 1; i < camelCase.length(); i++) {
            char c = camelCase.charAt(i);
            if (Character.isUpperCase(c)) sb.append(' ');
            sb.append(c);
        }
        return sb.toString();
    }

    private boolean hasField(Class<?> clazz, String fieldName) {
        try {
            clazz.getDeclaredField(fieldName);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    public static class MetricInfo {
        public String name;
        public String label;
        public String unit;
    }
}
