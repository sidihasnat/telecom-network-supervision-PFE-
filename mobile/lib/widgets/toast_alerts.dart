import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:get/get.dart';

import '../app_theme.dart';
import '../controllers/notification_controller.dart';
import '../controllers/websocket_controller.dart';
import '../data/mitre.dart';

// ──────────────────────────────────────────────────────────────
//  ToastAlertsWidget
//
//  ── إعادة تصميم في هذا الـ batch ──
//  المستخدم لاحظ أن الشكل طبق الأصل من React. النمط الجديد:
//   - خلفية موحَّدة داكنة (bgPrimary) بدل الـ tinted background
//   - شريط عمودي ملون عريض (4px) على اليسار يحدد الـ severity
//   - أيقونة دائرية بارزة داخل الـ banner بدل الـ pulsing dot
//   - زر ACK كـ icon button دائري بدل النص
//   - typography هادئة + spacing مريح
//
//  هذا نمط Material Design اللي ييجي مع Flutter بالأصل، ليس
//  Tailwind/web اللي في React.
// ──────────────────────────────────────────────────────────────
class ToastAlertsWidget extends StatelessWidget {
  const ToastAlertsWidget({super.key});

  @override
  Widget build(BuildContext context) {
    if (!Get.isRegistered<NotificationController>()) {
      return const SizedBox.shrink();
    }
    final c = Get.find<NotificationController>();

    return Obx(() {
      if (c.toastAlerts.isEmpty) return const SizedBox.shrink();

      return Padding(
        padding: const EdgeInsets.fromLTRB(8, 6, 8, 0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: c.toastAlerts.map((toast) {
            return Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: _ToastCard(toast: toast, controller: c),
            );
          }).toList(),
        ),
      );
    });
  }
}

class _ToastCard extends StatelessWidget {
  final ToastAlert toast;
  final NotificationController controller;

  const _ToastCard({required this.toast, required this.controller});

  @override
  Widget build(BuildContext context) {
    final palette = _palette();

    return Material(
      elevation: 4,
      borderRadius: BorderRadius.circular(12),
      color: AppColors.bgPrimary,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Vertical color stripe
              Container(width: 4, color: palette.accent),

              // Content
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(10, 10, 8, 10),
                  child: _buildContent(palette),
                ),
              ),

              // ACK button
              _AckIconButton(
                color: palette.accent,
                onTap: () => controller.dismissToast(toast.id),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildContent(_Palette palette) {
    final isAi = toast.source == 'AI';
    if (isAi) return _aiContent(palette);
    return _genericContent(palette);
  }

  // ─── AI content ──────────────────────────────────────────
  Widget _aiContent(_Palette palette) {
    final mitre = kMitreMap[toast.type];
    final info = kAttackInfo[toast.type];

    final topFeatures = _readTopFeatures();
    final confStr = toast.confidence != null
        ? '${(toast.confidence! * 100).toStringAsFixed(0)}%'
        : null;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            _IconBadge(icon: Icons.warning_rounded, color: palette.accent),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                formatAttackName(toast.type),
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        // device :: iface
        Padding(
          padding: const EdgeInsets.only(left: 36),
          child: Text(
            '${toast.device}${toast.iface != "-" ? "  ::  ${toast.iface}" : ""}',
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 11,
              fontFamily: 'monospace',
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        // chips row: confidence + severity + MITRE
        const SizedBox(height: 6),
        Padding(
          padding: const EdgeInsets.only(left: 36),
          child: Wrap(
            spacing: 4,
            runSpacing: 4,
            children: [
              if (confStr != null) _Chip(text: confStr, color: palette.accent),
              if (info != null)
                _Chip(text: info.severity, color: palette.accent, dim: true),
              if (mitre != null)
                _Chip(text: mitre.id, color: AppColors.textMuted, dim: true),
            ],
          ),
        ),
        // top features (if any)
        if (topFeatures.isNotEmpty) ...[
          const SizedBox(height: 6),
          Padding(
            padding: const EdgeInsets.only(left: 36),
            child: Wrap(
              spacing: 8,
              runSpacing: 2,
              children: topFeatures.take(3).map((f) {
                final name = f['name']?.toString() ?? '';
                final val = f['value'];
                final valStr = val is num
                    ? val.toStringAsFixed(1)
                    : (val?.toString() ?? '');
                return RichText(
                  text: TextSpan(
                    style: const TextStyle(fontSize: 9),
                    children: [
                      TextSpan(
                        text: '$name: ',
                        style: const TextStyle(color: AppColors.textMuted),
                      ),
                      TextSpan(
                        text: valStr,
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
        ],
        // recommendation
        if (info != null) ...[
          const SizedBox(height: 6),
          Padding(
            padding: const EdgeInsets.only(left: 36),
            child: Text(
              '💡  ${info.recommendation.split('·').first.trim()}',
              style: const TextStyle(
                color: AppColors.accent,
                fontSize: 10,
              ),
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
            ),
          ),
        ],
      ],
    );
  }

  // ─── Generic content (Trigger/Interface/Offline) ─────────
  Widget _genericContent(_Palette palette) {
    final isInterfaceDown = toast.source == 'INTERFACE_DOWN';
    final isDeviceOffline = toast.source == 'DEVICE_OFFLINE';

    String title;
    String subtitle;
    IconData icon;

    if (isDeviceOffline) {
      icon = Icons.power_off_rounded;
      title = 'Appareil hors ligne';
      subtitle = '${toast.device} a arrêté d\'envoyer des métriques';
    } else if (isInterfaceDown) {
      icon = Icons.cable_rounded;
      title = 'Interface inactive';
      subtitle =
          '${toast.device}${toast.iface != "-" ? " :: ${toast.iface}" : ""}';
    } else {
      // TRIGGER_RULE
      icon = Icons.notifications_active_rounded;
      title = 'Règle déclenchée';
      final ruleLabel = toast.ruleName ?? toast.type;
      final actualVal = toast.actualValue;
      final valSuffix = actualVal != null
          ? ' · ${actualVal is num ? actualVal.toStringAsFixed(1) : actualVal}'
          : '';
      subtitle = '${toast.device} · $ruleLabel$valSuffix';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            _IconBadge(icon: icon, color: palette.accent),
            const SizedBox(width: 8),
            Expanded(
              child: Text(
                title,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                  fontWeight: FontWeight.w700,
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ],
        ),
        const SizedBox(height: 4),
        Padding(
          padding: const EdgeInsets.only(left: 36),
          child: Text(
            subtitle,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 11,
              fontFamily: 'monospace',
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ),
        const SizedBox(height: 4),
        Padding(
          padding: const EdgeInsets.only(left: 36),
          child: _Chip(text: toast.severity, color: palette.accent),
        ),
      ],
    );
  }

  // ─── Color palette per source/severity ──────────────────
  _Palette _palette() {
    if (toast.source == 'INTERFACE_DOWN' ||
        toast.source == 'DEVICE_OFFLINE') {
      return _Palette(accent: AppColors.statusGrey);
    }
    if (toast.severity == 'WARNING') {
      return _Palette(accent: AppColors.statusYellow);
    }
    return _Palette(accent: AppColors.danger);
  }

  // ─── topFeatures: from toast or from live predictions ───
  List<Map<String, dynamic>> _readTopFeatures() {
    String? raw = toast.topFeaturesJson;
    if (raw == null || raw.isEmpty || raw == '[]') {
      if (Get.isRegistered<WebSocketController>()) {
        final ws = Get.find<WebSocketController>();
        final key = '${toast.device}::${toast.iface}';
        raw = ws.latestPredictions[key]?['topFeatures'] as String?;
      }
    }
    if (raw == null || raw.isEmpty || raw == '[]') return [];
    try {
      final parsed = jsonDecode(raw);
      if (parsed is List) {
        return parsed
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
    return [];
  }
}

// ─── Color palette object ───────────────────────────────────
class _Palette {
  final Color accent;
  _Palette({required this.accent});
}

// ─── Round icon badge ───────────────────────────────────────
class _IconBadge extends StatelessWidget {
  final IconData icon;
  final Color color;
  const _IconBadge({required this.icon, required this.color});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 28,
      height: 28,
      decoration: BoxDecoration(
        color: color.withOpacity(0.15),
        shape: BoxShape.circle,
      ),
      child: Icon(icon, size: 16, color: color),
    );
  }
}

// ─── Subtle chip ────────────────────────────────────────────
class _Chip extends StatelessWidget {
  final String text;
  final Color color;
  final bool dim;
  const _Chip({required this.text, required this.color, this.dim = false});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
      decoration: BoxDecoration(
        color: color.withOpacity(dim ? 0.08 : 0.15),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        text,
        style: TextStyle(
          color: color,
          fontSize: 9,
          fontWeight: FontWeight.w600,
          letterSpacing: 0.3,
        ),
      ),
    );
  }
}

// ─── Round icon-button ACK ──────────────────────────────────
class _AckIconButton extends StatelessWidget {
  final Color color;
  final VoidCallback onTap;
  const _AckIconButton({required this.color, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(8),
      child: Material(
        color: color.withOpacity(0.15),
        shape: const CircleBorder(),
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: Container(
            width: 36,
            height: 36,
            alignment: Alignment.center,
            child: Icon(Icons.check_rounded, size: 20, color: color),
          ),
        ),
      ),
    );
  }
}
