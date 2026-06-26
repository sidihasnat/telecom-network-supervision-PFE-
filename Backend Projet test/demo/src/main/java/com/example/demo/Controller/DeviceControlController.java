package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.Service.DeviceStatusService;
import com.example.demo.Service.DockerService;
import com.example.demo.Service.SshService;
import com.example.demo.model.AuditLog;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * DeviceControlController — التحكم بدورة حياة الأجهزة (Power Off/On/Restart).
 *
 * يستبدل النسخة القديمة (pause/unpause via terminal_agent.py).
 *
 * Endpoints:
 *   POST /api/device/{name}/poweroff  → docker stop
 *   POST /api/device/{name}/poweron   → docker start (+ init script)
 *   POST /api/device/{name}/restart   → docker restart (+ init script)
 *   GET  /api/device/{name}/status
 *   GET  /api/device/all/status
 */
@RestController
@RequestMapping("/api/device")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000",
        "http://10.0.4.10", "http://localhost", "http://127.0.0.1"})
public class DeviceControlController {

    private static final Logger log = LoggerFactory.getLogger(DeviceControlController.class);

    @Autowired
    private DockerService dockerService;

    @Autowired
    private SshService sshService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private DeviceStatusService deviceStatusService;

    /**
     * POST /api/device/{name}/poweroff
     */
    @PostMapping("/{name}/poweroff")
    public ResponseEntity<Map<String, Object>> powerOff(
            @PathVariable String name,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!sshService.isKnownDevice(name)) {
            response.put("success", false);
            response.put("error", "Unknown device: " + name);
            return ResponseEntity.badRequest().body(response);
        }

        if (!dockerService.isContainerRunning(name)) {
            response.put("success", false);
            response.put("error", "Device is already off");
            response.put("status", dockerService.getContainerStatus(name));
            return ResponseEntity.badRequest().body(response);
        }

        // 🆕 علّم الجهاز كـ powered-off قبل الإيقاف
        // (لمنع false alerts)
        deviceStatusService.markAsPoweredOff(name);

        // أغلق SSH session
        sshService.closeSession(name);

        boolean success = dockerService.stopContainer(name);

        if (success) {
            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                    "Device powered off: " + name, name);
        } else {
            // إذا فشل، أزل الـ flag حتى لا نخفي الـ alert في المستقبل
            deviceStatusService.markAsPoweredOn(name);
        }

        response.put("success", success);
        response.put("device", name);
        response.put("action", "poweroff");
        response.put("status", dockerService.getContainerStatus(name));

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/device/{name}/poweron
     */
    @PostMapping("/{name}/poweron")
    public ResponseEntity<Map<String, Object>> powerOn(
            @PathVariable String name,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!sshService.isKnownDevice(name)) {
            response.put("success", false);
            response.put("error", "Unknown device: " + name);
            return ResponseEntity.badRequest().body(response);
        }

        if (dockerService.isContainerRunning(name)) {
            response.put("success", false);
            response.put("error", "Device is already running");
            response.put("status", dockerService.getContainerStatus(name));
            return ResponseEntity.badRequest().body(response);
        }

        boolean success = dockerService.startContainer(name);

        if (success) {
            // أزل الـ flag — الجهاز يبدأ يرسل metrics قريباً
            deviceStatusService.markAsPoweredOn(name);
            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                    "Device powered on: " + name, name);
        }

        response.put("success", success);
        response.put("device", name);
        response.put("action", "poweron");
        response.put("status", dockerService.getContainerStatus(name));
        response.put("note", "Init script will run, please wait ~10s for full restoration");

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/device/{name}/restart
     */
    @PostMapping("/{name}/restart")
    public ResponseEntity<Map<String, Object>> restart(
            @PathVariable String name,
            HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();

        if (!sshService.isKnownDevice(name)) {
            response.put("success", false);
            response.put("error", "Unknown device: " + name);
            return ResponseEntity.badRequest().body(response);
        }

        // 🆕 علّم كـ powered-off مؤقتاً (لمنع alert أثناء restart)
        deviceStatusService.markAsPoweredOff(name);
        sshService.closeSession(name);

        boolean success = dockerService.restartContainer(name);

        if (success) {
            // بعد restart، الجهاز سيرسل heartbeat → markAsPoweredOn تلقائياً
            auditLogService.log(AuditLog.AuditType.DEVICE_CONTROL,
                    "Device restarted: " + name, name);
        } else {
            // فشل → أزل الـ flag
            deviceStatusService.markAsPoweredOn(name);
        }

        response.put("success", success);
        response.put("device", name);
        response.put("action", "restart");
        response.put("status", dockerService.getContainerStatus(name));

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/device/{name}/status
     */
    @GetMapping("/{name}/status")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String name) {
        Map<String, Object> response = new HashMap<>();

        if (!sshService.isKnownDevice(name)) {
            response.put("success", false);
            response.put("error", "Unknown device: " + name);
            return ResponseEntity.badRequest().body(response);
        }

        boolean running = dockerService.isContainerRunning(name);
        boolean exists = dockerService.isContainerExists(name);

        response.put("success", true);
        response.put("device", name);
        response.put("running", running);
        response.put("exists", exists);
        response.put("status", dockerService.getContainerStatus(name));
        response.put("ip", sshService.getDeviceIp(name));
        response.put("poweredOff", deviceStatusService.isPoweredOff(name));

        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/device/all/status
     */
    @GetMapping("/all/status")
    public ResponseEntity<Map<String, Object>> getAllStatus() {
        Map<String, Object> result = new HashMap<>();

        String[] devices = {
                "edge-router", "core-router",
                "web-server", "dns-server",
                "ftp-server", "db-server",
                "pc1", "pc2",
                "ext-client1", "ext-client2",
                "supervision-app"
        };

        for (String device : devices) {
            Map<String, Object> deviceStatus = new HashMap<>();
            deviceStatus.put("running", dockerService.isContainerRunning(device));
            deviceStatus.put("status", dockerService.getContainerStatus(device));
            deviceStatus.put("poweredOff", deviceStatusService.isPoweredOff(device));
            result.put(device, deviceStatus);
        }

        return ResponseEntity.ok(result);
    }
}