import 'dart:convert';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';
import 'package:http/http.dart' as http;

import '../api_service.dart';
import '../HomeNavigationPage.dart';

// ──────────────────────────────────────────────────────────────
//  LoginController
//  مرجعها: React pages/Login.jsx
//
//  المنطق نفسه بالضبط:
//   - State: username / password / error / loading
//   - POST /api/auth/login → {username, password}
//   - Response (200): {token, username, fullName, role}
//   - Response (401): {error: "Invalid username or password"}
//   - يحفظ في GetStorage (بديل localStorage في الموبايل):
//       'token' → JWT
//       'user' → {username, role, fullName}
//
//  إضافات الموبايل (مش في React):
//   - passwordVisible toggle (UX على الموبايل أهم)
//   - تمييز Network error vs Auth error (في React proxy فلا يحصل)
// ──────────────────────────────────────────────────────────────
class LoginController extends GetxController {
  // مطابق لـ React useState
  var username = ''.obs;
  var password = ''.obs;
  var error = ''.obs;       // ← نفس "error" في React (مش "msg")
  var isLoading = false.obs;

  // إضافة موبايل
  var passwordVisible = false.obs;

  final box = GetStorage();

  // ── handleSubmit (نفس React) ────────────────────────────────
  Future<void> login() async {
    // React: if (!username || !password) return;
    if (username.value.isEmpty || password.value.isEmpty) return;

    isLoading.value = true;
    error.value = '';

    try {
      final res = await http
          .post(
            Uri.parse('${ApiService.baseUrl}/api/auth/login'),
            headers: {'Content-Type': 'application/json'},
            body: jsonEncode({
              'username': username.value,
              'password': password.value,
            }),
          )
          .timeout(const Duration(seconds: 10));

      // نفس logic React: const data = await res.json();
      Map<String, dynamic> data;
      try {
        data = jsonDecode(res.body) as Map<String, dynamic>;
      } catch (_) {
        data = {};
      }

      // React: if (!res.ok) { setError(data.error || 'Login failed'); return; }
      if (res.statusCode < 200 || res.statusCode >= 300) {
        error.value = (data['error'] as String?) ?? 'Login failed';
        isLoading.value = false;
        return;
      }

      // ✅ نجح — نفس logic React (نحفظ token + user)
      final token = data['token'] as String?;
      if (token == null || token.isEmpty) {
        error.value = 'Server returned no token';
        isLoading.value = false;
        return;
      }

      // localStorage.setItem('token', data.token)
      box.write('token', token);

      // localStorage.setItem('user', JSON.stringify({
      //   username: data.username,
      //   role: data.role,
      //   fullName: data.fullName,
      // }))
      box.write('user', {
        'username': data['username'] ?? username.value,
        'role': data['role'] ?? 'VIEWER',
        'fullName': data['fullName'] ?? '',
      });

      // React: onLogin(data) — في الموبايل: navigate to home
      Get.offAll(() => const HomeNavigationPage());
    } catch (e) {
      // React: catch (err) { setError('Cannot connect to server'); }
      // إضافة موبايل: تمييز أكثر للمستخدم
      final msg = e.toString().toLowerCase();
      if (msg.contains('timeout') ||
          msg.contains('socket') ||
          msg.contains('connection') ||
          msg.contains('network')) {
        error.value =
            'Cannot connect to server. Check WiFi and Server URL (⚙️ above).';
      } else {
        error.value = 'Cannot connect to server';
      }
    } finally {
      isLoading.value = false;
    }
  }
}
