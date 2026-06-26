package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

/**
 * QuickCommand — predefined terminal commands shown as buttons on the Terminal page.
 *
 * Managed from Settings → Quick Commands (CRUD, ADMIN only).
 * Fetched by the Terminal page to render one-click shortcut buttons.
 *
 * Example entries:
 *   - name: "List connections"       command: "ss -tunap"
 *   - name: "Check CPU"              command: "top -bn1 | head -15"
 *   - name: "Show routing table"     command: "ip route"       deviceType: "router"
 *   - name: "Check failed logins"    command: "tail /var/log/auth.log" deviceType: "server"
 */
@Data
@Entity
@Table(name = "quick_commands")
public class QuickCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Short label shown on the button (e.g., "List connections")
    @Column(nullable = false, length = 100)
    private String name;

    // The shell command to execute (e.g., "ss -tunap")
    @Column(nullable = false, length = 500)
    private String command;

    // Optional scope:
    //   null / ""  → available on all devices
    //   "router"   → only on routers
    //   "server"   → only on servers
    private String deviceType;

    // Display order in the Terminal UI (lower = shown first)
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    // Allow admin to temporarily hide a command without deleting it
    @Column(nullable = false)
    private Boolean enabled = true;
}
