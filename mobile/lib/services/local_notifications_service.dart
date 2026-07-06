import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

// ──────────────────────────────────────────────────────────────
//  LocalNotificationsService
//
//  يعرض إشعارات حقيقية في system tray (Android/iOS) عند كشف
//  هجمات أو أحداث مهمة. يعمل عند:
//   - التطبيق مفتوح (foreground)
//   - التطبيق في الخلفية (background) ← طالما WebSocket متصل
//
//  ❌ لا يعمل لو التطبيق مغلق تماماً (killed) — هذا يحتاج FCM.
//
//  ── الـ Channels (Android) ──
//  - 'attacks'   → هجمات AI (CRITICAL severity)
//  - 'warnings'  → Trigger Rules + Interface Down (WARNING)
//  - 'system'    → Device Offline + رسائل عامة
//
//  كل channel له صوت/اهتزاز خاص (يمكن للمستخدم التحكم بكل واحد
//  من إعدادات النظام).
// ──────────────────────────────────────────────────────────────
class LocalNotificationsService {
  static final FlutterLocalNotificationsPlugin _plugin =
      FlutterLocalNotificationsPlugin();

  static bool _initialized = false;

  /// Initialize once at app startup.
  static Future<void> init() async {
    if (_initialized) return;

  //Définit l’icône par défaut des notifications.
    const androidInit =
        AndroidInitializationSettings('@mipmap/ic_launcher');

    const iosInit = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );

    const initSettings = InitializationSettings(
      android: androidInit,
      iOS: iosInit,
    );

    try {
      await _plugin.initialize(
        initSettings,
        onDidReceiveNotificationResponse: _onNotificationTap,
      );

      // Request runtime permission على Android 13+ (API 33+)
      // (في الـ AndroidManifest نضيف POST_NOTIFICATIONS)
      final android = _plugin
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>();
      if (android != null) {
        await android.requestNotificationsPermission();
      }

      _initialized = true;
      if (kDebugMode) print('[Notif] initialized');
    } catch (e) {
      if (kDebugMode) print('[Notif] init error: $e');
    }
  }

  static void _onNotificationTap(NotificationResponse response) {
    // عند الضغط على الإشعار — يمكن إضافة navigation هنا لاحقاً
    if (kDebugMode) {
      print('[Notif] tapped: ${response.payload}');
    }
  }

  /// عرض إشعار AI attack (CRITICAL — صوت + اهتزاز قوي)
  static Future<void> showAiAttack({
    required String attackType,
    required String device,
    required String iface,
    double? confidence,
    String? mitreId,
  }) async {
    if (!_initialized) return;

    final confStr = confidence != null
        ? ' (${(confidence * 100).toStringAsFixed(0)}%)'
        : '';
    final body = mitreId != null
        ? '$device :: $iface · $mitreId$confStr'
        : '$device :: $iface$confStr';

    await _show(
      id: _idFor(device, iface, attackType),
      channelId: 'attacks',
      channelName: 'Attack Alerts',
      channelDescription:
          'Critical AI-detected attacks on monitored devices',
      title: ' ${_prettyAttack(attackType)} detected',
      body: body,
      importance: Importance.max,
      priority: Priority.max,
      payload: 'attack::$device::$iface::$attackType',
    );
  }

  /// عرض إشعار Trigger Rule (WARNING/CRITICAL)
  static Future<void> showTriggerRule({
    required String device,
    required String ruleName,
    String? severity,
    dynamic actualValue,
  }) async {
    if (!_initialized) return;

    final isCritical = severity == 'CRITICAL';
    final body = actualValue != null
        ? '$ruleName · current: $actualValue'
        : ruleName;

    await _show(
      id: _idFor(device, 'trigger', ruleName),
      channelId: isCritical ? 'attacks' : 'warnings',
      channelName: isCritical ? 'Attack Alerts' : 'Trigger Warnings',
      channelDescription: isCritical
          ? 'Critical operator-defined alerts'
          : 'Trigger rule warnings',
      title: '${isCritical ? "" : ""} Rule triggered: $device',
      body: body,
      importance: isCritical ? Importance.max : Importance.high,
      priority: isCritical ? Priority.max : Priority.high,
      payload: 'trigger::$device::$ruleName',
    );
  }

  /// عرض إشعار Device Offline / Interface Down
  static Future<void> showOfflineEvent({
    required String device,
    String? iface,
    required String source, // INTERFACE_DOWN | DEVICE_OFFLINE
  }) async {
    if (!_initialized) return;

    final isInterface = source == 'INTERFACE_DOWN';
    final title = isInterface
        ? ' Interface down: $device'
        : ' Device offline: $device';
    final body = isInterface
        ? 'Link offline: ${iface ?? "-"}'
        : 'Device stopped sending metrics';

    await _show(
      id: _idFor(device, iface ?? '-', source),
      channelId: 'system',
      channelName: 'System Events',
      channelDescription: 'Device and interface status changes',
      title: title,
      body: body,
      importance: Importance.high,
      priority: Priority.high,
      payload: 'offline::$device::${iface ?? "-"}::$source',
    );
  }

  /// إلغاء إشعار محدد (عند ATTACK_ENDED مثلاً)
  static Future<void> cancel(
      String device, String iface, String type) async {
    if (!_initialized) return;
    final id = _idFor(device, iface, type);
    await _plugin.cancel(id);
  }

  static Future<void> cancelAll() async {
    if (!_initialized) return;
    await _plugin.cancelAll();
  }

  // ─── Internal helpers ───

  static Future<void> _show({
    required int id,
    required String channelId,
    required String channelName,
    required String channelDescription,
    required String title,
    required String body,
    required Importance importance,
    required Priority priority,
    String? payload,
  }) async {
    final androidDetails = AndroidNotificationDetails(
      channelId,
      channelName,
      channelDescription: channelDescription,
      importance: importance,
      priority: priority,
      enableVibration: true,
      playSound: true,
      ticker: title,
      visibility: NotificationVisibility.public,
      category: AndroidNotificationCategory.alarm,
    );

    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
      interruptionLevel: InterruptionLevel.timeSensitive,
    );

    final details = NotificationDetails(
      android: androidDetails,
      iOS: iosDetails,
    );

    try {
      await _plugin.show(id, title, body, details, payload: payload);
    } catch (e) {
      if (kDebugMode) print('[Notif] show error: $e');
    }
  }

  /// نولّد ID ثابت لكل (device, iface, type) عشان نقدر نلغي نفس
  /// الإشعار لو ATTACK_ENDED وصل.
  /// ID يجب أن يكون int 32-bit، فنستخدم hashCode محدود.
  static int _idFor(String device, String iface, String type) {
    final s = '$device::$iface::$type';
    // محصور في 0..2^31-1
    return s.hashCode & 0x7FFFFFFF;
  }

  static String _prettyAttack(String type) {
    const names = {
      'synflood': 'SYN Flood',
      'dos': 'DoS',
      'ddos': 'DDoS',
      'udp_flood': 'UDP Flood',
      'http_flood': 'HTTP Flood',
      'dns_flood': 'DNS Flood',
      'portscan': 'Port Scan',
      'ping_of_death': 'Ping of Death',
      'rst_flood': 'RST Flood',
      'router_dos': 'Router DoS',
      'router_synflood': 'Router SYN Flood',
      'bruteforce': 'Brute Force',
      'fault': 'Fault',
    };
    return names[type] ?? type.toUpperCase();
  }
}
