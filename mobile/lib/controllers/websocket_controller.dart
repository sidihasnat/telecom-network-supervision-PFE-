import 'dart:async';
import 'dart:convert';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../api_service.dart';
import 'notification_controller.dart';

// ──────────────────────────────────────────────────────────────
//  WebSocketController
//  STOMP over SockJS connection للـ /ws endpoint من Spring Boot.
//
//  Topics:
//   - /topic/metrics       → metrics live updates
//   - /topic/predictions   → AI predictions live
//   - /topic/notifications → NEW_ATTACK / ATTACK_ENDED / ESCALATION
//   - /topic/device-status → ONLINE / OFFLINE events
//
//  Auto-reconnect كل 5 ثواني لو الاتصال انقطع.
//
//  ──────────────────────────────────────────
//  🔧 الإصلاح الجوهري في هذا الـ batch:
//  ──────────────────────────────────────────
//  bug في GetX: `RxMap[key] = value` لا يطلق الـ ever() listeners
//  دائماً. كانت النتيجة:
//   - Monitoring/DeviceDetail لا تتحدث live (REST polling فقط كل 30s)
//   - الـ drawer يبقى أخضر حتى عند هجوم
//
//  الحل: استدعاء `.refresh()` بعد كل mutation للـ RxMap.
//
//  ──────────────────────────────────────────
//  🔔 إضافة notification push:
//  ──────────────────────────────────────────
//  عند وصول /topic/notifications، نمررها لـ NotificationController
//  ليبني toastAlerts الدائمة (بنفس الـ id حتى ATTACK_ENDED تزيله).
// ──────────────────────────────────────────────────────────────
class WebSocketController extends GetxController {
  StompClient? _client;
  final box = GetStorage();

  // ── Reactive state ──
  var isConnected = false.obs;

  // آخر metric لكل جهاز
  var latestMetrics = <String, Map<String, dynamic>>{}.obs;

  // آخر prediction لكل (device::interface)
  var latestPredictions = <String, Map<String, dynamic>>{}.obs;

  // وقت آخر metric لكل جهاز (للـ offline detection)
  final Map<String, int> deviceLastSeen = {};

  // ── Callbacks (للصفحات اللي تستمع لها) ──
  final List<void Function(Map<String, dynamic>)> _metricCallbacks = [];

  void onMetric(void Function(Map<String, dynamic>) cb) =>
      _metricCallbacks.add(cb);

  Timer? _offlineTimer;
  Timer? _unackedPollTimer;
  static const int _offlineThresholdMs = 45000;

  @override
  void onInit() {
    super.onInit();
    connect();
    _startOfflineTimer();
    _startUnackedPolling();
  }

  @override
  void onClose() {
    disconnect();
    _offlineTimer?.cancel();
    _unackedPollTimer?.cancel();
    super.onClose();
  }

  String get _wsUrl {
    final saved = box.read<String>('server_url');
    final base = (saved != null && saved.isNotEmpty)
        ? saved
        : 'http://10.0.2.2:8080';
    return '$base/ws';
  }

  void connect() {
    if (_client != null) return;

    _client = StompClient(
      config: StompConfig.sockJS(
        url: _wsUrl,
        onConnect: _onConnect,
        onDisconnect: (_) {
          isConnected.value = false;
          Future.delayed(const Duration(seconds: 5), () {
            if (_client == null) connect();
          });
        },
        onStompError: (_) => isConnected.value = false,
        onWebSocketError: (_) => isConnected.value = false,
        reconnectDelay: const Duration(seconds: 5),
      ),
    );

    _client!.activate();
  }

  void disconnect() {
    _client?.deactivate();
    _client = null;
    isConnected.value = false;
  }

  void reconnect() {
    disconnect();
    connect();
  }

  void _onConnect(StompFrame frame) {
    isConnected.value = true;

    // /topic/metrics
    _client!.subscribe(
      destination: '/topic/metrics',
      callback: (f) {
        if (f.body == null) return;
        try {
          final data = jsonDecode(f.body!) as Map<String, dynamic>;
          final deviceName = data['deviceName'] as String? ?? '';
          if (deviceName.isEmpty) return;

          // 🔧 FIX: GetX RxMap key mutations لا تطلق ever() دائماً.
          // نضيف refresh() لضمان أن جميع الـ listeners (Obx + ever)
          // تطلق ويتحدث الـ UI.
          latestMetrics[deviceName] = data;
          latestMetrics.refresh();

          deviceLastSeen[deviceName] =
              DateTime.now().millisecondsSinceEpoch;
          for (final cb in _metricCallbacks) {
            cb(data);
          }
        } catch (_) {}
      },
    );

    // /topic/predictions
    _client!.subscribe(
      destination: '/topic/predictions',
      callback: (f) {
        if (f.body == null) return;
        try {
          final data = jsonDecode(f.body!) as Map<String, dynamic>;
          final device = data['deviceName'] as String? ?? '';
          final iface = data['interfaceName'] as String? ?? '';
          final key = '$device::$iface';

          // 🔧 نفس الـ fix
          latestPredictions[key] = data;
          latestPredictions.refresh();
        } catch (_) {}
      },
    );

    // /topic/notifications
    _client!.subscribe(
      destination: '/topic/notifications',
      callback: (f) {
        if (f.body == null) return;
        try {
          final data = jsonDecode(f.body!) as Map<String, dynamic>;
          // 🔔 تمرير للـ NotificationController (toastAlerts + history)
          if (Get.isRegistered<NotificationController>()) {
            Get.find<NotificationController>().onNotification(data);
          }
          final type = data['type'] as String?;
          if (type == 'NEW_ATTACK' || type == 'ATTACK_ENDED') {
            _fetchUnacked();
          }
        } catch (_) {}
      },
    );

    // /topic/device-status
    _client!.subscribe(
      destination: '/topic/device-status',
      callback: (f) {
        if (f.body == null) return;
        try {
          final data = jsonDecode(f.body!) as Map<String, dynamic>;
          final deviceName = data['deviceName'] as String? ?? '';
          final status = data['status'] as String? ?? '';

          if (status == 'OFFLINE') {
            deviceLastSeen[deviceName] = 1;
            latestMetrics.remove(deviceName);
            latestMetrics.refresh();
          } else if (status == 'ONLINE') {
            deviceLastSeen[deviceName] =
                DateTime.now().millisecondsSinceEpoch;
          }
        } catch (_) {}
      },
    );

    // Initial unacked fetch بعد الاتصال
    _fetchUnacked();
  }

  void _startOfflineTimer() {
    _offlineTimer = Timer.periodic(const Duration(seconds: 10), (_) {
      final now = DateTime.now().millisecondsSinceEpoch;
      final toRemove = <String>[];
      for (final entry in latestMetrics.entries) {
        final lastSeen = deviceLastSeen[entry.key];
        if (lastSeen != null &&
            (lastSeen == 1 || (now - lastSeen) > _offlineThresholdMs)) {
          toRemove.add(entry.key);
        }
      }
      if (toRemove.isNotEmpty) {
        for (final k in toRemove) {
          latestMetrics.remove(k);
        }
        latestMetrics.refresh();
      }
    });
  }

  void _startUnackedPolling() {
    _unackedPollTimer =
        Timer.periodic(const Duration(seconds: 30), (_) => _fetchUnacked());
  }

  Future<void> _fetchUnacked() async {
    if (!Get.isRegistered<NotificationController>()) return;
    // لا نطلب لو ما في token (المستخدم لم يسجل دخول)
    final token = box.read<String>('token');
    if (token == null || token.isEmpty) return;

    try {
      final data = await ApiService.getUnacknowledged();
      if (data is List) {
        Get.find<NotificationController>().unackedSessions.value = data
            .whereType<Map>()
            .map((e) => Map<String, dynamic>.from(e))
            .toList();
      }
    } catch (_) {}
  }

  bool isDeviceOffline(String deviceName) {
    final lastSeen = deviceLastSeen[deviceName];
    if (lastSeen == 1) return true;
    if (lastSeen == null) return false;
    return (DateTime.now().millisecondsSinceEpoch - lastSeen) > 45000;
  }
}
