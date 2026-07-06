import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import 'app_theme.dart';
import 'api_service.dart';
import 'LoginPage.dart';
import 'controllers/websocket_controller.dart';

// ──────────────────────────────────────────────────────────────
//  ProfilePage
//  صفحة موبايلية فقط (مش موجودة في React — هي نسخة المستخدم
//  من Settings page admin، لكن للموبايل read-only)
//
//  المحتوى:
//   1. User info card (username + fullName + role من login response)
//   2. Connection status (WebSocket isConnected)
//   3. Server URL editor (TextField + Save + Reset)
//   4. About card
//   5. Logout button
//
//  مستوحى من بنية Login + بنية كل الـ user data المخزنة من
//  AuthController response: {token, username, fullName, role}
// ──────────────────────────────────────────────────────────────
class ProfilePage extends StatefulWidget {
  const ProfilePage({super.key});

  @override
  State<ProfilePage> createState() => _ProfilePageState();
}

class _ProfilePageState extends State<ProfilePage> {
  final box = GetStorage();
  late TextEditingController _urlCtrl;
  bool _changed = false;

  @override
  void initState() {
    super.initState();
    final saved =
        box.read<String>('server_url') ?? ApiService.defaultServerUrl;
    _urlCtrl = TextEditingController(text: saved);
    _urlCtrl.addListener(() {
      final current =
          box.read<String>('server_url') ?? ApiService.defaultServerUrl;
      setState(() {
        _changed = _urlCtrl.text != current;
      });
    });
  }

  @override
  void dispose() {
    _urlCtrl.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // المستخدم الحالي (من Login response)
    final user = box.read('user') as Map<String, dynamic>?;
    final username = user?['username']?.toString() ?? 'Unknown';
    final fullName = user?['fullName']?.toString() ?? '';
    final role = user?['role']?.toString() ?? 'USER';

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // ── 1. User Info card ──
        _buildUserCard(username, fullName, role),
        const SizedBox(height: 20),

        // ── 2. Connection status ──
        _sectionTitle('Connexion'),
        const SizedBox(height: 8),
        _buildConnectionStatus(),
        const SizedBox(height: 20),

        // ── 3. Server URL editor ──
        _sectionTitle('URL du Serveur'),
        const SizedBox(height: 12),
        TextField(
          controller: _urlCtrl,
          style: const TextStyle(
            color: AppColors.textPrimary,
            fontFamily: 'monospace',
            fontSize: 13,
          ),
          decoration: const InputDecoration(
            hintText: 'http://192.168.1.10',
            prefixIcon: Icon(Icons.link, color: AppColors.textSecondary),
          ),
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: ElevatedButton.icon(
                onPressed: _changed ? _saveUrl : null,
                icon: const Icon(Icons.save, size: 16),
                label: const Text('Enregistrer'),
                style: ElevatedButton.styleFrom(
                  backgroundColor:
                  _changed ? AppColors.accent : AppColors.bgHover,
                ),
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: _resetUrl,
                icon: const Icon(Icons.refresh, size: 16),
                label: const Text('Réinitialiser'),
                style: OutlinedButton.styleFrom(
                  foregroundColor: AppColors.textSecondary,
                  side: const BorderSide(color: AppColors.borderLight),
                ),
              ),
            ),
          ],
        ),


        const SizedBox(height:200),

        // ── 5. Logout ──
        SizedBox(
          width: double.infinity,
          child: ElevatedButton.icon(
            onPressed: _confirmLogout,
            icon: const Icon(Icons.logout, size: 18),
            label: const Text('DÉCONNEXION'),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.danger,
              padding: const EdgeInsets.symmetric(vertical: 14),
            ),
          ),
        ),

        const SizedBox(height: 120),
      ],
    );
  }

  // ──────────────────────────────────────────
  //  Sub-widgets
  // ──────────────────────────────────────────

  Widget _buildUserCard(String username, String fullName, String role) {
    final displayName = fullName.isNotEmpty ? fullName : username;

    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Row(
        children: [
          CircleAvatar(
            radius: 32,
            backgroundColor: AppColors.accent.withOpacity(0.2),
            child: const Icon(Icons.person,
                color: AppColors.accent, size: 36),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  displayName,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                if (fullName.isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    '@$username',
                    style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 12,
                      fontFamily: 'monospace',
                    ),
                  ),
                ],
                const SizedBox(height: 6),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 8, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.accent.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    role,
                    style: const TextStyle(
                      color: AppColors.accent,
                      fontSize: 10,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildConnectionStatus() {
    return Obx(() {
      bool isConn = false;
      if (Get.isRegistered<WebSocketController>()) {
        isConn = Get.find<WebSocketController>().isConnected.value;
      }
      final color = isConn ? AppColors.success : AppColors.danger;
      return Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.borderLight),
        ),
        child: Row(
          children: [
            Container(
              width: 10,
              height: 10,
              decoration:
              BoxDecoration(color: color, shape: BoxShape.circle),
            ),
            const SizedBox(width: 10),
            const Text(
              'WebSocket',
              style: TextStyle(
                  color: AppColors.textSecondary, fontSize: 13),
            ),
            const Spacer(),
            Text(
              isConn ? 'Connecté' : 'Déconnecté',
              style: TextStyle(
                color: color,
                fontSize: 13,
                fontWeight: FontWeight.bold,
              ),
            ),
          ],
        ),
      );
    });
  }

  Widget _sectionTitle(String title) {
    return Text(
      title,
      style: const TextStyle(
        color: AppColors.textPrimary,
        fontSize: 13,
        fontWeight: FontWeight.bold,
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Actions
  // ──────────────────────────────────────────

  void _saveUrl() {
    final newUrl = _urlCtrl.text.trim();
    if (newUrl.isEmpty) return;

    box.write('server_url', newUrl);
    setState(() {
      _changed = false;
    });

    // إعادة اتصال الـ WebSocket على الـ URL الجديد
    if (Get.isRegistered<WebSocketController>()) {
      Get.find<WebSocketController>().reconnect();
    }

    Get.snackbar(
      'Enregistré',
      'URL du serveur mise à jour. Reconnexion...',
      backgroundColor: AppColors.bgCard,
      colorText: AppColors.success,
      borderColor: AppColors.success.withOpacity(0.3),
      borderWidth: 1,
      borderRadius: 12,
      margin: const EdgeInsets.all(12),
      duration: const Duration(seconds: 2),
      snackPosition: SnackPosition.TOP,
    );
  }

  void _resetUrl() {
    _urlCtrl.text = ApiService.defaultServerUrl;
    box.write('server_url', ApiService.defaultServerUrl);
    setState(() {
      _changed = false;
    });
  }

  void _confirmLogout() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.bgCard,
        title: const Text(
          'Confirmer la Déconnexion',
          style: TextStyle(color: AppColors.textPrimary),
        ),
        content: const Text(
          'Êtes-vous sûr de vouloir vous déconnecter ?',
          style: TextStyle(color: AppColors.textSecondary),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Annuler',
                style: TextStyle(color: AppColors.textSecondary)),
          ),
          TextButton(
            onPressed: () {
              Navigator.pop(ctx);
              _doLogout();
            },
            child: const Text('Déconnecter',
                style: TextStyle(color: AppColors.danger)),
          ),
        ],
      ),
    );
  }

  void _doLogout() {
    if (Get.isRegistered<WebSocketController>()) {
      Get.find<WebSocketController>().disconnect();
    }
    box.remove('token');
    box.remove('user');
    Get.offAll(() => const LoginPage());
  }
}