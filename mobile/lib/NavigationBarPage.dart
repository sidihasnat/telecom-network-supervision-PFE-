import 'package:flutter/material.dart';
import 'package:flutter_zoom_drawer/flutter_zoom_drawer.dart';
import 'package:get/get.dart';

import 'app_theme.dart';
import 'controllers/navigation_controller.dart';
import 'controllers/ai_status_controller.dart';
import 'widgets/toast_alerts.dart';
import 'widgets/notification_bell.dart';
import 'Home.dart';
import 'MonitoringPage.dart';
import 'SecurityPage.dart';
import 'ProfilePage.dart';

// ──────────────────────────────────────────────────────────────
//  NavigationBarPage
//
//  ── في هذا الـ batch ──
//  WebSocketController و AiStatusController الآن يُسجَّلان في
//  HomeNavigationPage (قبل ZoomDrawer)، لذا حُذفت Get.put منا.
// ──────────────────────────────────────────────────────────────
class NavigationBarPage extends StatelessWidget {
  NavigationBarPage({super.key});

  final NavigationController navController = Get.put(NavigationController());

  final List<String> appTitles = const [
    'Accueil',
    'Surveillance',
    'Sécurité',
    'Profil',
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      appBar: AppBar(
        backgroundColor: AppColors.bgCard,
        title: Obx(() => Text(
              appTitles[navController.selectedIndex.value],
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontWeight: FontWeight.bold,
                fontSize: 20,
              ),
            )),
        centerTitle: true,
        leading: GestureDetector(
          onTap: () => ZoomDrawer.of(context)!.toggle(),
          child: const Icon(Icons.menu, color: AppColors.textPrimary),
        ),
        actions: const [
          SizedBox(width: 30),
          NotificationBell(),
          SizedBox(width: 4),
        ],
      ),
      body: Column(
        children: [
          const ToastAlertsWidget(),
          Expanded(
            child: Obx(() => getPage(navController.selectedIndex.value)),
          ),
        ],
      ),
      bottomNavigationBar: Container(
        margin: const EdgeInsets.all(12),
        height: 70,
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          border: Border.all(color: AppColors.borderLight, width: 1),
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.3),
              spreadRadius: 2,
              blurRadius: 8,
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(16),
          child: Theme(
            data: Theme.of(context).copyWith(
              navigationBarTheme: NavigationBarThemeData(
                labelTextStyle: WidgetStateProperty.resolveWith<TextStyle>(
                  (states) => const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 10,
                  ),
                ),
              ),
            ),
            child: Obx(() => NavigationBar(
                  overlayColor:
                      WidgetStateProperty.all(Colors.transparent),
                  backgroundColor: Colors.transparent,
                  indicatorColor: Colors.transparent,
                  selectedIndex: navController.selectedIndex.value,
                  onDestinationSelected: (int index) {
                    navController.selectedIndex.value = index;
                  },
                  destinations: [
                    buildNavItem(Icons.home_outlined, 'Accueil', 0),
                    buildNavItem(Icons.bar_chart_outlined, 'Surveiller', 1),
                    buildNavItem(Icons.shield_outlined, 'Sécurité', 2),
                    buildNavItem(Icons.person_outline, 'Profil', 3),
                  ],
                )),
          ),
        ),
      ),
    );
  }

  NavigationDestination buildNavItem(IconData icon, String label, int idx) {
    return NavigationDestination(
      icon: Obx(() {
        final isSelected = navController.selectedIndex.value == idx;
        return Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              curve: Curves.easeInOut,
              width: isSelected ? 28 : 0,
              height: 3,
              decoration: BoxDecoration(
                color: isSelected ? AppColors.accent : Colors.transparent,
                borderRadius: BorderRadius.circular(2),
              ),
            ),
            const SizedBox(height: 4),
            Icon(
              icon,
              color: isSelected ? AppColors.accent : AppColors.textSecondary,
              size: 22,
            ),
          ],
        );
      }),
      label: label,
    );
  }

  Widget getPage(int index) {
    switch (index) {
      case 0:
        return const Home();
      case 1:
        return const MonitoringPage();
      case 2:
        return const SecurityPage();
      case 3:
        return const ProfilePage();
      default:
        return const Home();
    }
  }
}

