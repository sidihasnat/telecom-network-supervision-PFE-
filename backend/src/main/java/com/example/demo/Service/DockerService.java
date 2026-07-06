package com.example.demo.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);

    @Value("${clab.container.prefix:clab-telecom-supervision-}")
    private String containerPrefix;

    public boolean stopContainer(String deviceName) {
        String containerName = containerPrefix + deviceName;
        log.info("⏸ Pausing container: {}", containerName);
        return executeDockerCommand("pause", containerName);
    }

    public boolean startContainer(String deviceName) {
        String containerName = containerPrefix + deviceName;
        log.info("▶ Unpausing container: {}", containerName);
        return executeDockerCommand("unpause", containerName);
    }

    public boolean restartContainer(String deviceName) {
        String containerName = containerPrefix + deviceName;
        log.info(" Restarting (pause+unpause): {}", containerName);

        boolean paused = executeDockerCommand("pause", containerName);
        if (!paused) return false;

        try { Thread.sleep(1500); } catch (InterruptedException ignored) {}

        return executeDockerCommand("unpause", containerName);
    }

    public boolean isContainerRunning(String deviceName) {
        String containerName = containerPrefix + deviceName;
        try {

            ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "-a",
                "--filter", "name=" + containerName,
                "--format", "{{.Status}}"
            );
            Process process = pb.start();
            String output = readOutput(process);
            process.waitFor(3, TimeUnit.SECONDS);
            String status = output.trim().toLowerCase();

            if (status.contains("paused")) return false;
            return status.startsWith("up");
        } catch (Exception e) {
            log.error("Error checking container status: {}", e.getMessage());
            return false;
        }
    }

    public boolean isContainerExists(String deviceName) {
        String containerName = containerPrefix + deviceName;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "-aq", "-f", "name=" + containerName
            );
            Process process = pb.start();
            String output = readOutput(process);
            process.waitFor(3, TimeUnit.SECONDS);
            return !output.trim().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    public String getContainerStatus(String deviceName) {
        String containerName = containerPrefix + deviceName;
        try {
            ProcessBuilder pb = new ProcessBuilder(
                "docker", "ps", "-a",
                "--filter", "name=" + containerName,
                "--format", "{{.Status}}"
            );
            Process process = pb.start();
            String output = readOutput(process);
            process.waitFor(3, TimeUnit.SECONDS);
            String status = output.trim();
            return status.isEmpty() ? "not_found" : status;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private boolean executeDockerCommand(String... args) {
        List<String> command = new ArrayList<>();
        command.add("docker");
        for (String arg : args) command.add(arg);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output = readOutput(process);
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                log.error("Docker command timed out: {}", String.join(" ", command));
                return false;
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                log.error("Docker command failed (exit={}): {} | output: {}",
                    exitCode, String.join(" ", command), output);
                return false;
            }
            return true;

        } catch (Exception e) {
            log.error("Docker command error: {}", e.getMessage());
            return false;
        }
    }

    private String readOutput(Process process) {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        } catch (Exception e) {
            log.error("Error reading process output: {}", e.getMessage());
        }
        return output.toString();
    }
}
