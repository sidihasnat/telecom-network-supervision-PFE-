import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import '../LoginPage.dart';

// ──────────────────────────────────────────────────────────────
//  NavigationController
//  نفس بنية navigation_controller من المشروع القديم:
//   - selectedIndex.obs (الـ tab الحالي)
//   - handleAuthStatus(int) لـ 401 redirect
//
//  هذا الـ controller permanent — يبقى موجود طوال الـ session
// ──────────────────────────────────────────────────────────────
class NavigationController extends GetxController {
  // الـ tab الحالي في الـ BottomNavigationBar
  // 0 = Home, 1 = Security, 2 = Profile
  var selectedIndex = 0.obs;

  // ── معالجة 401/403 — إذا الـ token expired نرجع للـ Login ──
  void handleAuthStatus(int statusCode) {
    if (statusCode == 401 || statusCode == 403) {
      final box = GetStorage();
      box.remove('token');
      box.remove('user');
      Get.offAll(() => const LoginPage());
    }
  }
}
