import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import 'app_theme.dart';
import 'SplashScreen.dart';
import 'controllers/notification_controller.dart';
import 'services/local_notifications_service.dart';

void main() async {
  // Required for plugins (LocalNotifications + GetStorage)
  WidgetsFlutterBinding.ensureInitialized();

  // GetStorage للـ token + server URL
  await GetStorage.init();

  // 🔔 Initialize system notifications (Android tray + iOS push)
  // (يطلب permission من المستخدم في أول تشغيل)
  await LocalNotificationsService.init();

  // ── Permanent: NotificationController ──
  // (يحتاج يكون موجوداً قبل WebSocket حتى يستلم notifications + يرسل push)
  Get.put(NotificationController(), permanent: true);

  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return GetMaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'TelecomAI',
      theme: AppTheme.darkTheme,
      home: const SplashScreen(),
    );
  }
}
