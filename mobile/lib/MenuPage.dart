import 'package:flutter/material.dart';
import 'package:flutter_zoom_drawer/flutter_zoom_drawer.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import 'app_theme.dart';
import 'LoginPage.dart';
import 'DeviceDetailPage.dart';
import 'controllers/websocket_controller.dart';
import 'controllers/ai_status_controller.dart';

// ──────────────────────────────────────────────────────────────
//  MenuPage — قائمة الأجهزة مع status dots
//  مرجعها: React components/Sidebar.jsx
//
//  ── الإصلاح في هذا الـ batch ──
//  المشكلة السابقة: لو MenuPage بنا قبل WebSocketController
//  (ZoomDrawer يبني menuScreen + mainScreen معاً)، فالـ Get.find
//  يفشل، Obx ترجع SizedBox، ولا تشترك بأي Rx، ولا تعيد البناء.
//
//  الإصلاحان:
//  1. WebSocketController الآن مسجَّل في HomeNavigationPage قبل
//     ZoomDrawer.
//  2. أزلنا التعقيد (_refreshTrigger + recursive scheduler) —
//     Obx الآن يقرأ ws.latestMetrics.value و
//     ws.latestPredictions.value مباشرة (يضمن Rx subscription).
// ──────────────────────────────────────────────────────────────

const Map<String, List<_DeviceEntry>> _kDeviceGroups = {
  'Routeurs': [
    _DeviceEntry(name: 'edge-router'),
    _DeviceEntry(name: 'core-router'),
  ],
  'Serveurs': [
    _DeviceEntry(name: 'web-server'),
    _DeviceEntry(name: 'dns-server'),
    _DeviceEntry(name: 'ftp-server'),
    _DeviceEntry(name: 'db-server'),
  ],
  'Gestion': [
    _DeviceEntry(name: 'supervision-app'),
  ],
  'Clients': [
    _DeviceEntry(name: 'pc1'),
    _DeviceEntry(name: 'pc2'),
  ],
};

class _DeviceEntry {
  final String name;
  const _DeviceEntry({required this.name});
}

class MenuPage extends StatelessWidget {
  const MenuPage({super.key});

  @override
  Widget build(BuildContext context) {
    final box = GetStorage();
    final user = box.read('user') as Map<String, dynamic>?;
    final username = user?['username'] ?? 'User';
    final role = user?['role'] ?? 'USER';

    return Scaffold(
      backgroundColor: AppColors.bgCard,
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 12, 20, 0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  GestureDetector(
                    onTap: () => ZoomDrawer.of(context)!.close(),
                    child: const Icon(Icons.arrow_back,
                        color: AppColors.textPrimary, size: 28),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    children: [
                      CircleAvatar(
                        radius: 22,
                        backgroundColor:
                            AppColors.accent.withOpacity(0.2),
                        child: const Icon(Icons.person,
                            color: AppColors.accent, size: 26),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment:
                              CrossAxisAlignment.start,
                          children: [
                            Text(
                              username.toString(),
                              style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontSize: 15,
                                fontWeight: FontWeight.bold,
                              ),
                              overflow: TextOverflow.ellipsis,
                            ),
                            const SizedBox(height: 2),
                            Container(
                              padding: const EdgeInsets.symmetric(
                                  horizontal: 6, vertical: 1),
                              decoration: BoxDecoration(
                                color:
                                    AppColors.accent.withOpacity(0.15),
                                borderRadius:
                                    BorderRadius.circular(3),
                              ),
                              child: Text(
                                role.toString(),
                                style: const TextStyle(
                                  color: AppColors.accent,
                                  fontSize: 9,
                                  fontWeight: FontWeight.w600,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
            ),

            const SizedBox(height: 14),
            const Divider(color: AppColors.borderLight, height: 1),

            // Device list — single Obx subscribes once for all
            Expanded(child: const _DeviceList()),

            const Divider(color: AppColors.borderLight, height: 1),
            const _AiStatusFooter(),
            const Divider(color: AppColors.borderLight, height: 1),

            Padding(
              padding: const EdgeInsets.fromLTRB(8, 4, 8, 12),
              child: Column(
                children: [
                  ListTile(
                    contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12, vertical: 0),
                    dense: true,
                    visualDensity:
                        const VisualDensity(vertical: -2),
                    leading: const Icon(Icons.logout_outlined,
                        color: AppColors.danger, size: 20),
                    title: const Text(
                      'Déconnexion',
                      style: TextStyle(
                        color: AppColors.danger,
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    onTap: _logout,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _logout() {
    final box = GetStorage();
    box.remove('token');
    box.remove('user');
    Get.offAll(() => const LoginPage());
  }


}

// ──────────────────────────────────────────────────────────────
//  Device list — يعيد البناء عند:
//   - تحديث latestMetrics (offline detection)
//   - تحديث latestPredictions (attack/transit status)
// ──────────────────────────────────────────────────────────────
class _DeviceList extends StatelessWidget {
  const _DeviceList();

  @override
  Widget build(BuildContext context) {
    if (!Get.isRegistered<WebSocketController>()) {
      // safety net — يعرض الأجهزة بدون status لو WS لم يُسجَّل بعد
      return _buildList(null);
    }
    final ws = Get.find<WebSocketController>();

    // Single Obx — يقرأ كلا RxMaps via .value (يضمن subscription)
    return Obx(() {
      // ⚠️ لازم نقرأ .value صراحة عشان Obx يشترك
      final metrics = ws.latestMetrics.value;
      final preds = ws.latestPredictions.value;
      return _buildList(_StatusContext(ws: ws, metrics: metrics, preds: preds));
    });
  }

  Widget _buildList(_StatusContext? ctx) {
    return SingleChildScrollView(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: _kDeviceGroups.entries.map((group) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Padding(
                padding:
                    const EdgeInsets.fromLTRB(20, 10, 20, 6),
                child: Text(
                  group.key.toUpperCase(),
                  style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 10,
                    letterSpacing: 1.2,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
              ...group.value.map(
                (device) =>
                    _DeviceTile(device: device, ctx: ctx),
              ),
            ],
          );
        }).toList(),
      ),
    );
  }
}

class _StatusContext {
  final WebSocketController ws;
  final Map<String, Map<String, dynamic>> metrics;
  final Map<String, Map<String, dynamic>> preds;
  _StatusContext(
      {required this.ws,
      required this.metrics,
      required this.preds});
}

class _DeviceTile extends StatelessWidget {
  final _DeviceEntry device;
  final _StatusContext? ctx;

  const _DeviceTile({required this.device, required this.ctx});

  @override
  Widget build(BuildContext context) {
    final status = _computeStatus();
    return InkWell(
      onTap: () => _openDevice(context),
      child: Padding(
        padding:
            const EdgeInsets.symmetric(horizontal: 20, vertical: 7),
        child: Row(
          children: [
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: _statusColor(status),
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                device.name,
                style: TextStyle(
                  color: status == 'offline'
                      ? AppColors.textMuted
                      : AppColors.textPrimary,
                  fontSize: 13,
                  fontFamily: 'monospace',
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            if (status == 'offline')
              const Text('hors ligne',
                  style: TextStyle(
                      color: AppColors.textMuted, fontSize: 9)),
          ],
        ),
      ),
    );
  }

  String _computeStatus() {
    if (ctx == null) return 'normal';
    if (ctx!.ws.isDeviceOffline(device.name)) return 'offline';

    String worst = 'normal';
    final prefix = '${device.name}::';
    for (final entry in ctx!.preds.entries) {
      if (!entry.key.startsWith(prefix)) continue;
      final attack = entry.value['predictedAttack'] as String?;
      if (attack == null || attack == 'normal') continue;
      if (attack == 'transit') {
        if (worst != 'attack') worst = 'transit';
      } else {
        worst = 'attack';
      }
    }
    return worst;
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'attack':
        return AppColors.danger;
      case 'transit':
        return AppColors.warning;
      case 'offline':
        return AppColors.statusGrey;
      default:
        return AppColors.success;
    }
  }

  void _openDevice(BuildContext context) {
    ZoomDrawer.of(context)!.close();
    Future.delayed(const Duration(milliseconds: 250), () {
      Get.to(() => DeviceDetailPage(deviceName: device.name));
    });
  }
}

// ──────────────────────────────────────────────────────────────
//  AI Engine status footer
// ──────────────────────────────────────────────────────────────
class _AiStatusFooter extends StatelessWidget {
  const _AiStatusFooter();

  @override
  Widget build(BuildContext context) {
    if (!Get.isRegistered<AiStatusController>()) {
      return const Padding(
        padding: EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        child: Text('AI: ...',
            style: TextStyle(
                color: AppColors.textMuted, fontSize: 11)),
      );
    }
    final ai = Get.find<AiStatusController>();

    return Obx(() {
      final s = ai.status.value;
      Color dotColor;
      Color textColor;
      String label;
      switch (s) {
        case 'live':
          dotColor = AppColors.success;
          textColor = AppColors.success;
          label = 'Direct';
          break;
        case 'stopped':
          dotColor = AppColors.warning;
          textColor = AppColors.warning;
          label = 'Arrêté';
          break;
        case 'disconnected':
          dotColor = AppColors.danger;
          textColor = AppColors.danger;
          label = 'Déconnecté';
          break;
        default:
          dotColor = AppColors.statusGrey;
          textColor = AppColors.textMuted;
          label = 'Inconnu';
      }
      return Padding(
        padding:
            const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
        child: Row(
          children: [
            const Text('Détection :',
                style: TextStyle(
                    color: AppColors.textMuted, fontSize: 11)),
            const SizedBox(width: 6),
            Container(
              width: 8,
              height: 8,
              decoration: BoxDecoration(
                color: dotColor,
                shape: BoxShape.circle,
              ),
            ),
            const SizedBox(width: 6),
            Text(
              label,
              style: TextStyle(
                color: textColor,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ),
      );
    });
  }
}
