import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import '../api_service.dart';
import '../app_theme.dart';
import '../controllers/notification_controller.dart';

// ──────────────────────────────────────────────────────────────
//  NotificationBell
//  مرجعها: React Header.jsx — الـ Bell button في الأعلى
//
//  badge = notifications.length + unackedSessions.length
//
//  عند الضغط: يفتح bottom sheet فيه:
//    - قائمة Unacknowledged sessions (مع ACK button لكل واحد)
//    - قائمة WebSocket notifications history
//    - زر Clear All في الأعلى
//
//  هذا widget بسيط لأنه يقرأ من NotificationController مباشرة.
// ──────────────────────────────────────────────────────────────
class NotificationBell extends StatelessWidget {
  const NotificationBell({super.key});

  @override
  Widget build(BuildContext context) {
    if (!Get.isRegistered<NotificationController>()) {
      return const SizedBox.shrink();
    }
    final c = Get.find<NotificationController>();

    return Obx(() {
      final total = c.bellBadgeCount;

      return Stack(
        clipBehavior: Clip.none,
        children: [
          IconButton(
            icon: const Icon(Icons.notifications_outlined,
                color: AppColors.textPrimary, size: 22),
            onPressed: () => _openBottomSheet(context, c),
          ),
          if (total > 0)
            Positioned(
              top: 6,
              right: 6,
              child: Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 5, vertical: 1),
                decoration: BoxDecoration(
                  color: AppColors.danger,
                  borderRadius: BorderRadius.circular(10),
                ),
                constraints: const BoxConstraints(
                  minWidth: 16,
                  minHeight: 16,
                ),
                child: Text(
                  total > 9 ? '9+' : '$total',
                  textAlign: TextAlign.center,
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ),
        ],
      );
    });
  }

  void _openBottomSheet(BuildContext context, NotificationController c) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.bgCard,
      isScrollControlled: true,
      shape: const RoundedRectangleBorder(
        borderRadius:
            BorderRadius.vertical(top: Radius.circular(20)),
      ),
      builder: (ctx) => DraggableScrollableSheet(
        initialChildSize: 0.7,
        minChildSize: 0.4,
        maxChildSize: 0.9,
        expand: false,
        builder: (_, scrollCtrl) =>
            _BellBottomSheet(controller: c, scrollCtrl: scrollCtrl),
      ),
    );
  }
}

class _BellBottomSheet extends StatelessWidget {
  final NotificationController controller;
  final ScrollController scrollCtrl;

  const _BellBottomSheet({
    required this.controller,
    required this.scrollCtrl,
  });

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      final unacked = controller.unackedSessions;
      final notifs = controller.notifications;
      final total = unacked.length + notifs.length;

      return Column(
        children: [
          // Drag handle
          Container(
            margin: const EdgeInsets.only(top: 8, bottom: 4),
            width: 40,
            height: 4,
            decoration: BoxDecoration(
              color: AppColors.borderLight,
              borderRadius: BorderRadius.circular(2),
            ),
          ),
          // Header
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 12, 12),
            child: Row(
              children: [
                const Text(
                  'Notifications',
                  style: TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(width: 8),
                if (total > 0)
                  Text(
                    '$total non lus',
                    style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 11,
                    ),
                  ),
                const Spacer(),
                if (total > 0)
                  TextButton(
                    onPressed: () async {
                      // ACK all unacked sessions
                      final sessions =
                          List<Map<String, dynamic>>.from(unacked);
                      for (final s in sessions) {
                        final id = s['id'];
                        if (id is int) {
                          try {
                            await ApiService.acknowledgeSession(id);
                          } catch (_) {}
                        }
                      }
                      controller.unackedSessions.clear();
                      controller.clearAllNotifications();
                    },
                    style: TextButton.styleFrom(
                      foregroundColor: AppColors.textSecondary,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      backgroundColor:
                          AppColors.borderLight.withOpacity(0.5),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(6),
                      ),
                    ),
                    child: const Text(
                      'Tout Effacer',
                      style: TextStyle(fontSize: 11),
                    ),
                  ),
              ],
            ),
          ),
          const Divider(color: AppColors.borderLight, height: 1),
          // List
          Expanded(
            child: total == 0
                ? const Center(
                    child: Text(
                      'Aucune notification',
                      style: TextStyle(
                          color: AppColors.textMuted, fontSize: 13),
                    ),
                  )
                : ListView(
                    controller: scrollCtrl,
                    children: [
                      // Unacked sessions
                      ...unacked.map(
                          (session) => _UnackedSessionTile(session: session)),
                      // WebSocket notifications
                      ...notifs
                          .asMap()
                          .entries
                          .map((e) => _NotifTile(notif: e.value)),
                    ],
                  ),
          ),
        ],
      );
    });
  }
}

class _UnackedSessionTile extends StatelessWidget {
  final Map<String, dynamic> session;
  const _UnackedSessionTile({required this.session});

  @override
  Widget build(BuildContext context) {
    final c = Get.find<NotificationController>();
    final attackType = session['attackType']?.toString() ?? 'unknown';
    final device = session['deviceName']?.toString() ?? '';
    final iface = session['interfaceName']?.toString() ?? '-';
    final escalation = session['escalationLevel']?.toString() ?? '1';
    final conf = session['avgConfidence'];
    final duration = session['durationFormatted']?.toString();

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: const BoxDecoration(
        border: Border(
          bottom:
              BorderSide(color: AppColors.borderLight, width: 0.5),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: const BoxDecoration(
                  color: AppColors.danger,
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                attackType,
                style: const TextStyle(
                  color: AppColors.danger,
                  fontFamily: 'monospace',
                  fontSize: 12,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                'Niv $escalation',
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 10,
                ),
              ),
              const Spacer(),
              InkWell(
                onTap: () async {
                  final id = session['id'];
                  if (id is int) {
                    try {
                      await ApiService.acknowledgeSession(id);
                      c.unackedSessions.removeWhere(
                          (s) => s['id'] == id);
                    } catch (_) {}
                  }
                },
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: AppColors.accent.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Text(
                    'ACK',
                    style: TextStyle(
                      color: AppColors.accent,
                      fontSize: 11,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          Text(
            '$device::$iface',
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 13,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            [
              if (conf is num) '${(conf * 100).toStringAsFixed(0)}% confiance',
              if (duration != null && duration.isNotEmpty) duration,
            ].join(' · '),
            style: const TextStyle(
              color: AppColors.textMuted,
              fontSize: 10,
            ),
          ),
        ],
      ),
    );
  }
}

class _NotifTile extends StatelessWidget {
  final Map<String, dynamic> notif;
  const _NotifTile({required this.notif});

  @override
  Widget build(BuildContext context) {
    final type = notif['type']?.toString() ?? '';
    final icon = type == 'NEW_ATTACK'
        ? '🚨'
        : type == 'ATTACK_ENDED'
            ? '✅'
            : type == 'ESCALATION'
                ? '⚠️'
                : '🔔';

    final ts = notif['timestamp']?.toString();
    final timeStr = (ts != null && ts.isNotEmpty)
        ? _formatTime(ts)
        : 'now';

    final device = notif['deviceName']?.toString() ?? '';
    final iface = notif['interfaceName']?.toString() ?? '';
    final attack = notif['attackType']?.toString() ?? '';
    final duration = notif['duration']?.toString() ?? '';
    final oldLevel = notif['oldLevel']?.toString();
    final newLevel = notif['newLevel']?.toString();

    String body;
    if (type == 'NEW_ATTACK') {
      body = '$attack sur $device::$iface';
    } else if (type == 'ATTACK_ENDED') {
      body = '$attack terminé sur $device${duration.isNotEmpty ? ' ($duration)' : ''}';
    } else if (type == 'ESCALATION') {
      body =
          'Escalade Niv $oldLevel→$newLevel: $attack sur $device';
    } else {
      body = type;
    }

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: const BoxDecoration(
        border: Border(
          bottom:
              BorderSide(color: AppColors.borderLight, width: 0.5),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Text(icon, style: const TextStyle(fontSize: 13)),
              const SizedBox(width: 8),
              Text(
                timeStr,
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 10,
                ),
              ),
            ],
          ),
          const SizedBox(height: 4),
          Text(
            body,
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 12,
            ),
          ),
        ],
      ),
    );
  }

  String _formatTime(String iso) {
    try {
      return DateFormat('HH:mm:ss')
          .format(DateTime.parse(iso).toLocal());
    } catch (_) {
      return '';
    }
  }
}
