import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'app_theme.dart';
import 'controllers/security_controller.dart';
import 'DeviceDetailPage.dart';

// ──────────────────────────────────────────────────────────────
//  SecurityPage
//  مرجعها: React pages/Security.jsx
//
//  3 Tabs:
//   🔴 Live — predictions+sessions merge → table per device
//   📜 History — historical sessions split (Attacks AI / Alerts non-AI)
//   📋 Activity Log — audit log entries
//
//  مهم: في الموبايل بدون Mitigate button (read-only)
// ──────────────────────────────────────────────────────────────
class SecurityPage extends StatelessWidget {
  const SecurityPage({super.key});

  @override
  Widget build(BuildContext context) {
    final SecurityController c = Get.put(SecurityController());

    return RefreshIndicator(
      onRefresh: () => c.refresh(),
      color: AppColors.accent,
      backgroundColor: AppColors.bgCard,
      child: Obx(() => ListView(
            padding: const EdgeInsets.all(12),
            children: [
              // ── Tab buttons ──
              _buildTabButtons(c),
              const SizedBox(height: 16),

              // ── Tab content ──
              if (c.activeTab.value == 0)
                ..._buildLiveTab(context, c)
              else
                ..._buildHistoryTab(context, c),


              const SizedBox(height: 80),
            ],
          )),
    );
  }

  // ──────────────────────────────────────────
  //  Tab buttons
  // ──────────────────────────────────────────
  Widget _buildTabButtons(SecurityController c) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(
        children: [
          _tabBtn(c, 0, 'Direct'),
          const SizedBox(width: 8),
          _tabBtn(c, 1, 'Historique'),
        ],
      ),
    );
  }

  Widget _tabBtn(SecurityController c, int idx, String label) {
    final selected = c.activeTab.value == idx;
    return GestureDetector(
      onTap: () => c.changeTab(idx),
      child: Container(
        padding:
            const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.accent.withOpacity(0.1)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          label,
          style: TextStyle(
            color: selected
                ? AppColors.accent
                : AppColors.textSecondary,
            fontSize: 13,
            fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB 0 — LIVE
  // ════════════════════════════════════════
  List<Widget> _buildLiveTab(BuildContext context, SecurityController c) {
    final rows = c.liveRows;

    if (rows.isEmpty) {
      return [
        Container(
          padding: const EdgeInsets.symmetric(
              vertical: 60, horizontal: 24),
          decoration: BoxDecoration(
            color: AppColors.bgCard,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.borderLight),
          ),
          child: const Column(
            children: [
              Icon(Icons.shield_outlined,
                  color: AppColors.textMuted, size: 50),
              SizedBox(height: 12),
              Text(
                'En attente des prédictions...',
                style: TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 14,
                ),
              ),
              SizedBox(height: 6),
              Text(
                'Assurez-vous que le moteur IA fonctionne et que la prédiction en direct est démarrée.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 11,
                ),
              ),
            ],
          ),
        ),
      ];
    }

    return rows.map((row) => _liveRowCard(context, row)).toList();
  }

  Widget _liveRowCard(BuildContext context, LiveRow row) {
    // Status colors (نفس React)
    final color = _statusColor(row.status);
    final bgTint = color.withOpacity(0.05);

    // Display attack name (نفس React logic)
    String displayAttack = row.predictedAttack;
    if (row.isTrigger && row.sessionSource == 'TRIGGER_RULE') {
      // Match: trigger:cpuUsage>80
      final m = RegExp(r'^trigger:(.+?)([><]=?)(.+)$')
          .firstMatch(displayAttack);
      if (m != null) {
        displayAttack = 'Règle : ${m.group(1)} ${m.group(2)} ${m.group(3)}';
      }
    } else if (row.isTrigger && row.sessionSource == 'INTERFACE_DOWN') {
      displayAttack = 'Interface Inactive';
    } else if (row.isTrigger && row.sessionSource == 'DEVICE_OFFLINE') {
      displayAttack = 'Appareil Hors Ligne';
    }

    // Confidence (نفس React)
    final isAiAttack = !row.isTrigger && row.status == 'attack';
    String confDisplay = '—';
    if (isAiAttack && row.confidence != null) {
      confDisplay =
          '${(row.confidence! * 100).toStringAsFixed(0)}%';
    }

    return GestureDetector(
      onTap: () => Get.to(() => DeviceDetailPage(deviceName: row.device)),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: row.status == 'normal' ? AppColors.bgCard : bgTint,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: row.status == 'normal'
                ? AppColors.borderLight
                : color.withOpacity(0.3),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header: status dot + STATUS + device
            Row(
              children: [
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 8),
                Text(
                  row.status.toUpperCase(),
                  style: TextStyle(
                    color: color,
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    row.device,
                    style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 14,
                      fontWeight: FontWeight.w600,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
                Text(
                  row.iface,
                  style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 10,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            // Attack + Confidence
            Row(
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Attaque',
                        style: TextStyle(
                          color: AppColors.textMuted,
                          fontSize: 10,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        displayAttack,
                        style: TextStyle(
                          color: color,
                          fontSize: 12,
                          fontFamily: 'monospace',
                        ),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 12),
                Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    const Text(
                      'Confiance',
                      style: TextStyle(
                        color: AppColors.textMuted,
                        fontSize: 10,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      confDisplay,
                      style: const TextStyle(
                        color: AppColors.textPrimary,
                        fontSize: 13,
                        fontWeight: FontWeight.bold,
                        fontFamily: 'monospace',
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'attack':
        return AppColors.danger;
      case 'warning':
      case 'transit':
        return AppColors.warning;
      case 'offline':
        return AppColors.textSecondary;
      case 'normal':
      default:
        return AppColors.success;
    }
  }

  // ════════════════════════════════════════
  //  TAB 1 — HISTORY
  // ════════════════════════════════════════
  List<Widget> _buildHistoryTab(
      BuildContext context, SecurityController c) {
    return [
      // Stats cards
      _buildHistoryStats(c),
      const SizedBox(height: 12),

      // Sub-tabs (Attacks / Alerts)
      _buildHistorySubTabs(c),
      const SizedBox(height: 12),

      // Filters
      _buildHistoryFilters(c),
      const SizedBox(height: 12),

      // List
      ..._buildHistoryList(c),
    ];
  }

  Widget _buildHistoryStats(SecurityController c) {
    final stats = c.sessionStats;
    return Row(
      children: [
        Expanded(
            child: _statCard(
                'Total Attaques',
                ((stats['totalEnded'] as num?)?.toInt() ?? 0) +
                    ((stats['totalMitigated'] as num?)?.toInt() ?? 0))),
        const SizedBox(width: 6),
        Expanded(
            child: _statCard(
                'Plus Ciblé',
                stats['mostTargeted']?.toString() ?? '—',
                isText: true)),
        const SizedBox(width: 6),
        Expanded(
            child: _statCard(
                'Plus Commun',
                stats['mostCommon']?.toString() ?? '—',
                isText: true)),
        const SizedBox(width: 6),
        Expanded(
            child: _statCard(
                'Actif Maintenant',
                (stats['totalActive'] as num?)?.toInt() ?? 0,
                isCritical: true)),
      ],
    );
  }

  Widget _statCard(String label, dynamic value,
      {bool isText = false, bool isCritical = false}) {
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: AppColors.textMuted,
              fontSize: 9,
            ),
            overflow: TextOverflow.ellipsis,
          ),
          const SizedBox(height: 4),
          Text(
            value.toString(),
            style: TextStyle(
              color: isCritical
                  ? AppColors.danger
                  : AppColors.textPrimary,
              fontSize: isText ? 11 : 18,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  Widget _buildHistorySubTabs(SecurityController c) {
    return Container(
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Row(
        children: [
          Expanded(
            child: _subTabBtn(c, 'attacks',
                'Attaques (${c.historyAttacks.length})'),
          ),
          Expanded(
            child: _subTabBtn(c, 'alerts',
                'Alertes (${c.historyAlerts.length})'),
          ),
        ],
      ),
    );
  }

  Widget _subTabBtn(SecurityController c, String tab, String label) {
    final selected = c.historySubTab.value == tab;
    return GestureDetector(
      onTap: () => c.changeHistorySubTab(tab),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 10),
        decoration: BoxDecoration(
          color: selected
              ? AppColors.accent.withOpacity(0.15)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(8),
        ),
        child: Center(
          child: Text(
            label,
            style: TextStyle(
              color:
                  selected ? AppColors.accent : AppColors.textSecondary,
              fontSize: 12,
              fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildHistoryFilters(SecurityController c) {
    return Row(
      children: [
        // Period dropdown
        Expanded(
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: DropdownButton<String>(
              value: c.historyPeriod.value,
              isExpanded: true,
              underline: const SizedBox.shrink(),
              dropdownColor: AppColors.bgCard,
              hint: const Text('Tout le temps',
                  style: TextStyle(
                      color: AppColors.textSecondary, fontSize: 12)),
              style: const TextStyle(
                  color: AppColors.textPrimary, fontSize: 12),
              items: const [
                DropdownMenuItem(value: '', child: Text('Tout le temps')),
                DropdownMenuItem(value: '1h', child: Text('Dernière heure')),
                DropdownMenuItem(value: '6h', child: Text('Dernières 6h')),
                DropdownMenuItem(value: '24h', child: Text('Dernières 24h')),
                DropdownMenuItem(value: '7d', child: Text('Dernière semaine')),
                DropdownMenuItem(
                    value: '30d', child: Text('Derniers 30 jours')),
              ],
              onChanged: (v) {
                if (v != null) c.changeHistoryPeriod(v);
              },
            ),
          ),
        ),
        const SizedBox(width: 8),
        // Device filter
        Expanded(
          child: Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: DropdownButton<String>(
              value: c.historyDeviceFilter.value,
              isExpanded: true,
              underline: const SizedBox.shrink(),
              dropdownColor: AppColors.bgCard,
              hint: const Text('Tous les appareils',
                  style: TextStyle(
                      color: AppColors.textSecondary, fontSize: 12)),
              style: const TextStyle(
                  color: AppColors.textPrimary, fontSize: 12),
              items: [
                const DropdownMenuItem(
                    value: '', child: Text('Tous les appareils')),
                ...SecurityController.allDevices.map((d) =>
                    DropdownMenuItem(value: d, child: Text(d))),
              ],
              onChanged: (v) {
                if (v != null) c.changeHistoryDevice(v);
              },
            ),
          ),
        ),
      ],
    );
  }

  List<Widget> _buildHistoryList(SecurityController c) {
    if (c.historyLoading.value) {
      return [
        const Padding(
          padding: EdgeInsets.symmetric(vertical: 40),
          child: Center(
            child: CircularProgressIndicator(color: AppColors.accent),
          ),
        ),
      ];
    }

    final list = c.historySubTab.value == 'attacks'
        ? c.historyAttacks
        : c.historyAlerts;

    if (list.isEmpty) {
      return [
        Container(
          padding: const EdgeInsets.symmetric(
              vertical: 40, horizontal: 24),
          decoration: BoxDecoration(
            color: AppColors.bgCard,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.borderLight),
          ),
          child: Center(
            child: Text(
              c.historySubTab.value == 'attacks'
                  ? 'Aucune attaque dans cette période'
                  : 'Aucune alerte dans cette période',
              style: const TextStyle(
                color: AppColors.textMuted,
                fontSize: 13,
              ),
            ),
          ),
        ),
      ];
    }

    return list.map((s) {
      return c.historySubTab.value == 'attacks'
          ? _attackHistoryCard(s)
          : _alertHistoryCard(s);
    }).toList();
  }

  // History attack card (AI sessions)
  Widget _attackHistoryCard(Map<String, dynamic> s) {
    final device = s['deviceName']?.toString() ?? '—';
    final iface = s['interfaceName']?.toString() ?? '—';
    final attackType = s['attackType']?.toString() ?? '—';
    final status = s['status']?.toString() ?? '—';
    final startedAt = s['startedAt'] as String?;
    final endedAt = s['endedAt'] as String?;
    final conf = s['avgConfidence'];
    final confStr = (conf is num)
        ? '${(conf * 100).toStringAsFixed(0)}%'
        : '—';

    Color statusColor;
    if (status == 'ACTIVE') {
      statusColor = AppColors.danger;
    } else if (status == 'MITIGATED') {
      statusColor = AppColors.success;
    } else {
      statusColor = AppColors.textSecondary;
    }

    return GestureDetector(
      onTap: () => Get.to(() => DeviceDetailPage(deviceName: device)),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.borderLight),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: statusColor.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text(
                    status,
                    style: TextStyle(
                      color: statusColor,
                      fontSize: 9,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '$device :: $iface',
                    style: const TextStyle(
                      color: AppColors.textPrimary,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
                Text(
                  confStr,
                  style: const TextStyle(
                    color: AppColors.accent,
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              attackType,
              style: const TextStyle(
                color: AppColors.danger,
                fontSize: 12,
                fontFamily: 'monospace',
              ),
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                const Icon(Icons.access_time,
                    size: 11, color: AppColors.textMuted),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    '${_fmtDt(startedAt)} → ${_fmtDt(endedAt) ?? "en cours"}',
                    style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // Alert history card (TRIGGER + INTERFACE + OFFLINE)
  Widget _alertHistoryCard(Map<String, dynamic> s) {
    final src = s['sessionSource'] as String?;
    final severity = s['severity'] as String?;
    final device = s['deviceName']?.toString() ?? '—';
    final iface = s['interfaceName']?.toString() ?? '—';
    final attackType = s['attackType']?.toString() ?? '';
    final status = s['status']?.toString() ?? '—';
    final startedAt = s['startedAt'] as String?;
    final endedAt = s['endedAt'] as String?;

    // Build display label (نفس React alertLabel)
    String icon;
    String label;
    Color labelColor;
    if (src == 'INTERFACE_DOWN') {
      icon = '🔌';
      label = 'Interface Inactive';
      labelColor = AppColors.textSecondary;
    } else if (src == 'DEVICE_OFFLINE') {
      icon = '⚫';
      label = 'Appareil Hors Ligne';
      labelColor = AppColors.textSecondary;
    } else if (src == 'TRIGGER_RULE') {
      final m = RegExp(r'^trigger:(.+?)([><]=?)(.+)$')
          .firstMatch(attackType);
      final txt = m != null
          ? '${m.group(1)} ${m.group(2)} ${m.group(3)}'
          : attackType;
      final isCrit = severity == 'CRITICAL';
      icon = isCrit ? '🚨' : '⚠️';
      label = 'Règle : $txt';
      labelColor = isCrit ? AppColors.danger : AppColors.warning;
    } else {
      icon = '•';
      label = attackType;
      labelColor = AppColors.textSecondary;
    }

    return GestureDetector(
      onTap: () => Get.to(() => DeviceDetailPage(deviceName: device)),
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppColors.bgCard,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppColors.borderLight),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Text(icon, style: const TextStyle(fontSize: 16)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    label,
                    style: TextStyle(
                      color: labelColor,
                      fontSize: 13,
                      fontWeight: FontWeight.w600,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: AppColors.bgPrimary,
                    borderRadius: BorderRadius.circular(3),
                  ),
                  child: Text(
                    status,
                    style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 9,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              children: [
                const Icon(Icons.dns_outlined,
                    size: 12, color: AppColors.textMuted),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    iface != '—' ? '$device :: $iface' : device,
                    style: const TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 11,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Row(
              children: [
                const Icon(Icons.access_time,
                    size: 11, color: AppColors.textMuted),
                const SizedBox(width: 4),
                Expanded(
                  child: Text(
                    '${_fmtDt(startedAt)} → ${_fmtDt(endedAt) ?? "en cours"}',
                    style: const TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10,
                      fontFamily: 'monospace',
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────
  String? _fmtDt(String? iso) {
    if (iso == null) return null;
    try {
      final d = DateTime.parse(iso).toLocal();
      return DateFormat('MM-dd HH:mm:ss').format(d);
    } catch (_) {
      return iso;
    }
  }
}
