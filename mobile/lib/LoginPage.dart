import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:get_storage/get_storage.dart';

import 'app_theme.dart';
import 'api_service.dart';
import 'controllers/login_controller.dart';

// ──────────────────────────────────────────────────────────────
//  LoginPage
//  مرجعها: React pages/Login.jsx
//
//  بنية React:
//   1. Logo (Shield icon داخل دائرة accent/10) + "TelecomAI" + subtitle
//   2. Card "Sign In":
//      - Error box (لو موجود) — bg-danger/10 border-danger/20
//      - Username input
//      - Password input
//      - Sign In button (disabled لو loading أو فاضي)
//      - Hint text "Default: admin / admin123"
//
//  إضافات للموبايل:
//   - زر ⚙️ في الزاوية لتعديل Server URL (لأن الموبايل ما عنده proxy)
//   - عرض Server URL الحالي في الأسفل (clickable)
//   - Password visibility toggle
//
//  التصميم matches React بالضبط (نفس الألوان والـ spacing).
// ──────────────────────────────────────────────────────────────
class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    final LoginController c = Get.put(LoginController());

    return Scaffold(
      backgroundColor: AppColors.bgPrimary,
      body: SafeArea(
        child: Stack(
          children: [
            // ── الـ ⚙️ في الزاوية (إضافة موبايل) ──
            Positioned(
              top: 12,
              right: 12,
              child: IconButton(
                icon: const Icon(
                  Icons.settings_outlined,
                  color: AppColors.textSecondary,
                  size: 24,
                ),
                onPressed: () => _showServerUrlDialog(context),
                tooltip: 'Server URL',
              ),
            ),

            // ── Centered content (نفس React: flex items-center justify-center) ──
            Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.symmetric(horizontal: 24),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 380),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      // ── Logo Block ──
                      // React: text-center mb-8
                      _buildLogoBlock(),
                      const SizedBox(height: 32),

                      // ── Sign In Card ──
                      // React: bg-bg-card rounded-xl border border-gray-800 p-6
                      _buildSignInCard(c),

                      const SizedBox(height: 16),

                      // ── Server URL display (إضافة موبايل) ──
                      _buildServerUrlHint(context),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Logo block (نفس React بالضبط)
  // ──────────────────────────────────────────
  Widget _buildLogoBlock() {
    return Column(
      children: [
        // React: w-16 h-16 bg-accent/10 rounded-2xl
        Container(
          width: 64,
          height: 64,
          decoration: BoxDecoration(
            color: AppColors.accent.withOpacity(0.1),
            borderRadius: BorderRadius.circular(16),
          ),
          child: const Icon(
            Icons.shield_outlined,
            color: AppColors.accent,
            size: 42,
          ),
        ),
        const SizedBox(height: 20),
        // React: text-2xl font-bold text-gray-100

        // React: text-sm text-gray-500
      ],
    );
  }

  // ──────────────────────────────────────────
  //  Sign In Card (نفس React بالضبط)
  // ──────────────────────────────────────────
  Widget _buildSignInCard(LoginController c) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // React: text-lg font-semibold text-gray-200 mb-6
          const Text(
            'Connexion',
            style: TextStyle(
              color: AppColors.textPrimary,
              fontSize: 18,
              fontWeight: FontWeight.w600,
            ),
          ),
          const SizedBox(height: 20),

          // ── Error box (React: bg-danger/10 border-danger/20) ──
          Obx(() {
            if (c.error.value.isEmpty) return const SizedBox.shrink();
            return Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: AppColors.danger.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(
                      color: AppColors.danger.withOpacity(0.2)),
                ),
                child: Text(
                  c.error.value,
                  style: const TextStyle(
                    color: AppColors.danger,
                    fontSize: 13,
                  ),
                ),
              ),
            );
          }),

          // ── Username input ──
          // React: text-xs text-gray-500 mb-1.5
          const Text(
            'Nom d\'utilisateur',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 6),
          TextField(
            autofocus: true,
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 14,
            ),
            onChanged: (v) => c.username.value = v,
            // React: onKeyDown={(e) => e.key === 'Enter' && handleSubmit(e)}
            onSubmitted: (_) => c.login(),
            textInputAction: TextInputAction.next,
            decoration: const InputDecoration(
              hintText: 'admin',
            ),
          ),

          const SizedBox(height: 16),

          // ── Password input ──
          const Text(
            'Mot de passe',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 12,
            ),
          ),
          const SizedBox(height: 6),
          Obx(() => TextField(
                obscureText: !c.passwordVisible.value,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 14,
                ),
                onChanged: (v) => c.password.value = v,
                onSubmitted: (_) => c.login(),
                textInputAction: TextInputAction.done,
                decoration: InputDecoration(
                  hintText: '••••••••',
                  suffixIcon: IconButton(
                    icon: Icon(
                      c.passwordVisible.value
                          ? Icons.visibility_off
                          : Icons.visibility,
                      color: AppColors.textMuted,
                      size: 20,
                    ),
                    onPressed: () =>
                        c.passwordVisible.value = !c.passwordVisible.value,
                  ),
                ),
              )),

          const SizedBox(height: 20),

          // ── Sign In button ──
          // React: disabled={loading || !username || !password}
          //        bg-accent text-white rounded-lg
          Obx(() {
            final disabled = c.isLoading.value ||
                c.username.value.isEmpty ||
                c.password.value.isEmpty;

            return SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: disabled ? null : c.login,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                  disabledBackgroundColor:
                      AppColors.accent.withOpacity(0.4),
                  foregroundColor: Colors.white,
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  elevation: 0,
                ),
                child: c.isLoading.value
                    ? const SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          color: Colors.white,
                          strokeWidth: 2,
                        ),
                      )
                    // React: {loading ? 'Signing in...' : 'Sign In'}
                    : const Text(
                        'Se connecter',
                        style: TextStyle(
                          fontSize: 14,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
              ),
            );
          }),

          const SizedBox(height: 24),

          // ── Hint (نفس React) ──
          // React: text-xs text-gray-600 text-center

        ],
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Server URL hint (إضافة موبايل)
  // ──────────────────────────────────────────
  Widget _buildServerUrlHint(BuildContext context) {
    final box = GetStorage();
    final url =
        box.read<String>('server_url') ?? ApiService.defaultServerUrl;

    return GestureDetector(
      onTap: () => _showServerUrlDialog(context),
      child: Container(
        padding:
            const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.dns_outlined,
                size: 12, color: AppColors.textMuted),
            const SizedBox(width: 6),
            Text(
              url,
              style: const TextStyle(
                color: AppColors.textMuted,
                fontSize: 11,
                fontFamily: 'monospace',
              ),
            ),
            const SizedBox(width: 6),
            const Icon(Icons.edit_outlined,
                size: 11, color: AppColors.textMuted),
          ],
        ),
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Server URL dialog (إضافة موبايل)
  // ──────────────────────────────────────────
  void _showServerUrlDialog(BuildContext context) {
    final box = GetStorage();
    final currentUrl =
        box.read<String>('server_url') ?? ApiService.defaultServerUrl;
    final controller = TextEditingController(text: currentUrl);

    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.bgCard,
        shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12)),
        title: const Row(
          children: [
            Icon(Icons.dns_outlined, color: AppColors.accent),
            SizedBox(width: 8),
            Text('URL du Serveur',
                style: TextStyle(
                    color: AppColors.textPrimary, fontSize: 18)),
          ],
        ),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text(
              'URL du serveur TelecomAI :',
              style:
                  TextStyle(color: AppColors.textSecondary, fontSize: 12),
            ),
            const SizedBox(height: 12),
            TextField(
              controller: controller,
              autofocus: true,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontFamily: 'monospace',
                fontSize: 13,
              ),
              decoration: const InputDecoration(
                hintText: 'http://192.168.x.x',
              ),
            ),
            const SizedBox(height: 12),
            Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: AppColors.bgPrimary,
                borderRadius: BorderRadius.circular(6),
              ),
              child: const Text(
                '💡 Si vous utilisez le port forwarding (socat) sur Ubuntu, utilisez http://<ip-pc> sans port.\n\nSi connexion directe : inclure :8080 etc.',
                style: TextStyle(color: AppColors.textMuted, fontSize: 10),
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () =>
                controller.text = ApiService.defaultServerUrl,
            child: const Text('Réinitialiser',
                style: TextStyle(color: AppColors.textSecondary)),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Annuler',
                style: TextStyle(color: AppColors.textSecondary)),
          ),
          ElevatedButton(
            onPressed: () {
              final newUrl = controller.text.trim();
              if (newUrl.isEmpty) return;
              box.write('server_url', newUrl);
              Navigator.pop(ctx);
              Get.snackbar(
                'Enregistré',
                newUrl,
                backgroundColor: AppColors.bgCard,
                colorText: AppColors.success,
                borderColor: AppColors.success.withOpacity(0.3),
                borderWidth: 1,
                margin: const EdgeInsets.all(12),
                duration: const Duration(seconds: 2),
              );
            },
            style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.accent),
            child: const Text('Enregistrer'),
          ),
        ],
      ),
    );
  }
}
