import 'dart:async';
import 'package:get/get.dart';

import '../api_service.dart';

// ──────────────────────────────────────────────────────────────
//  AiStatusController
//  مرجعها: React Sidebar.jsx (lines 47-56 + 105-110)
//
//  يستطلع /ai/status كل 15 ثانية ويعرض حالة Flask AI engine:
//    - 'live'         → AI live prediction running (livePredictionActive=true)
//    - 'stopped'      → models loaded لكن live prediction معطل
//    - 'disconnected' → ما قدر يتصل بالـ endpoint (Flask down أو network)
//    - 'unknown'      → الحالة الأولية قبل أول استطلاع
//
//  يستبدل WebSocket online/offline indicator في AppBar.
// ──────────────────────────────────────────────────────────────
class AiStatusController extends GetxController {
  // 'live' | 'stopped' | 'disconnected' | 'unknown'
  var status = 'unknown'.obs;

  Timer? _pollTimer;

  @override
  void onInit() {
    super.onInit();
    _fetchOnce();
    _pollTimer = Timer.periodic(
        const Duration(seconds: 15), (_) => _fetchOnce());
  }

  @override
  void onClose() {
    _pollTimer?.cancel();
    super.onClose();
  }

  Future<void> _fetchOnce() async {
    try {
      final data = await ApiService.getAiStatus();
      if (data is Map && data['livePredictionActive'] == true) {
        status.value = 'live';
      } else {
        status.value = 'stopped';
      }
    } catch (_) {
      status.value = 'disconnected';
    }
  }

  Future<void> refresh() async => _fetchOnce();
}
