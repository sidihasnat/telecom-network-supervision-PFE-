import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';
import 'package:http/http.dart' as http;

import 'LoginPage.dart';

// ──────────────────────────────────────────────────────────────
//  ApiService
//  ملف واحد فيه كل الـ REST calls للـ Spring Boot backend.
//
//  ملاحظات مهمة (تم تصحيحها بعد المراجعة):
//   - الفيلد في AttackSession اسمه "sessionSource" مش "source"
//   - ARP fields: ip / mac (مش ipAddress / macAddress)
//   - KPI values موزعة على: root + tcpStats + securityMetric + interfaces[]
// ──────────────────────────────────────────────────────────────
class ApiService {
  static final box = GetStorage();

  // الـ default URL (Android Emulator)
  static const String defaultServerUrl = 'http://10.0.2.2:8080';

  // ── الـ base URL الحالي ──
  static String get baseUrl {
    final saved = box.read<String>('server_url');
    return (saved != null && saved.isNotEmpty) ? saved : defaultServerUrl;
  }

  // ── Headers مع JWT token ──
  static Map<String, String> _authHeaders() {
    final token = box.read<String>('token');
    return {
      'Content-Type': 'application/json',
      if (token != null) 'Authorization': 'Bearer $token',
    };
  }

  static void _handleAuthStatus(int statusCode) {
    if (statusCode == 401 || statusCode == 403) {
      box.remove('token');
      box.remove('user');
      Get.offAll(() => const LoginPage());
    }
  }

  static Future<dynamic> _get(String path) async {
    try {
      final res = await http
          .get(Uri.parse('$baseUrl$path'), headers: _authHeaders())
          .timeout(const Duration(seconds: 15));
      _handleAuthStatus(res.statusCode);
      if (res.statusCode == 200) {
        if (res.body.isEmpty) return null;
        return jsonDecode(res.body);
      }
      if (res.statusCode == 204) return null;
      throw Exception('HTTP ${res.statusCode}');
    } catch (e) {
      debugPrint('GET $path error: $e');
      rethrow;
    }
  }

  static Future<dynamic> _post(String path,
      [Map<String, dynamic>? body]) async {
    try {
      final res = await http
          .post(
            Uri.parse('$baseUrl$path'),
            headers: _authHeaders(),
            body: body != null ? jsonEncode(body) : null,
          )
          .timeout(const Duration(seconds: 15));
      _handleAuthStatus(res.statusCode);
      if (res.statusCode >= 200 && res.statusCode < 300) {
        if (res.body.isEmpty) return null;
        return jsonDecode(res.body);
      }
      throw Exception('HTTP ${res.statusCode}');
    } catch (e) {
      debugPrint('POST $path error: $e');
      rethrow;
    }
  }

  // ════════════════════════════════════════════════════════════
  //  Metrics
  // ════════════════════════════════════════════════════════════

  /// كل الـ devices بآخر metric لكل واحد
  /// يرجع List من MetricData، كل واحد فيه:
  ///   - root: cpuUsage, memoryUsage, diskUsage, status, deviceName, deviceType
  ///   - interfaces: List<InterfaceMetric> (throughputIn/Out, latency, packetLoss, isUp...)
  ///   - tcpStats: passiveOpens, activeOpens, inSegs, outSegs, retransRate...
  ///   - securityMetric: failedLogins, uniqueSourceIPs, halfOpenConnections...
  ///   - arpTable: List من entries (ip, mac)
  ///   - neighbors: List<String>
  static Future<dynamic> getLatestMetrics() => _get('/api/metrics/latest');

  /// تاريخ metrics لجهاز معين (يرجع List من MetricData)
  static Future<dynamic> getDeviceMetrics(String deviceName) =>
      _get('/api/metrics/device/$deviceName');

  /// History لـ metric واحد على جهاز واحد، فترة محددة
  static Future<dynamic> getDeviceMetricHistory(
    String device,
    String metric, {
    String period = '24h',
  }) =>
      _get('/api/metrics/device/$device/history?metric=$metric&period=$period');

  /// ARP table لجهاز
  /// يرجع List من Map<String,String>: [{"ip": "...", "mac": "..."}, ...]
  static Future<dynamic> getDeviceArpTable(String device) =>
      _get('/api/metrics/device/$device/arp');

  // ════════════════════════════════════════════════════════════
  //  AI Predictions
  // ════════════════════════════════════════════════════════════

  static Future<dynamic> getLatestPredictions() =>
      _get('/api/metrics/predictions');

  // ════════════════════════════════════════════════════════════
  //  Attack Sessions (sessionSource — مش source!)
  // ════════════════════════════════════════════════════════════

  static Future<dynamic> getActiveSessions() =>
      _get('/api/attack-sessions/active');

  static Future<dynamic> getAttackHistory(
      {String? device, String? period}) {
    final params = <String>[];
    if (device != null && device.isNotEmpty) params.add('device=$device');
    if (period != null && period.isNotEmpty) params.add('period=$period');
    final qs = params.isEmpty ? '' : '?${params.join('&')}';
    return _get('/api/attack-sessions/history$qs');
  }

  static Future<dynamic> getAttackTimeline({String period = '6h'}) =>
      _get('/api/attack-sessions/timeline?period=$period');

  static Future<dynamic> getRecentSessions() =>
      _get('/api/attack-sessions/recent');

  static Future<dynamic> getSessionStats() =>
      _get('/api/attack-sessions/stats');

  /// Unacknowledged active sessions (للـ Bell badge + dropdown)
  /// نفس React: getUnacknowledged() — /api/attack-sessions/unacknowledged
  static Future<dynamic> getUnacknowledged() =>
      _get('/api/attack-sessions/unacknowledged');

  /// Acknowledge session — يستدعى عند الضغط على ACK button
  /// نفس React: acknowledgeSession(id) — POST /api/attack-sessions/{id}/acknowledge
  static Future<dynamic> acknowledgeSession(int sessionId) =>
      _post('/api/attack-sessions/$sessionId/acknowledge');

  // ════════════════════════════════════════════════════════════
  //  Device Status
  // ════════════════════════════════════════════════════════════

  static Future<dynamic> getDeviceTimeline({String period = '6h'}) =>
      _get('/api/device-status/timeline?period=$period');

  static Future<dynamic> getOfflineDevices() =>
      _get('/api/device-status/offline');

  static Future<dynamic> getAllDevicesStatus() =>
      _get('/api/device/all/status');

  static Future<dynamic> getDeviceStatus(String deviceName) =>
      _get('/api/device/$deviceName/status');

  // ════════════════════════════════════════════════════════════
  //  Interface Status (للـ routers)
  // ════════════════════════════════════════════════════════════

  static Future<dynamic> getInterfaceTimeline(
    String device, {
    String period = '24h',
  }) =>
      _get('/api/interface-status/timeline/$device?period=$period');

  // ════════════════════════════════════════════════════════════
  //  Downtime (للـ SLA tab في Home)
  // ════════════════════════════════════════════════════════════

  /// Attack downtime — يرجع {deviceName: seconds}
  static Future<dynamic> getDowntime({String period = '30d'}) =>
      _get('/api/attack-sessions/downtime?period=$period');

  /// Offline downtime (Device Offline events) — يرجع {deviceName: seconds}
  static Future<dynamic> getOfflineDowntime({String period = '24h'}) =>
      _get('/api/device-status/downtime?period=$period');

  // ════════════════════════════════════════════════════════════
  //  AI Status (Flask via Nginx /ai)
  // ════════════════════════════════════════════════════════════

  /// AI engine status — يرجع {livePredictionActive: bool, ...}
  static Future<dynamic> getAiStatus() => _get('/ai/status');

  // ════════════════════════════════════════════════════════════
  //  Audit Log (للـ Activity Log tab في Security)
  // ════════════════════════════════════════════════════════════

  /// Audit log — يقبل query string (e.g. "?type=ATTACK&period=24h")
  static Future<dynamic> getAuditLog(String queryString) =>
      _get('/api/audit$queryString');

  /// Available metrics for History tab picker
  /// Returns: {system: [...], tcp: [...], security: [...]}
  static Future<dynamic> getAvailableMetrics() =>
      _get('/api/alert-rules/available-metrics');
}
