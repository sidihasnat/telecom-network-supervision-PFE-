import 'package:flutter/foundation.dart';
import 'package:get/get.dart';

import '../services/local_notifications_service.dart';

// ──────────────────────────────────────────────────────────────
//  NotificationController
//
//  ── في هذا الـ batch ──
//  أصبح يستدعي LocalNotificationsService عند كل NEW_ATTACK/
//  ATTACK_ENDED ليعرض إشعار النظام (system tray).
// ──────────────────────────────────────────────────────────────

class ToastAlert {
  final String id;
  final String type;
  final String device;
  final String iface;
  final num? confidence;
  final String timestamp;
  final String source; // AI | TRIGGER_RULE | INTERFACE_DOWN | DEVICE_OFFLINE
  final String severity;
  final String? ruleName;
  final dynamic actualValue;
  final String? topFeaturesJson;

  ToastAlert({
    required this.id,
    required this.type,
    required this.device,
    required this.iface,
    required this.source,
    required this.severity,
    required this.timestamp,
    this.confidence,
    this.ruleName,
    this.actualValue,
    this.topFeaturesJson,
  });
}

class NotificationController extends GetxController {
  // Persistent toast banners
  var toastAlerts = <ToastAlert>[].obs;

  // Bell dropdown notifications history (max 50)
  var notifications = <Map<String, dynamic>>[].obs;

  // Unacked sessions count (للـ Bell badge)
  var unackedSessions = <Map<String, dynamic>>[].obs;

  void onNotification(Map<String, dynamic> notif) {
    notifications.insert(0, notif);
    if (notifications.length > 50) {
      notifications.removeRange(50, notifications.length);
    }

    final type = notif['type'] as String?;

    if (type == 'NEW_ATTACK') {
      _addToastFromNotification(notif);
      // 🔔 إرسال push notification للجهاز
      _firePushNotification(notif);
    }

    if (type == 'ATTACK_ENDED') {
      final toastId = _buildToastId(notif);
      toastAlerts.removeWhere((t) => t.id == toastId);
      // إلغاء الإشعار من tray النظام
      _cancelPushNotification(notif);
    }
  }

  void _addToastFromNotification(Map<String, dynamic> notif) {
    final source = (notif['source'] as String?) ?? 'AI';
    final device = (notif['deviceName'] as String?) ?? 'unknown';
    final iface = (notif['interfaceName'] as String?) ?? '-';
    final attackType = (notif['attackType'] as String?) ?? 'unknown';
    final id = '$device::$iface::$attackType';

    if (toastAlerts.any((t) => t.id == id)) {
      if (kDebugMode) print('[Notif] toast $id already exists, skipping');
      return;
    }

    final defaultSev =
        (source == 'AI' || source == 'DEVICE_OFFLINE') ? 'CRITICAL' : 'WARNING';
    final severity = (notif['severity'] as String?) ?? defaultSev;

    final ts = (notif['timestamp'] as String?) ??
        DateTime.now().toUtc().toIso8601String();

    toastAlerts.add(ToastAlert(
      id: id,
      type: attackType,
      device: device,
      iface: iface,
      confidence: notif['confidence'] as num?,
      timestamp: ts,
      source: source,
      severity: severity,
      ruleName: notif['ruleName'] as String?,
      actualValue: notif['actualValue'],
      topFeaturesJson: notif['topFeatures'] as String?,
    ));
  }

  // ── Push notification dispatch ──
  void _firePushNotification(Map<String, dynamic> notif) {
    final source = (notif['source'] as String?) ?? 'AI';
    final device = (notif['deviceName'] as String?) ?? 'unknown';
    final iface = (notif['interfaceName'] as String?) ?? '-';
    final attackType = (notif['attackType'] as String?) ?? 'unknown';
    final severity = (notif['severity'] as String?) ?? 'CRITICAL';

    switch (source) {
      case 'AI':
        LocalNotificationsService.showAiAttack(
          attackType: attackType,
          device: device,
          iface: iface,
          confidence: (notif['confidence'] as num?)?.toDouble(),
          mitreId: _mitreFor(attackType),
        );
        break;
      case 'TRIGGER_RULE':
        LocalNotificationsService.showTriggerRule(
          device: device,
          ruleName: (notif['ruleName'] as String?) ?? attackType,
          severity: severity,
          actualValue: notif['actualValue'],
        );
        break;
      case 'INTERFACE_DOWN':
      case 'DEVICE_OFFLINE':
        LocalNotificationsService.showOfflineEvent(
          device: device,
          iface: iface,
          source: source,
        );
        break;
    }
  }

  void _cancelPushNotification(Map<String, dynamic> notif) {
    final device = (notif['deviceName'] as String?) ?? '';
    final iface = (notif['interfaceName'] as String?) ?? '-';
    final attackType = (notif['attackType'] as String?) ?? '';
    LocalNotificationsService.cancel(device, iface, attackType);
  }

  String _buildToastId(Map<String, dynamic> notif) {
    final device = (notif['deviceName'] as String?) ?? '';
    final iface = (notif['interfaceName'] as String?) ?? '-';
    final attackType = (notif['attackType'] as String?) ?? '';
    return '$device::$iface::$attackType';
  }

  String? _mitreFor(String attackType) {
    const map = {
      'synflood': 'T1498.001',
      'dos': 'T1498',
      'ddos': 'T1498',
      'http_flood': 'T1499',
      'dns_flood': 'T1498.002',
      'portscan': 'T1046',
      'bruteforce': 'T1110',
      'fault': 'T1489',
      'rst_flood': 'T1498.001',
      'udp_flood': 'T1498.001',
      'ping_of_death': 'T1498.001',
      'router_dos': 'T1498',
      'router_synflood': 'T1498.001',
    };
    return map[attackType];
  }

  // ── Dismiss / ACK ──
  void dismissToast(String id) {
    toastAlerts.removeWhere((t) => t.id == id);
  }

  void clearAllToasts() {
    toastAlerts.clear();
  }

  void clearAllNotifications() {
    notifications.clear();
    toastAlerts.clear();
    LocalNotificationsService.cancelAll();
  }

  int get bellBadgeCount =>
      notifications.length + unackedSessions.length;
}
