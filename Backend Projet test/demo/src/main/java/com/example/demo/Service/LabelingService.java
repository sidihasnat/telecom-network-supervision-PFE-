package com.example.demo.Service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the current attack labels in memory — per device + per interface.
 *
 * Structure:
 *   deviceLabels  : { "ssh-server" → "bruteforce", "edge-router" → "transit", ... }
 *   interfaceLabels: { "edge-router::br-wan" → "fault", "edge-router::eth2" → "normal", ... }
 *
 * Priority when labeling a metric:
 *   1. Interface-level label (most specific)
 *   2. Device-level label
 *   3. null (normal monitoring mode)
 */
@Service
public class LabelingService {

    // Device-level labels: deviceName → label
    // Example: "ssh-server" → "bruteforce"
    private final ConcurrentHashMap<String, String> deviceLabels = new ConcurrentHashMap<>();

    // Interface-level labels: "deviceName::interfaceName" → label
    // Example: "edge-router::br-wan" → "fault"
    private final ConcurrentHashMap<String, String> interfaceLabels = new ConcurrentHashMap<>();

    // ── Device-level ──────────────────────────────────────────────

    public void setDeviceLabel(String deviceName, String label) {
        if (label == null) {
            deviceLabels.remove(deviceName);
        } else {
            deviceLabels.put(deviceName, label);
        }
    }

    public String getDeviceLabel(String deviceName) {
        return deviceLabels.get(deviceName);
    }

    public void clearDeviceLabel(String deviceName) {
        deviceLabels.remove(deviceName);
    }

    public void clearAllLabels() {
        deviceLabels.clear();
        interfaceLabels.clear();
    }

    // ── Interface-level ───────────────────────────────────────────

    public void setInterfaceLabel(String deviceName, String interfaceName, String label) {
        String key = deviceName + "::" + interfaceName;
        if (label == null) {
            interfaceLabels.remove(key);
        } else {
            interfaceLabels.put(key, label);
        }
    }

    public String getInterfaceLabel(String deviceName, String interfaceName) {
        return interfaceLabels.get(deviceName + "::" + interfaceName);
    }

    public void clearInterfaceLabel(String deviceName, String interfaceName) {
        interfaceLabels.remove(deviceName + "::" + interfaceName);
    }

    // ── Resolve label for a metric ────────────────────────────────
    // Priority: interface-level > device-level > null

    public String resolveLabel(String deviceName, String interfaceName) {
        // 1. Interface-level (most specific)
        if (interfaceName != null) {
            String ifaceLabel = getInterfaceLabel(deviceName, interfaceName);
            if (ifaceLabel != null) return ifaceLabel;
        }
        // 2. Device-level
        return getDeviceLabel(deviceName);
    }

    // ── Status ────────────────────────────────────────────────────

    public boolean isLabeling() {
        return !deviceLabels.isEmpty() || !interfaceLabels.isEmpty();
    }

    public ConcurrentHashMap<String, String> getAllDeviceLabels() {
        return deviceLabels;
    }

    public ConcurrentHashMap<String, String> getAllInterfaceLabels() {
        return interfaceLabels;
    }
}