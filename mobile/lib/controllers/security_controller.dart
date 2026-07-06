import 'dart:async';
import 'package:get/get.dart';

import '../api_service.dart';
import 'websocket_controller.dart';

// ──────────────────────────────────────────────────────────────
//  SecurityController
//  مرجعها: React pages/Security.jsx (TabLive + TabHistory)
//
//  Live tab logic (مهم جداً):
//   1. Fetch predictions via REST once (restPredictions)
//   2. Merge with WebSocket predictions (latestPredictions overrides)
//   3. Per device, build worst status across interfaces:
//      - predictedAttack == 'normal'/null → normal
//      - predictedAttack == 'transit' → transit
//      - else → attack
//      Then cross-check with activeSessions: if no matching active
//      session, flip to normal (predictions stale في DB).
//   4. Add missing devices as 'normal'
//   5. Add trigger/interface/offline rows من activeSessions
//   6. Sort: attack > warning > offline > transit > normal
// ──────────────────────────────────────────────────────────────

// أنواع الصفوف اللي تظهر في Live tab
class LiveRow {
  final String device;
  final String iface;
  final String status; // 'attack' | 'warning' | 'offline' | 'transit' | 'normal'
  final String predictedAttack;
  final num? confidence; // null لو non-AI

  // لو من Trigger Rule / Interface Down / Device Offline
  final bool isTrigger;
  final String? sessionSource; // AI | TRIGGER_RULE | INTERFACE_DOWN | DEVICE_OFFLINE
  final String? severity;
  final Map<String, dynamic>? triggerSession;

  LiveRow({
    required this.device,
    required this.iface,
    required this.status,
    required this.predictedAttack,
    this.confidence,
    this.isTrigger = false,
    this.sessionSource,
    this.severity,
    this.triggerSession,
  });
}

class SecurityController extends GetxController {
  // ── Tabs ──
  var activeTab = 0.obs; // 0=Live, 1=History, 2=Activity Log

  // ── Live tab ──
  var restPredictions = <String, Map<String, dynamic>>{}.obs;
  var activeSessions = <Map<String, dynamic>>[].obs;

  // ── History tab ──
  var historyPeriod = ''.obs; // '' = all time
  var historyDeviceFilter = ''.obs;
  var historyLoading = true.obs;
  var historySessions = <Map<String, dynamic>>[].obs;
  var sessionStats = <String, dynamic>{}.obs;
  var historySubTab = 'attacks'.obs; // 'attacks' | 'alerts'

  // ── Activity Log tab ──
  var auditLog = <Map<String, dynamic>>[].obs;
  var auditLogLoading = false.obs;
  var auditLogTypeFilter = ''.obs;

  // ── Devices list (نفس React ALL_DEVICES) ──
  static const List<String> allDevices = [
    'edge-router',
    'core-router',
    'web-server',
    'dns-server',
    'ftp-server',
    'db-server',
  ];

  Timer? _refreshTimer;
  Worker? _wsListener;

  @override
  void onInit() {
    super.onInit();
    _initialFetch();
    _startAutoRefresh();
    _listenToWebSocket();
  }

  @override
  void onClose() {
    _refreshTimer?.cancel();
    _wsListener?.dispose();
    super.onClose();
  }

  void _initialFetch() {
    _fetchRestPredictions();
    _fetchActiveSessions();
  }

  void _startAutoRefresh() {
    _refreshTimer = Timer.periodic(const Duration(seconds: 15), (_) {
      _fetchActiveSessions();
    });
  }

  // listen to WebSocket predictions changes — refresh active sessions
  void _listenToWebSocket() {
    if (Get.isRegistered<WebSocketController>()) {
      final ws = Get.find<WebSocketController>();
      _wsListener = ever(ws.latestPredictions, (_) {
        _fetchActiveSessions();
      });
    }
  }

  Future<void> refresh() async {
    _fetchRestPredictions();
    await _fetchActiveSessions();
    if (activeTab.value == 1) await _fetchHistory();
    if (activeTab.value == 2) await _fetchAuditLog();
  }

  void changeTab(int idx) {
    if (activeTab.value == idx) return;
    activeTab.value = idx;
    if (idx == 1 && historySessions.isEmpty) _fetchHistory();
    if (idx == 2 && auditLog.isEmpty) _fetchAuditLog();
  }

  // ──────────────────────────────────────────
  //  Live tab fetchers
  // ──────────────────────────────────────────

  Future<void> _fetchRestPredictions() async {
    try {
      final list = await ApiService.getLatestPredictions();
      if (list is List) {
        final map = <String, Map<String, dynamic>>{};
        for (final raw in list) {
          if (raw is Map) {
            final pred = Map<String, dynamic>.from(raw);
            final dev = pred['deviceName'] as String? ?? '';
            final iface = pred['interfaceName'] as String? ?? '';
            final key = '$dev::$iface';
            // نفس React: لو موجود، احتفظ بالأحدث (id أكبر)
            if (!map.containsKey(key) ||
                ((pred['id'] as int? ?? 0) >
                    (map[key]?['id'] as int? ?? 0))) {
              map[key] = pred;
            }
          }
        }
        restPredictions.value = map;
      }
    } catch (_) {}
  }

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

  // ──────────────────────────────────────────
  //  Live tab — getter for sorted rows
  //  هذا قلب الـ Live tab (نفس React بالضبط)
  // ──────────────────────────────────────────
  List<LiveRow> get liveRows {
    // 1. Merge predictions
    // {...restPredictions, ...websocketPredictions}
    final merged = <String, Map<String, dynamic>>{};
    merged.addAll(restPredictions);

    if (Get.isRegistered<WebSocketController>()) {
      final ws = Get.find<WebSocketController>();
      ws.latestPredictions.forEach((key, pred) {
        merged[key] = pred;
      });
    }

    // 2. Build deviceMap (worst status per device)
    final deviceMap = <String, LiveRow>{};
    final priority = {'attack': 0, 'transit': 1, 'normal': 2};

    merged.forEach((key, pred) {
      final parts = key.split('::');
      if (parts.length != 2) return;
      final device = parts[0];
      final iface = parts[1];
      if (!allDevices.contains(device)) return;

      final attackType = pred['predictedAttack'] as String?;
      String status;
      if (attackType == null || attackType == 'normal') {
        status = 'normal';
      } else if (attackType == 'transit') {
        status = 'transit';
      } else {
        status = 'attack';
      }

      // 3. Cross-reference with activeSessions
      if (status == 'attack' || status == 'transit') {
        final hasActive = activeSessions.any((s) =>
            s['deviceName'] == device &&
            s['interfaceName'] == iface &&
            s['attackType'] == attackType &&
            s['status'] == 'ACTIVE');
        if (!hasActive) {
          status = 'normal';
        }
      }

      // Keep worst status per device
      final existing = deviceMap[device];
      if (existing == null ||
          (priority[status] ?? 9) <
              (priority[existing.status] ?? 9)) {
        // Clean confidence لو flipped to normal
        final confidence = status == 'normal'
            ? null
            : (pred['confidence'] as num?);
        final cleanAttack = status == 'normal'
            ? 'normal'
            : (attackType ?? 'normal');

        deviceMap[device] = LiveRow(
          device: device,
          iface: iface,
          status: status,
          predictedAttack: cleanAttack,
          confidence: confidence,
        );
      }
    });

    // 4. Add missing devices as 'normal'
    for (final d in allDevices) {
      if (!deviceMap.containsKey(d)) {
        deviceMap[d] = LiveRow(
          device: d,
          iface: '—',
          status: 'normal',
          predictedAttack: 'normal',
        );
      }
    }

    // 5. Add trigger / interface / offline rows
    final triggerRows = <LiveRow>[];
    for (final s in activeSessions) {
      final src = s['sessionSource'] as String?;
      if (src != 'TRIGGER_RULE' &&
          src != 'INTERFACE_DOWN' &&
          src != 'DEVICE_OFFLINE') continue;

      String status;
      if (src == 'INTERFACE_DOWN' || src == 'DEVICE_OFFLINE') {
        status = 'offline';
      } else if (s['severity'] == 'CRITICAL') {
        status = 'attack';
      } else {
        status = 'warning';
      }

      triggerRows.add(LiveRow(
        device: s['deviceName'] as String? ?? '',
        iface: (s['interfaceName'] as String?) ?? '-',
        status: status,
        predictedAttack: s['attackType'] as String? ?? '',
        confidence: s['avgConfidence'] as num?,
        isTrigger: true,
        sessionSource: src,
        severity: s['severity'] as String?,
        triggerSession: Map<String, dynamic>.from(s),
      ));
    }

    // 6. Combine + sort
    // attack(0) > warning(1) > offline(2) > transit(3) > normal(4)
    final sortPriority = {
      'attack': 0,
      'warning': 1,
      'offline': 2,
      'transit': 3,
      'normal': 4,
    };

    final all = [...triggerRows, ...deviceMap.values];
    all.sort((a, b) {
      final pa = sortPriority[a.status] ?? 9;
      final pb = sortPriority[b.status] ?? 9;
      return pa.compareTo(pb);
    });

    return all;
  }

  // ──────────────────────────────────────────
  //  History tab fetchers
  // ──────────────────────────────────────────

  Future<void> _fetchHistory() async {
    historyLoading.value = true;
    try {
      final results = await Future.wait([
        ApiService.getAttackHistory(
          device: historyDeviceFilter.value.isEmpty
              ? null
              : historyDeviceFilter.value,
          period: historyPeriod.value.isEmpty
              ? null
              : historyPeriod.value,
        ),
        ApiService.getSessionStats(),
      ]);

      if (results[0] is List) {
        historySessions.value = (results[0] as List)
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
      if (results[1] is Map) {
        sessionStats.value =
            Map<String, dynamic>.from(results[1] as Map);
      }
    } catch (_) {}
    historyLoading.value = false;
  }

  void changeHistoryPeriod(String p) {
    if (historyPeriod.value == p) return;
    historyPeriod.value = p;
    _fetchHistory();
  }

  void changeHistoryDevice(String d) {
    if (historyDeviceFilter.value == d) return;
    historyDeviceFilter.value = d;
    _fetchHistory();
  }

  void changeHistorySubTab(String tab) {
    historySubTab.value = tab;
  }

  // Split history (نفس React)
  List<Map<String, dynamic>> get historyAttacks {
    return historySessions.where((s) {
      final src = s['sessionSource'] as String?;
      return src == null || src == 'AI';
    }).toList();
  }

  List<Map<String, dynamic>> get historyAlerts {
    return historySessions.where((s) {
      final src = s['sessionSource'] as String?;
      return src == 'TRIGGER_RULE' ||
          src == 'INTERFACE_DOWN' ||
          src == 'DEVICE_OFFLINE';
    }).toList();
  }

  // ──────────────────────────────────────────
  //  Activity Log fetchers
  // ──────────────────────────────────────────

  Future<void> _fetchAuditLog() async {
    auditLogLoading.value = true;
    try {
      // TODO: nayttäkö audit log endpoint
      // Backend has /api/audit-log
      final type = auditLogTypeFilter.value.isEmpty
          ? null
          : auditLogTypeFilter.value;
      final params = <String>[];
      if (type != null) params.add('type=$type');
      final qs = params.isEmpty ? '' : '?${params.join('&')}';
      final data = await ApiService.getAuditLog(qs);
      if (data is List) {
        auditLog.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
    auditLogLoading.value = false;
  }

  void changeAuditType(String t) {
    if (auditLogTypeFilter.value == t) return;
    auditLogTypeFilter.value = t;
    _fetchAuditLog();
  }
}
