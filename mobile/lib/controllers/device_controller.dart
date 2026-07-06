import 'dart:async';
import 'package:get/get.dart';

import '../api_service.dart';
import 'websocket_controller.dart';

// ──────────────────────────────────────────────────────────────
//  DeviceController
//  مرجعها: React components/SidePanel.jsx
//
//  Tabs:
//   0. Overview — current metrics + sparklines + interfaces
//   1. AI Result — predictions + topFeatures + MITRE
//   2. KPI — device-specific metrics + sparklines
//   3. Interfaces (router only) — UP/DOWN timeline
//   4. History — historical chart of one metric
//   5. Counters — raw TCP+Security counters
//   6. Properties — name/type/interfaces/neighbors/ARP
//
//  المصادر:
//   - getDeviceMetrics(name) — last N readings (history للـ sparklines)
//   - WebSocketController.latestMetrics[name] — current live metric
//   - getInterfaceTimeline(name, period) — Interfaces tab
//   - getDeviceMetricHistory(name, metric, period) — History tab
//   - getDeviceArpTable(name) — ARP table
//   - getAvailableMetrics() — للـ history metric picker
// ──────────────────────────────────────────────────────────────
class DeviceController extends GetxController {
  final String deviceName;
  DeviceController(this.deviceName);

  // ── Live metric (يجي من WebSocket أو REST fallback) ──
  var latestMetric = Rxn<Map<String, dynamic>>();

  // آخر 20 قراءة (للـ sparklines في Overview/KPI)
  var metricsHistory = <Map<String, dynamic>>[].obs;

  // Predictions الخاصة بهذا الجهاز (key = iface)
  var devicePredictions = <String, Map<String, dynamic>>{}.obs;

  // Tab state
  var activeTab = 0.obs;

  // Offline detection (نفس React: 45s threshold)
  var isOffline = false.obs;

  // ── Interfaces tab ──
  var interfacesPeriod = '24h'.obs;
  var interfaceEvents = <Map<String, dynamic>>[].obs;
  var interfacesLoading = false.obs;

  // ── History tab ──
  var historyMetric = 'cpuUsage'.obs;
  var historyPeriod = '24h'.obs;
  var historyData = Rxn<Map<String, dynamic>>();
  var historyLoading = false.obs;
  var availableMetrics = <String, List<Map<String, dynamic>>>{}.obs;

  // ── Properties tab (ARP) ──
  var arpEntries = <Map<String, dynamic>>[].obs;
  var arpLoading = false.obs;

  Timer? _refreshTimer;
  Timer? _offlineTimer;
  Worker? _wsListener;
  Worker? _wsPredListener;

  @override
  void onInit() {
    super.onInit();
    _initialFetch();
    _startWatchers();
    _connectWebSocket();
  }

  @override
  void onClose() {
    _refreshTimer?.cancel();
    _offlineTimer?.cancel();
    _wsListener?.dispose();
    _wsPredListener?.dispose();
    super.onClose();
  }

  Future<void> _initialFetch() async {
    await Future.wait([
      _fetchHistoryMetrics(),
      _fetchInterfaceTimeline(),
      _fetchAvailableMetrics(),
      _fetchHistoryChart(),
      _fetchArp(),
    ]);
  }

  void _startWatchers() {
    // Auto-refresh كل 30s
    _refreshTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _fetchHistoryMetrics();
      _fetchInterfaceTimeline();
    });

    // Check offline كل 5s (نفس React)
    _offlineTimer = Timer.periodic(const Duration(seconds: 5), (_) {
      _checkOffline();
    });
  }

  void _connectWebSocket() {
    if (Get.isRegistered<WebSocketController>()) {
      final ws = Get.find<WebSocketController>();

      // Initial sync
      _syncFromWebSocket(ws);

      // Listen for changes
      _wsListener = ever(ws.latestMetrics, (_) => _syncFromWebSocket(ws));
      _wsPredListener =
          ever(ws.latestPredictions, (_) => _syncPredictionsFromWs(ws));
    }
  }

  void _syncFromWebSocket(WebSocketController ws) {
    final metric = ws.latestMetrics[deviceName];
    if (metric != null) {
      latestMetric.value = metric;
      // Append to history if it's a new metric
      final lastId = metricsHistory.isEmpty
          ? null
          : metricsHistory.last['id'];
      if (lastId == null || metric['id'] != lastId) {
        final updated = [...metricsHistory, metric];
        if (updated.length > 20) {
          updated.removeAt(0);
        }
        metricsHistory.value = updated;
      }
    }
    _checkOffline();
  }

  void _syncPredictionsFromWs(WebSocketController ws) {
    final result = <String, Map<String, dynamic>>{};
    ws.latestPredictions.forEach((key, pred) {
      if (key.startsWith('$deviceName::')) {
        final iface = key.substring('$deviceName::'.length);
        result[iface] = pred;
      }
    });
    devicePredictions.value = result;
  }

  void _checkOffline() {
    if (Get.isRegistered<WebSocketController>()) {
      final ws = Get.find<WebSocketController>();
      isOffline.value = ws.isDeviceOffline(deviceName);
    }
  }

  // ──────────────────────────────────────────
  //  Fetchers
  // ──────────────────────────────────────────

  Future<void> _fetchHistoryMetrics() async {
    try {
      final data = await ApiService.getDeviceMetrics(deviceName);
      if (data is List && data.isNotEmpty) {
        final list = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
        // Keep last 20 (نفس React .slice(-20))
        final keep = list.length > 20
            ? list.sublist(list.length - 20)
            : list;
        metricsHistory.value = keep;
        // أحدث metric (لو مفيش WebSocket)
        if (latestMetric.value == null && keep.isNotEmpty) {
          latestMetric.value = keep.last;
        }
      }
    } catch (_) {}
  }

  Future<void> _fetchInterfaceTimeline() async {
    if (!deviceName.contains('router')) return;
    interfacesLoading.value = true;
    try {
      final data = await ApiService.getInterfaceTimeline(
        deviceName,
        period: interfacesPeriod.value,
      );
      if (data is List) {
        interfaceEvents.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
    interfacesLoading.value = false;
  }

  Future<void> _fetchAvailableMetrics() async {
    try {
      final data = await ApiService.getAvailableMetrics();
      if (data is Map) {
        final result = <String, List<Map<String, dynamic>>>{};
        data.forEach((key, value) {
          if (value is List) {
            result[key.toString()] = value
                .whereType<Map>()
                .map((e) => Map<String, dynamic>.from(e))
                .toList();
          }
        });
        availableMetrics.value = result;
      }
    } catch (_) {}
  }

  Future<void> _fetchHistoryChart() async {
    historyLoading.value = true;
    try {
      final data = await ApiService.getDeviceMetricHistory(
        deviceName,
        historyMetric.value,
        period: historyPeriod.value,
      );
      if (data is Map) {
        historyData.value = Map<String, dynamic>.from(data);
      } else {
        historyData.value = null;
      }
    } catch (_) {
      historyData.value = null;
    }
    historyLoading.value = false;
  }

  Future<void> _fetchArp() async {
    arpLoading.value = true;
    try {
      final data = await ApiService.getDeviceArpTable(deviceName);
      if (data is List) {
        arpEntries.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
    arpLoading.value = false;
  }

  // ──────────────────────────────────────────
  //  Public actions
  // ──────────────────────────────────────────

  Future<void> refresh() async {
    await _initialFetch();
  }

  void changeTab(int idx) {
    activeTab.value = idx;
  }

  void changeInterfacesPeriod(String p) {
    if (interfacesPeriod.value == p) return;
    interfacesPeriod.value = p;
    _fetchInterfaceTimeline();
  }

  void changeHistoryMetric(String m) {
    if (historyMetric.value == m) return;
    historyMetric.value = m;
    _fetchHistoryChart();
  }

  void changeHistoryPeriod(String p) {
    if (historyPeriod.value == p) return;
    historyPeriod.value = p;
    _fetchHistoryChart();
  }

  // ──────────────────────────────────────────
  //  KPI helper — يبحث في root + tcpStats + securityMetric + interfaces[]
  // ──────────────────────────────────────────
  num? extractMetric(String key) {
    final m = latestMetric.value;
    if (m == null) return null;

    // 1. root
    if (m[key] != null && m[key] is num) return m[key] as num;

    // 2. tcpStats
    final tcp = m['tcpStats'];
    if (tcp is Map && tcp[key] != null && tcp[key] is num) {
      return tcp[key] as num;
    }

    // 3. securityMetric
    final sec = m['securityMetric'];
    if (sec is Map && sec[key] != null && sec[key] is num) {
      return sec[key] as num;
    }

    // 4. interfaces (نأخذ أول قيمة غير صفر)
    final ifaces = m['interfaces'];
    if (ifaces is List) {
      for (final iface in ifaces) {
        if (iface is Map && iface[key] != null && iface[key] is num) {
          final val = iface[key] as num;
          if (val != 0) return val;
        }
      }
    }
    return null;
  }
}
