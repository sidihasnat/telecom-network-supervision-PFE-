import 'dart:async';
import 'package:get/get.dart';

import '../api_service.dart';

// ──────────────────────────────────────────────────────────────
//  HomeController
//  مرجعها: React pages/Home.jsx
//
//  يدير 2 tabs:
//   - Tab 0: Overview (4 SummaryCards + Problem Timeline + Live Feed)
//   - Tab 1: SLA (table per device بـ downtime)
//
//  يعتمد على:
//   - getSessionStats() - للـ stats
//   - getRecentSessions() - للـ Live Feed
//   - getAiStatus() - للـ AI status card
//   - getDeviceTimeline(period) - للـ Problem Timeline (status events)
//   - getAttackTimeline(period) - للـ Problem Timeline (attack overlays)
//   - getActiveSessions() - للـ counts (attacks/warnings)
//   - getDowntime(period) - للـ SLA
//   - getOfflineDowntime(period) - للـ SLA
//
//  Auto-refresh كل 30s (نفس React).
// ──────────────────────────────────────────────────────────────
class HomeController extends GetxController {
  // ── الـ tab الحالي ──
  var activeTab = 0.obs;

  // ── Tab 0 state ──
  var timelinePeriod = '6h'.obs;

  // device_status timeline events (List من {deviceName, status, startedAt, endedAt, ...})
  var timeline = <Map<String, dynamic>>[].obs;

  // attack_session timeline (overlapping window)
  var attackTimeline = <Map<String, dynamic>>[].obs;

  // active sessions (للـ counts في SummaryCards)
  var activeSessions = <Map<String, dynamic>>[].obs;

  // recent sessions (للـ Live Feed — أول 8)
  var recentSessions = <Map<String, dynamic>>[].obs;

  // AI status: 'Live' / 'Stopped' / 'Disconnected' / '—'
  var aiStatus = '—'.obs;

  // ── Tab 1 state (SLA) ──
  var slaPeriod = '24h'.obs;
  var slaLoading = false.obs;
  var attackDowntime = <String, dynamic>{}.obs;
  var offlineDowntime = <String, dynamic>{}.obs;

  // قائمة الأجهزة الستة (نفس React DEVICES const)
  static const List<String> devices = [
    'edge-router',
    'core-router',
    'web-server',
    'dns-server',
    'ftp-server',
    'db-server',
  ];

  Timer? _refreshTimer;

  @override
  void onInit() {
    super.onInit();
    _initialFetch();
    _startAutoRefresh();
  }

  @override
  void onClose() {
    _refreshTimer?.cancel();
    super.onClose();
  }

  // ── Initial fetch (نفس useEffect Mount في React) ──
  void _initialFetch() {
    _fetchActiveSessions();
    _fetchRecentSessions();
    _fetchAiStatus();
    _fetchTimelines();
  }

  // ── Auto-refresh (React useEffect with setInterval 30s) ──
  void _startAutoRefresh() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _fetchTimelines();
      _fetchActiveSessions();
      _fetchRecentSessions();
      _fetchAiStatus();
    });
  }

  Future<void> refresh() async {
    _initialFetch();
    if (activeTab.value == 1) {
      await _fetchSla();
    }
  }

  void changePeriod(String p) {
    if (timelinePeriod.value == p) return;
    timelinePeriod.value = p;
    _fetchTimelines();
  }

  void changeTab(int idx) {
    if (activeTab.value == idx) return;
    activeTab.value = idx;
    if (idx == 1) {
      _fetchSla();
    }
  }

  void changeSlaPeriod(String p) {
    if (slaPeriod.value == p) return;
    slaPeriod.value = p;
    _fetchSla();
  }

  // ── Fetchers ──

  Future<void> _fetchActiveSessions() async {
    try {
      final data = await ApiService.getActiveSessions();
      if (data is List) {
        activeSessions.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
  }

  Future<void> _fetchRecentSessions() async {
    try {
      final data = await ApiService.getRecentSessions();
      if (data is List) {
        recentSessions.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
  }

  Future<void> _fetchAiStatus() async {
    try {
      final data = await ApiService.getAiStatus();
      if (data is Map && data['livePredictionActive'] == true) {
        aiStatus.value = 'Live';
      } else {
        aiStatus.value = 'Stopped';
      }
    } catch (_) {
      aiStatus.value = 'Disconnected';
    }
  }

  Future<void> _fetchTimelines() async {
    try {
      final results = await Future.wait([
        ApiService.getDeviceTimeline(period: timelinePeriod.value),
        ApiService.getAttackTimeline(period: timelinePeriod.value),
      ]);
      if (results[0] is List) {
        timeline.value = (results[0] as List)
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
      if (results[1] is List) {
        attackTimeline.value = (results[1] as List)
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
  }

  Future<void> _fetchSla() async {
    slaLoading.value = true;
    try {
      final results = await Future.wait([
        ApiService.getDowntime(period: slaPeriod.value).catchError((_) => {}),
        ApiService.getOfflineDowntime(period: slaPeriod.value)
            .catchError((_) => {}),
      ]);
      if (results[0] is Map) {
        attackDowntime.value = Map<String, dynamic>.from(results[0] as Map);
      }
      if (results[1] is Map) {
        offlineDowntime.value = Map<String, dynamic>.from(results[1] as Map);
      }
    } catch (_) {}
    slaLoading.value = false;
  }

  // ──────────────────────────────────────────────────────────
  //  Computed values (نفس منطق React)
  // ──────────────────────────────────────────────────────────

  /// عدد الأجهزة المهاجَمَة (red).
  /// نفس logic React بالضبط:
  ///   AI || (TRIGGER_RULE && CRITICAL) → attackDevices
  Set<String> get attackDevicesSet {
    final result = <String>{};
    for (final s in activeSessions) {
      if (s['attackType'] == 'transit') continue;

      final source = s['sessionSource'] as String?;
      final severity = s['severity'] as String?;
      final isAi = source == null || source == 'AI';
      final isCritTrigger = source == 'TRIGGER_RULE' && severity == 'CRITICAL';

      if (isAi || isCritTrigger) {
        final dev = s['deviceName'] as String?;
        if (dev != null) result.add(dev);
      }
    }
    return result;
  }

  /// عدد الأجهزة في warning (yellow).
  /// TRIGGER_RULE && !CRITICAL — لكن لو الجهاز موجود في attacks → نحذفه.
  Set<String> get warningDevicesSet {
    final result = <String>{};
    for (final s in activeSessions) {
      if (s['attackType'] == 'transit') continue;

      final source = s['sessionSource'] as String?;
      final severity = s['severity'] as String?;
      final isWarnTrigger =
          source == 'TRIGGER_RULE' && severity != 'CRITICAL';

      if (isWarnTrigger) {
        final dev = s['deviceName'] as String?;
        if (dev != null) result.add(dev);
      }
    }
    // لو الجهاز في attacks، احذفه من warnings (نفس React)
    for (final d in attackDevicesSet) {
      result.remove(d);
    }
    return result;
  }

  /// الأجهزة الطبيعية = الكل - attack - warning
  int get normalCount {
    final attacks = attackDevicesSet;
    final warnings = warningDevicesSet;
    return devices.where((d) => !attacks.contains(d) && !warnings.contains(d))
        .length;
  }
}
