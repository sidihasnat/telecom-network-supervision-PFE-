import 'dart:async';
import 'package:get/get.dart';

import '../api_service.dart';
import 'websocket_controller.dart';

// ──────────────────────────────────────────────────────────────
//  MonitoringController
//  مرجعها: React pages/Monitoring.jsx
//
//  المنطق نفسه:
//   - يقرأ latestMetrics من WebSocketController
//      (الـ React يقرأها من WebSocket /topic/metrics)
//   - بدون auto-refresh منفصل (الـ WebSocket يعمل ذلك)
//   - كـ fallback: REST polling كل 30s لو WebSocket ميشتغلش
//
//  الـ data structure: deviceMetrics = {deviceName: metric_obj}
// ──────────────────────────────────────────────────────────────
class MonitoringController extends GetxController {
  // الـ metrics الحالية لكل جهاز
  // يجي من WebSocketController.latestMetrics
  // (إذا الـ WebSocket مش متصل، نعتمد على REST كـ backup)
  var deviceMetrics = <String, Map<String, dynamic>>{}.obs;

  Timer? _restFallbackTimer;
  Worker? _wsListener;

  @override
  void onInit() {
    super.onInit();
    _connectToWebSocket();
    _startRestFallback();
  }

  @override
  void onClose() {
    _restFallbackTimer?.cancel();
    _wsListener?.dispose();
    super.onClose();
  }

  /// نستخدم latestMetrics من WebSocketController إذا متاح
  /// (هذا يعطينا live updates نفس React)
  void _connectToWebSocket() {
    if (Get.isRegistered<WebSocketController>()) {
      final ws = Get.find<WebSocketController>();
      // initial sync
      deviceMetrics.value = Map.from(ws.latestMetrics);
      // listen for changes
      _wsListener = ever(ws.latestMetrics, (Map<String, Map<String, dynamic>> m) {
        deviceMetrics.value = Map.from(m);
      });
    }
  }

  /// REST fallback — لو WebSocket ميشتغلش (e.g. أول فتح للصفحة)
  /// نجلب من /api/metrics/latest كل 30s
  void _startRestFallback() {
    _fetchFromRest();
    _restFallbackTimer = Timer.periodic(const Duration(seconds: 30), (_) {
      _fetchFromRest();
    });
  }

  Future<void> refresh() async {
    await _fetchFromRest();
  }

  Future<void> _fetchFromRest() async {
    try {
      final data = await ApiService.getLatestMetrics();
      if (data is List) {
        final map = <String, Map<String, dynamic>>{};
        for (final raw in data) {
          if (raw is Map) {
            final m = Map<String, dynamic>.from(raw);
            final name = m['deviceName'] as String?;
            if (name != null) map[name] = m;
          }
        }
        // فقط نحدّث لو الـ WebSocket مش متصل (أو أول مرة)
        if (deviceMetrics.isEmpty ||
            !Get.isRegistered<WebSocketController>() ||
            !Get.find<WebSocketController>().isConnected.value) {
          deviceMetrics.value = map;
        }
      }
    } catch (_) {}
  }
}
