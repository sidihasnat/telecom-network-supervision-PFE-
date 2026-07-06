import 'package:flutter/material.dart';

// ──────────────────────────────────────────────────────────────
//  AppColors — ألوان المشروع
//  محفوظة كما هي من المشروع الحالي + ألوان status للـ
//  AttackSession sources (AI / Trigger / Interface / Offline)
// ──────────────────────────────────────────────────────────────
class AppColors {
  // Backgrounds
  static const Color bgPrimary = Color(0xFF0B0E14);
  static const Color bgCard = Color(0xFF141923);
  static const Color bgHover = Color(0xFF1C2333);
  static const Color bgSurface = Color(0xFF1A1F2E);

  // Accent + status
  static const Color accent = Color(0xFF10B981);
  static const Color danger = Color(0xFFFF4444);
  static const Color warning = Color(0xFFFFAA00);
  static const Color success = Color(0xFF34D399);

  // Text
  static const Color textPrimary = Color(0xFFE5E7EB);
  static const Color textSecondary = Color(0xFF6B7280);
  static const Color textMuted = Color(0xFF4B5563);

  // Borders
  static const Color border = Color(0xFF374151);
  static const Color borderLight = Color(0xFF1F2937);

  // ──────────────────────────────────────────────────────────
  // Status colors — مطابقة لمنطق الـ Web بالضبط:
  //   🔴 RED    → AI attack OR Trigger Rule severity=CRITICAL
  //   🟡 YELLOW → Trigger Rule severity=WARNING
  //   ⚫ GREY   → Interface Down OR Device Offline (مهما كانت severity)
  //   🟢 GREEN  → Normal
  // ──────────────────────────────────────────────────────────
  static const Color statusRed = Color(0xFFFF4444);
  static const Color statusYellow = Color(0xFFFFAA00);
  static const Color statusGrey = Color(0xFF6B7280);
  static const Color statusGreen = Color(0xFF10B981);

  // دالة مساعدة لاختيار اللون حسب source و severity
  // (نستخدمها في كل الصفحات لتجنب التكرار)
  static Color colorForSession({
    required String source, // AI | TRIGGER_RULE | INTERFACE_DOWN | DEVICE_OFFLINE
    String? severity, // CRITICAL | WARNING | null
  }) {
    if (source == 'INTERFACE_DOWN' || source == 'DEVICE_OFFLINE') {
      return statusGrey;
    }
    if (source == 'AI') {
      return statusRed;
    }
    if (source == 'TRIGGER_RULE') {
      if (severity == 'CRITICAL') return statusRed;
      return statusYellow;
    }
    return statusGreen;
  }
}

// ──────────────────────────────────────────────────────────────
//  AppTheme
//  Dark theme مأخوذ من المشروع الحالي (شكل و style لم يتغيرا)
// ──────────────────────────────────────────────────────────────
class AppTheme {
  static ThemeData get darkTheme => ThemeData(
        brightness: Brightness.dark,
        scaffoldBackgroundColor: AppColors.bgPrimary,
        primaryColor: AppColors.accent,
        colorScheme: const ColorScheme.dark(
          primary: AppColors.accent,
          secondary: AppColors.accent,
          surface: AppColors.bgCard,
          error: AppColors.danger,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: AppColors.bgCard,
          elevation: 0,
          centerTitle: false,
          titleTextStyle: TextStyle(
            color: AppColors.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.w600,
          ),
          iconTheme: IconThemeData(color: AppColors.textSecondary),
        ),
        cardTheme: CardThemeData(
          color: AppColors.bgCard,
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: const BorderSide(color: AppColors.borderLight),
          ),
        ),
        inputDecorationTheme: InputDecorationTheme(
          filled: true,
          fillColor: AppColors.bgPrimary,
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: AppColors.border),
          ),
          enabledBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: AppColors.border),
          ),
          focusedBorder: OutlineInputBorder(
            borderRadius: BorderRadius.circular(12),
            borderSide: const BorderSide(color: AppColors.accent, width: 1.5),
          ),
          hintStyle: const TextStyle(color: AppColors.textMuted, fontSize: 14),
          labelStyle:
              const TextStyle(color: AppColors.textSecondary, fontSize: 12),
          contentPadding:
              const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        ),
        elevatedButtonTheme: ElevatedButtonThemeData(
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.accent,
            foregroundColor: Colors.white,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
            padding: const EdgeInsets.symmetric(vertical: 14),
            textStyle: const TextStyle(
              fontSize: 14,
              fontWeight: FontWeight.w600,
            ),
          ),
        ),
        dividerTheme: const DividerThemeData(
          color: AppColors.borderLight,
          thickness: 1,
        ),
      );
}
