import 'package:flutter/material.dart';
import 'package:flutter_zoom_drawer/flutter_zoom_drawer.dart';
import 'package:get/get.dart';

import 'NavigationBarPage.dart';
import 'MenuPage.dart';
import 'controllers/websocket_controller.dart';
import 'controllers/ai_status_controller.dart';

// ──────────────────────────────────────────────────────────────
//  HomeNavigationPage
//
//  ── الإصلاح في هذا الـ batch ──
//  ZoomDrawer يبني menuScreen و mainScreen في نفس اللحظة. لو
//  WebSocketController مسجَّل داخل NavigationBarPage فقط، فـ
//  MenuPage قد يبني قبله ويرجع SizedBox.shrink (لأن Get.find يفشل).
//  والـ Obx بدون Rx subscriptions لن يعيد البناء أبداً → الأجهزة
//  لا تظهر في الـ Drawer.
//
//  الحل: تسجيل permanent للـ controllers هنا، قبل بناء الـ
//  ZoomDrawer. الآن MenuPage و NavigationBarPage يجدان كل شيء
//  جاهزاً.
// ──────────────────────────────────────────────────────────────
class HomeNavigationPage extends StatelessWidget {
  const HomeNavigationPage({super.key});

  @override
  Widget build(BuildContext context) {
    // ⚠️ تسجيل قبل ZoomDrawer (إصلاح اختفاء الأجهزة في الـ Drawer)
    if (!Get.isRegistered<WebSocketController>()) {
      Get.put(WebSocketController(), permanent: true);
    }
    if (!Get.isRegistered<AiStatusController>()) {
      Get.put(AiStatusController(), permanent: true);
    }

    return ZoomDrawer(
      angle: 0,
      mainScreenScale: 0.1,
      borderRadius: 40,
      menuScreen: const MenuPage(),
      mainScreen: NavigationBarPage(),
    );
  }
}
