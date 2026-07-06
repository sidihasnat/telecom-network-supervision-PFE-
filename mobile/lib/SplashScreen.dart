import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import 'app_theme.dart';
import 'LoginPage.dart';
import 'HomeNavigationPage.dart';

// ──────────────────────────────────────────────────────────────
//  SplashScreen
//  شاشة البداية — تفحص لو في token محفوظ:
//   - لو موجود → HomeNavigationPage مباشرة
//   - لو مش موجود → LoginPage
//
//  نفس فكرة SplashScreen في المشروع القديم
// ──────────────────────────────────────────────────────────────
class SplashScreen extends StatefulWidget {
  const SplashScreen({super.key});

  @override
  State<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends State<SplashScreen> {
  @override
  void initState() {
    super.initState();
    // نأخر شوية عشان الـ logo يظهر
    Future.delayed(const Duration(seconds: 2), _checkAuth);
  }

  void _checkAuth() {
    final box = GetStorage();
    final token = box.read<String>('token');

    if (token != null && token.isNotEmpty) {
      Get.offAll(() => const HomeNavigationPage());
    } else {
      Get.offAll(() => const LoginPage());
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            // Logo دائري بلون accent
            Container(
              width: 100,
              height: 100,
              decoration: BoxDecoration(
                color: AppColors.accent.withOpacity(0.15),
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.accent, width: 2),
              ),
              child: const Icon(
                Icons.shield_outlined,
                color: AppColors.accent,
                size: 50,
              ),
            ),
            const SizedBox(height: 24),
            const Text(
              'TelecomAI',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 28,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 8),
            const Text(
              'Supervision Réseau & Détection des Menaces',
              style: TextStyle(
                color: AppColors.textSecondary,
                fontSize: 13,
              ),
            ),
            const SizedBox(height: 48),
            const CircularProgressIndicator(
              color: AppColors.accent,
              strokeWidth: 2,
            ),
          ],
        ),
      ),
    );
  }
}
