package com.example.demo.Controller;

import com.example.demo.Service.AuditLogService;
import com.example.demo.Service.SshService;
import com.example.demo.Service.SshService.CommandResult;
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
 * TerminalController — تنفيذ commands على الأجهزة عبر SSH.
 *
 * يستبدل terminal_agent.py القديم (port 6000) بالكامل.
 *
 * Frontend يستدعي:
 *   POST /api/terminal/exec
 *   Body: { "device": "web-server", "command": "ps aux" }
 */
@RestController
@RequestMapping("/api/terminal")
@CrossOrigin(origins = {"http://localhost:3000", "http://127.0.0.1:3000",
                         "http://10.0.4.10", "http://localhost", "http://127.0.0.1"})
public class  TerminalController {

    private static final Logger log = LoggerFactory.getLogger(TerminalController.class);

    @Autowired
    private SshService sshService;

    @Autowired
    private AuditLogService auditLogService;


    private static final String[] DANGEROUS_PATTERNS = {
        "rm -rf /",
        ":(){:|:&};:",
        "dd if=/dev/zero of=/dev/sd",
        "mkfs.",
    };


    @PostMapping("/exec")
    public ResponseEntity<Map<String, Object>> executeCommand(
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {

        String device = request.get("device");
        String command = request.get("command");

        if (device == null && request.containsKey("container")) {
            String containerName = request.get("container");
            device = containerName.replace("clab-telecom-supervision-", "");
        }

        Map<String, Object> response = new HashMap<>();

        if (device == null || device.isBlank()) {
            response.put("success", false);
            response.put("error", "Device name required");
            return ResponseEntity.badRequest().body(response);
        }
        if (command == null || command.isBlank()) {
            response.put("success", false);
            response.put("error", "Command required");
            return ResponseEntity.badRequest().body(response);
        }

        if (!sshService.isKnownDevice(device)) {
            response.put("success", false);
            response.put("error", "Unknown device: " + device);
            return ResponseEntity.badRequest().body(response);
        }

        String cmdLower = command.toLowerCase();
        for (String pattern : DANGEROUS_PATTERNS) {
            if (cmdLower.contains(pattern.toLowerCase())) {
                log.warn("⚠️ Dangerous command blocked on {}: {}", device, command);
                response.put("success", false);
                response.put("error", "Dangerous command blocked");
                return ResponseEntity.status(403).body(response);
            }
        }

        log.info("Terminal [{}]: {}", device, command);
        CommandResult result = sshService.executeCommand(device, command);

        response.put("success", result.success);
        response.put("exitCode", result.exitCode);
        response.put("stdout", result.stdout);
        response.put("stderr", result.stderr);
        response.put("output", result.getCombinedOutput());
        if (!result.success) {
            response.put("error", result.errorMessage);
        }

        try {
            auditLogService.log(
                AuditLog.AuditType.TERMINAL,
                "device=" + device + ", cmd=" + command + ", exit=" + result.exitCode,
                device
            );
        } catch (Exception e) {
            log.debug("Audit log skipped: {}", e.getMessage());
        }

        return ResponseEntity.ok(response);
    }


    @GetMapping("/devices")
    public ResponseEntity<Map<String, Object>> listDevices() {
        Map<String, Object> response = new HashMap<>();
        response.put("devices", new String[]{
            "edge-router", "core-router",
            "web-server", "dns-server",
            "ftp-server", "db-server",
            "pc1", "pc2",
            "ext-client1", "ext-client2",
            "supervision-app"
        });
        return ResponseEntity.ok(response);
    }


    @GetMapping("/ping/{device}")
    public ResponseEntity<Map<String, Object>> pingDevice(@PathVariable String device) {
        Map<String, Object> response = new HashMap<>();
        response.put("device", device);
        response.put("reachable", sshService.ping(device));
        return ResponseEntity.ok(response);
    }
}
