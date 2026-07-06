package com.example.demo.Service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LabelingService {

    private final ConcurrentHashMap<String, String> deviceLabels = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, String> interfaceLabels = new ConcurrentHashMap<>();

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

    public String resolveLabel(String deviceName, String interfaceName) {

        if (interfaceName != null) {
            String ifaceLabel = getInterfaceLabel(deviceName, interfaceName);
            if (ifaceLabel != null) return ifaceLabel;
        }

        return getDeviceLabel(deviceName);
    }

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
