package com.example.demo.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AuditLog — Journal d'Audit.
 *
 * يسجل كل حدث غير هجومي في النظام:
 *   PROTECTION  → SYN Cookies enabled on web-server
 *   ESCALATION  → Alert escalated Level 1 → Level 2
 *   ACKNOWLEDGE → Admin acknowledged synflood alert
 *   TERMINAL    → Command executed: ss -tn on web-server
 *   AI          → Live prediction started / stopped
 *   SETTINGS    → Protection mode changed: Manual → Auto
 *   AUTH        → User logged in / failed login
 *   EMAIL       → 🆕 Email digest sent / instant alert sent / SMTP failure
 *
 * يظهر في: Security/AI → Tab 3 (Activity Log)
 */
@Data
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditType type;

    public enum AuditType {
        PROTECTION,     // 🛡️ إجراء حماية
        ESCALATION,     // ⚠️ ترقية مستوى إنذار
        ACKNOWLEDGE,    // ✅ اعتراف بإنذار
        TERMINAL,       // 🖥️ أمر terminal
        AI,             // 🤖 حدث AI
        SETTINGS,       // ⚙️ تغيير إعدادات
        AUTH,           // 🔑 تسجيل دخول
        DEVICE_CONTROL, // 🎛️ Power on/off
        EMAIL           // 📧 إرسال email (digest / instant alert / failure)
    }

    @Column(nullable = false, length = 500)
    private String description;

    // ── اختياري: ربط بجهاز معين ────────────────────────────────
    private String deviceName;

    // ── اختياري: المستخدم الذي قام بالفعل ──────────────────────
    private String username;

    // ── Pre-persist: timestamp تلقائي ──────────────────────────
    @PrePersist
    public void setTimestampIfNull() {
        if (this.timestamp == null) {
            this.timestamp = LocalDateTime.now();
        }
    }
}
