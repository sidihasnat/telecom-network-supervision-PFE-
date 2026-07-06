package com.example.demo.Service;

import com.jcraft.jsch.*;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SshService {

    private static final Logger log = LoggerFactory.getLogger(SshService.class);

    @Value("${ssh.default.username:root}")
    private String defaultUsername;

    @Value("${ssh.default.password:toor}")
    private String defaultPassword;

    @Value("${ssh.default.port:22}")
    private int defaultPort;

    @Value("${ssh.connect.timeout:5000}")
    private int connectTimeout;

    @Value("${ssh.command.timeout:10000}")
    private int commandTimeout;

    private static final Map<String, String> DEVICE_IP_MAP = Map.ofEntries(
        Map.entry("edge-router", "10.0.3.1"),
        Map.entry("core-router", "10.0.3.2"),
        Map.entry("web-server",  "10.0.1.2"),
        Map.entry("dns-server",  "10.0.1.3"),
        Map.entry("ftp-server",  "10.0.2.2"),
        Map.entry("db-server",   "10.0.2.3"),
        Map.entry("pc1",         "10.0.2.4"),
        Map.entry("pc2",         "10.0.2.5"),
        Map.entry("ext-client1", "10.0.0.20"),
        Map.entry("ext-client2", "10.0.0.30"),
        Map.entry("supervision-app", "10.0.4.11")
    );

    private final Map<String, Session> sessionPool = new ConcurrentHashMap<>();
    private final JSch jsch = new JSch();

    @PostConstruct
    public void init() {
        log.info("SshService initialized — managing {} devices", DEVICE_IP_MAP.size());
    }

    @PreDestroy
    public void cleanup() {
        log.info("🔌 Closing all SSH sessions...");
        sessionPool.values().forEach(s -> {
            try { if (s.isConnected()) s.disconnect(); }
            catch (Exception e) {  }
        });
        sessionPool.clear();
    }

    public String getDeviceIp(String deviceName) {
        String ip = DEVICE_IP_MAP.get(deviceName);
        if (ip == null) {
            throw new IllegalArgumentException("Unknown device: " + deviceName);
        }
        return ip;
    }

    public boolean isKnownDevice(String deviceName) {
        return DEVICE_IP_MAP.containsKey(deviceName);
    }

    private synchronized Session getSession(String deviceName) throws JSchException {
        Session existing = sessionPool.get(deviceName);
        if (existing != null && existing.isConnected()) {
            return existing;
        }

        if (existing != null) {
            try { existing.disconnect(); } catch (Exception e) {  }
        }

        String ip = getDeviceIp(deviceName);
        log.debug("Creating SSH session: {} → {}", deviceName, ip);

        Session session = jsch.getSession(defaultUsername, ip, defaultPort);
        session.setPassword(defaultPassword);
        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password,publickey");
        session.setServerAliveInterval(30000);
        session.setServerAliveCountMax(3);
        session.connect(connectTimeout);

        sessionPool.put(deviceName, session);
        return session;
    }

    public CommandResult executeCommand(String deviceName, String command) {
        long startTime = System.currentTimeMillis();
        ChannelExec channel = null;

        try {
            Session session = getSession(deviceName);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);

            InputStream stdoutStream = channel.getInputStream();
            InputStream stderrStream = channel.getErrStream();

            channel.connect(connectTimeout);

            ByteArrayOutputStream stdoutBuf = new ByteArrayOutputStream();
            ByteArrayOutputStream stderrBuf = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];

            long deadline = System.currentTimeMillis() + commandTimeout;

            while (System.currentTimeMillis() < deadline) {

                while (stdoutStream.available() > 0) {
                    int n = stdoutStream.read(buffer, 0, buffer.length);
                    if (n < 0) break;
                    stdoutBuf.write(buffer, 0, n);
                }

                while (stderrStream.available() > 0) {
                    int n = stderrStream.read(buffer, 0, buffer.length);
                    if (n < 0) break;
                    stderrBuf.write(buffer, 0, n);
                }

                if (channel.isClosed()) {

                    while (stdoutStream.available() > 0) {
                        int n = stdoutStream.read(buffer, 0, buffer.length);
                        if (n < 0) break;
                        stdoutBuf.write(buffer, 0, n);
                    }
                    while (stderrStream.available() > 0) {
                        int n = stderrStream.read(buffer, 0, buffer.length);
                        if (n < 0) break;
                        stderrBuf.write(buffer, 0, n);
                    }
                    break;
                }

                Thread.sleep(50);
            }

            int exitCode = channel.isClosed() ? channel.getExitStatus() : -1;
            String stdout = stdoutBuf.toString();
            String stderr = stderrBuf.toString();

            long duration = System.currentTimeMillis() - startTime;
            log.debug("SSH [{}] '{}' → exit={} stdout={}b stderr={}b ({}ms)",
                deviceName, command, exitCode, stdout.length(), stderr.length(), duration);

            return new CommandResult(exitCode, stdout, stderr, true, null);

        } catch (Exception e) {
            log.error("SSH command failed [{}] '{}': {}", deviceName, command, e.getMessage());
            sessionPool.remove(deviceName);
            return new CommandResult(-1, "", "", false, e.getMessage());
        } finally {
            if (channel != null) {
                try { channel.disconnect(); } catch (Exception ignored) {}
            }
        }
    }

    public boolean ping(String deviceName) {
        CommandResult result = executeCommand(deviceName, "echo ok");
        return result.success && result.stdout.trim().equals("ok");
    }

    public void closeSession(String deviceName) {
        Session session = sessionPool.remove(deviceName);
        if (session != null && session.isConnected()) {
            try { session.disconnect(); }
            catch (Exception e) {  }
        }
    }

    public static class CommandResult {
        public final int exitCode;
        public final String stdout;
        public final String stderr;
        public final boolean success;
        public final String errorMessage;

        public CommandResult(int exitCode, String stdout, String stderr,
                             boolean success, String errorMessage) {
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            this.success = success;
            this.errorMessage = errorMessage;
        }

        public String getCombinedOutput() {
            StringBuilder sb = new StringBuilder();
            if (!stdout.isEmpty()) sb.append(stdout);
            if (!stderr.isEmpty()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(stderr);
            }
            return sb.toString();
        }
    }
}
