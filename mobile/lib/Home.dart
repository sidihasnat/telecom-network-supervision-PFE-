import 'package:flutter/material.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'app_theme.dart';
import 'controllers/home_controller.dart';
import 'DeviceDetailPage.dart';

// ──────────────────────────────────────────────────────────────
//  Home page
//  مرجعها: React pages/Home.jsx
//
//  بنية React:
//   - Tab buttons في الأعلى: 🏠 Overview | 📋 SLA
//   - Tab 0 (Overview):
//       1. Grid 4-cols من SummaryCards (Active Attacks / Warnings / Normal / AI Status)
//       2. ❌ Topology Map (مستثنى من الموبايل)
//       3. Grid 2-cols: Problem Timeline + Live Attack Feed
//   - Tab 1 (SLA):
//       Period dropdown + table (6 columns)
//
//  في الموبايل: Stack vertical بدل grids (responsive)
// ──────────────────────────────────────────────────────────────
class Home extends StatelessWidget {
  const Home({super.key});

  @override
  Widget build(BuildContext context) {
    final HomeController c = Get.put(HomeController());

    return RefreshIndicator(
      onRefresh: () => c.refresh(),
      color: AppColors.accent,
      backgroundColor: AppColors.bgCard,
      child: Obx(() => ListView(
            padding: const EdgeInsets.all(12),
            children: [
              // ── Tab buttons (نفس React) ──
              _buildTabButtons(c),
              const SizedBox(height: 16),

              // ── Tab content ──
              if (c.activeTab.value == 0)
                ..._buildOverviewTab(context, c)
              else
                ..._buildSlaTab(c),

              const SizedBox(height: 80),
            ],
          )),
    );
  }

  // ──────────────────────────────────────────
  //  Tab Buttons (نفس React: 🏠 Overview | 📋 SLA)
  // ──────────────────────────────────────────
  Widget _buildTabButtons(HomeController c) {
    return Row(
      children: [
        _tabButton(c, 0, 'Aperçu'),
        const SizedBox(width: 8),
        _tabButton(c, 1, 'SLA'),
      ],
    );
  }

  Widget _tabButton(HomeController c, int idx, String label) {
    final selected = c.activeTab.value == idx;
    return GestureDetector(
      onTap: () => c.changeTab(idx),
      child: Container(
        padding:
            const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          // React: bg-accent/10 لو selected
          color: selected
              ? AppColors.accent.withOpacity(0.1)
              : Colors.transparent,
          borderRadius: BorderRadius.circular(10),
        ),
        child: Text(
          label,
          style: TextStyle(
            color:
                selected ? AppColors.accent : AppColors.textSecondary,
            fontSize: 13,
            fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
          ),
        ),
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB 0 — OVERVIEW
  // ════════════════════════════════════════
  List<Widget> _buildOverviewTab(BuildContext context, HomeController c) {
    return [
      // 1. Summary Cards (4 cards)
      _buildSummaryCards(c),
      const SizedBox(height: 16),

      // 2. Problem Timeline
      _buildProblemTimeline(context, c),
      const SizedBox(height: 16),

      // 3. Live Attack Feed
      _buildLiveFeed(context, c),
    ];
  }

  // ──────────────────────────────────────────
  //  Summary Cards (4 cards — نفس React Grid 4-cols)
  //  في الموبايل: 2x2 grid (responsive)
  // ──────────────────────────────────────────
  Widget _buildSummaryCards(HomeController c) {
    return Column(
      children: [
        Row(
          children: [
            Expanded(
              child: _summaryCard(
                label: 'Attaques Actives',
                value: c.attackDevicesSet.length.toString(),
                color: AppColors.danger,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _summaryCard(
                label: 'Avertissements',
                value: c.warningDevicesSet.length.toString(),
                color: AppColors.warning,
              ),
            ),
          ],
        ),
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: _summaryCard(
                label: 'Appareils Normaux',
                value: c.normalCount.toString(),
                color: AppColors.success,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(
              child: _summaryCard(
                label: 'Statut IA',
                value: c.aiStatus.value,
                color: AppColors.accent,
                isText: true,
              ),
            ),
          ],
        ),
      ],
    );
  }

  /// SummaryCard — مطابق لـ React SummaryCard()
  Widget _summaryCard({
    required String label,
    required String value,
    required Color color,
    bool isText = false,
  }) {
    return Container(
      // React: bg-bg-card rounded-xl p-4 border
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // React: text-xs text-gray-500
          Text(
            label,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 11,
            ),
          ),
          const SizedBox(height: 6),
          // React: text-2xl font-bold font-mono ${colorMap[color]}
          Text(
            value,
            style: TextStyle(
              color: color,
              fontSize: isText ? 18 : 22,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Problem Timeline
  //  (يستخدم منطق React: clip + period markers + tooltip via tap)
  // ──────────────────────────────────────────
  Widget _buildProblemTimeline(BuildContext context, HomeController c) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header: title + period dropdown
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Chronologie des Problèmes',
                style: TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 13,
                ),
              ),
              _buildPeriodDropdown(c),
            ],
          ),
          const SizedBox(height: 12),

          // Timeline rows
          if (c.timeline.isEmpty)
            // React: "No status events recorded yet."
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 30),
              child: Center(
                child: Text(
                  'Aucun événement de statut enregistré.',
                  style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 12,
                  ),
                ),
              ),
            )
          else
            ..._buildTimelineRows(context, c),

          const SizedBox(height: 8),

          // Time axis markers (نفس React)
          if (c.timeline.isNotEmpty) _buildTimeAxis(c),

          const SizedBox(height: 8),

          // Legend (نفس React)
          _buildLegend(),
        ],
      ),
    );
  }

  Widget _buildPeriodDropdown(HomeController c) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.bgPrimary,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: DropdownButton<String>(
        value: c.timelinePeriod.value,
        onChanged: (v) {
          if (v != null) c.changePeriod(v);
        },
        underline: const SizedBox.shrink(),
        isDense: true,
        dropdownColor: AppColors.bgCard,
        style: const TextStyle(
          color: AppColors.textPrimary,
          fontSize: 11,
        ),
        items: const [
          DropdownMenuItem(value: '1h', child: Text('Dernière heure')),
          DropdownMenuItem(value: '6h', child: Text('Dernières 6h')),
          DropdownMenuItem(value: '24h', child: Text('Dernières 24h')),
          DropdownMenuItem(value: '7d', child: Text('Dernière semaine')),
        ],
      ),
    );
  }

  // الـ rows الـ 6 (one per device)
  List<Widget> _buildTimelineRows(
      BuildContext context, HomeController c) {
    final now = DateTime.now();
    final periodMs = _periodMillis(c.timelinePeriod.value);
    final startTime = now.subtract(Duration(milliseconds: periodMs));

    // group by device
    final grouped = <String, List<Map<String, dynamic>>>{};
    for (final ev in c.timeline) {
      final dev = ev['deviceName'] as String?;
      if (dev == null) continue;
      grouped.putIfAbsent(dev, () => []).add(ev);
    }

    final attackGrouped = <String, List<Map<String, dynamic>>>{};
    for (final atk in c.attackTimeline) {
      if (atk['attackType'] == 'transit') continue;
      final dev = atk['deviceName'] as String?;
      if (dev == null) continue;
      attackGrouped.putIfAbsent(dev, () => []).add(atk);
    }

    return HomeController.devices.map((device) {
      final events = (grouped[device] ?? [])
          .where((ev) => _overlapsWindow(ev, startTime, now))
          .toList();
      final attacks = (attackGrouped[device] ?? [])
          .where((atk) => _overlapsWindow(atk, startTime, now))
          .toList();

      return Padding(
        padding: const EdgeInsets.only(bottom: 8),
        child: Row(
          children: [
            // اسم الجهاز
            SizedBox(
              width: 80,
              child: GestureDetector(
                onTap: () =>
                    Get.to(() => DeviceDetailPage(deviceName: device)),
                child: Text(
                  device,
                  style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 10,
                    fontFamily: 'monospace',
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ),
            const SizedBox(width: 8),

            // الـ bar
            Expanded(
              child: LayoutBuilder(
                builder: (ctx, cons) {
                  return Container(
                    height: 20,
                    decoration: BoxDecoration(
                      color: AppColors.bgPrimary,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: ClipRRect(
                      borderRadius: BorderRadius.circular(4),
                      child: Stack(
                        children: [
                          // status events (online/offline)
                          ..._buildStatusBars(context, device, events,
                              startTime, now, cons.maxWidth),
                          // attack overlays
                          ..._buildAttackBars(context, device, attacks,
                              startTime, now, cons.maxWidth),
                        ],
                      ),
                    ),
                  );
                },
              ),
            ),
          ],
        ),
      );
    }).toList();
  }

  bool _overlapsWindow(
      Map<String, dynamic> ev, DateTime startTime, DateTime now) {
    final startStr = ev['startedAt'] as String?;
    if (startStr == null) return false;
    DateTime s;
    try {
      s = DateTime.parse(startStr);
    } catch (_) {
      return false;
    }
    final endStr = ev['endedAt'] as String?;
    final e = endStr != null ? (DateTime.tryParse(endStr) ?? now) : now;
    return e.isAfter(startTime) && !s.isAfter(now);
  }

  /// React: clip both start AND end to the window (مهم!)
  Map<String, double>? _clipPosition(
    Map<String, dynamic> ev,
    DateTime startTime,
    DateTime now,
  ) {
    final startStr = ev['startedAt'] as String?;
    if (startStr == null) return null;
    DateTime startRaw;
    try {
      startRaw = DateTime.parse(startStr);
    } catch (_) {
      return null;
    }
    final endStr = ev['endedAt'] as String?;
    final endRaw =
        endStr != null ? (DateTime.tryParse(endStr) ?? now) : now;

    // React: clip BOTH to window
    final startClipped = startRaw.isBefore(startTime) ? startTime : startRaw;
    final endClipped = endRaw.isAfter(now) ? now : endRaw;
    if (!endClipped.isAfter(startClipped)) return null;

    final totalMs = now.difference(startTime).inMilliseconds.toDouble();
    if (totalMs <= 0) return null;

    final leftMs =
        startClipped.difference(startTime).inMilliseconds.toDouble();
    final widthMs =
        endClipped.difference(startClipped).inMilliseconds.toDouble();

    return {
      'left': (leftMs / totalMs) * 100,
      // React: Math.max(0.5, ...)
      'width': ((widthMs / totalMs) * 100).clamp(0.5, 100.0),
    };
  }

  List<Widget> _buildStatusBars(
    BuildContext context,
    String device,
    List<Map<String, dynamic>> events,
    DateTime startTime,
    DateTime now,
    double width,
  ) {
    final bars = <Widget>[];
    for (final ev in events) {
      final pos = _clipPosition(ev, startTime, now);
      if (pos == null) continue;

      // React: isOffline ? 'bg-gray-500' : 'bg-success/40'
      final isOffline = ev['status'] == 'OFFLINE';
      final color = isOffline
          ? AppColors.statusGrey
          : AppColors.statusGreen.withOpacity(0.4);

      bars.add(Positioned(
        left: width * pos['left']! / 100,
        width: width * pos['width']! / 100,
        top: 0,
        bottom: 0,
        child: GestureDetector(
          onTap: () => _showSegmentDetails(context, device, ev,
              isAttack: false),
          child: Container(color: color),
        ),
      ));
    }
    return bars;
  }

  List<Widget> _buildAttackBars(
    BuildContext context,
    String device,
    List<Map<String, dynamic>> attacks,
    DateTime startTime,
    DateTime now,
    double width,
  ) {
    final bars = <Widget>[];
    for (final atk in attacks) {
      final pos = _clipPosition(atk, startTime, now);
      if (pos == null) continue;

      // React logic بالضبط:
      //   INTERFACE_DOWN/DEVICE_OFFLINE → bg-gray-500/60
      //   TRIGGER_RULE && !CRITICAL → bg-warning/60
      //   else (AI أو CRIT TRIGGER) → bg-danger/60
      final source = atk['sessionSource'] as String?;
      final severity = atk['severity'] as String?;

      Color color;
      if (source == 'INTERFACE_DOWN' || source == 'DEVICE_OFFLINE') {
        color = AppColors.statusGrey.withOpacity(0.6);
      } else if (source == 'TRIGGER_RULE' && severity != 'CRITICAL') {
        color = AppColors.statusYellow.withOpacity(0.6);
      } else {
        color = AppColors.statusRed.withOpacity(0.6);
      }

      bars.add(Positioned(
        left: width * pos['left']! / 100,
        width: width * pos['width']! / 100,
        top: 0,
        bottom: 0,
        child: GestureDetector(
          onTap: () =>
              _showSegmentDetails(context, device, atk, isAttack: true),
          child: Container(color: color),
        ),
      ));
    }
    return bars;
  }

  // Time axis (5 markers — نفس React)
  Widget _buildTimeAxis(HomeController c) {
    final now = DateTime.now();
    final periodMs = _periodMillis(c.timelinePeriod.value);
    final startTime = now.subtract(Duration(milliseconds: periodMs));
    final showDate = periodMs > 86400000; // أكثر من 24 ساعة

    return Padding(
      padding: const EdgeInsets.only(left: 88),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: List.generate(5, (i) {
          final t = startTime.add(Duration(
              milliseconds: ((i / 4) * periodMs).round()));
          final label = showDate
              ? DateFormat('MM/dd HH:mm').format(t)
              : DateFormat('HH:mm').format(t);
          return Text(
            label,
            style: const TextStyle(
              color: AppColors.textMuted,
              fontSize: 8,
              fontFamily: 'monospace',
            ),
          );
        }),
      ),
    );
  }

  // Legend (نفس React: Online / Offline / Under Attack)
  Widget _buildLegend() {
    return Row(
      children: [
        _legendItem(AppColors.statusGreen.withOpacity(0.4), 'En ligne'),
        const SizedBox(width: 12),
        _legendItem(AppColors.statusGrey, 'Hors ligne'),
        const SizedBox(width: 12),
        _legendItem(AppColors.statusRed.withOpacity(0.6), 'Sous Attaque'),
      ],
    );
  }

  Widget _legendItem(Color color, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
        const SizedBox(width: 4),
        Text(
          text,
          style: const TextStyle(
            color: AppColors.textMuted,
            fontSize: 9,
          ),
        ),
      ],
    );
  }

  int _periodMillis(String period) {
    switch (period) {
      case '1h':
        return 3600000;
      case '6h':
        return 21600000;
      case '24h':
        return 86400000;
      case '7d':
        return 604800000;
      default:
        return 21600000;
    }
  }

  // ──────────────────────────────────────────
  //  Segment Details (bottom sheet)
  // ──────────────────────────────────────────
  void _showSegmentDetails(
    BuildContext context,
    String device,
    Map<String, dynamic> seg, {
    required bool isAttack,
  }) {
    showModalBottomSheet(
      context: context,
      backgroundColor: AppColors.bgCard,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
      ),
      builder: (ctx) => Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  isAttack
                      ? Icons.warning_amber_rounded
                      : Icons.dns_outlined,
                  color: isAttack ? AppColors.danger : AppColors.accent,
                ),
                const SizedBox(width: 10),
                Text(
                  device,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 16,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (isAttack) ...[
              _detailRow('Type', seg['attackType']?.toString() ?? '—'),
              _detailRow(
                  'Source',
                  _sourceLabel(
                      seg['sessionSource']?.toString() ?? 'AI')),
              if (seg['severity'] != null)
                _detailRow('Sévérité', seg['severity'].toString()),
            ] else
              _detailRow('Statut', seg['status']?.toString() ?? '—'),
            _detailRow('Démarré',
                _formatDateTime(seg['startedAt'] as String?)),
            _detailRow(
                'Terminé',
                seg['endedAt'] == null
                    ? 'En cours'
                    : _formatDateTime(seg['endedAt'] as String?)),
            const SizedBox(height: 16),
            SizedBox(
              width: double.infinity,
              child: ElevatedButton(
                onPressed: () {
                  Navigator.pop(ctx);
                  Get.to(() => DeviceDetailPage(deviceName: device));
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.accent,
                ),
                child: const Text('Voir les Détails de l\'Appareil'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _detailRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        children: [
          SizedBox(
            width: 80,
            child: Text(
              label,
              style: const TextStyle(
                color: AppColors.textSecondary,
                fontSize: 13,
              ),
            ),
          ),
          Expanded(
            child: Text(
              value,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 13,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ──────────────────────────────────────────
  //  Live Attack Feed (نفس React)
  // ──────────────────────────────────────────
  Widget _buildLiveFeed(BuildContext context, HomeController c) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // React: text-sm text-gray-400 mb-3
          const Text(
            'Flux d\'Attaques en Direct',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
            ),
          ),
          const SizedBox(height: 12),

          if (c.recentSessions.isEmpty)
            // React: "No recent attacks"
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(
                child: Text(
                  'Aucune attaque récente',
                  style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 13,
                  ),
                ),
              ),
            )
          else
            // React: recentSessions.slice(0, 8)
            ...c.recentSessions
                .take(8)
                .map((s) => _feedRow(context, s))
        ],
      ),
    );
  }

  Widget _feedRow(BuildContext context, Map<String, dynamic> s) {
    final status = s['status'] as String?;
    final attackType = s['attackType']?.toString() ?? '—';
    final device = s['deviceName']?.toString() ?? '—';
    final iface = s['interfaceName']?.toString() ?? '';
    final startedAt = s['startedAt'] as String?;
    final confidence = s['avgConfidence'];

    // React: dot color logic
    Color dotColor;
    bool pulse = false;
    if (status == 'ACTIVE') {
      dotColor = AppColors.danger;
      pulse = true;
    } else if (status == 'MITIGATED') {
      dotColor = AppColors.success;
    } else {
      dotColor = AppColors.textMuted;
    }

    final timeStr = startedAt != null
        ? _formatTimeShort(startedAt)
        : '';

    final confStr = (confidence is num)
        ? '${(confidence * 100).toStringAsFixed(0)}%'
        : '';

    return GestureDetector(
      onTap: () => Get.to(() => DeviceDetailPage(deviceName: device)),
      child: Container(
        padding: const EdgeInsets.symmetric(vertical: 6),
        child: Row(
          children: [
            // dot
            _pulsingDot(dotColor, pulse),
            const SizedBox(width: 8),
            // time
            Text(
              timeStr,
              style: const TextStyle(
                color: AppColors.textMuted,
                fontSize: 10,
                fontFamily: 'monospace',
              ),
            ),
            const SizedBox(width: 8),
            // attack type (red لو ACTIVE)
            Expanded(
              child: Text(
                attackType,
                style: TextStyle(
                  color: status == 'ACTIVE'
                      ? AppColors.danger
                      : AppColors.textSecondary,
                  fontSize: 11,
                  fontFamily: 'monospace',
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            const SizedBox(width: 4),
            // device::interface
            Expanded(
              child: Text(
                iface.isNotEmpty ? '$device::$iface' : device,
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 10,
                ),
                overflow: TextOverflow.ellipsis,
              ),
            ),
            // confidence
            if (confStr.isNotEmpty)
              Text(
                confStr,
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 10,
                ),
              ),
          ],
        ),
      ),
    );
  }

  Widget _pulsingDot(Color color, bool pulse) {
    if (!pulse) {
      return Container(
        width: 6,
        height: 6,
        decoration: BoxDecoration(color: color, shape: BoxShape.circle),
      );
    }
    return TweenAnimationBuilder<double>(
      duration: const Duration(milliseconds: 1000),
      tween: Tween(begin: 0.5, end: 1.0),
      builder: (_, value, __) {
        return Container(
          width: 6,
          height: 6,
          decoration: BoxDecoration(
            color: color.withOpacity(value),
            shape: BoxShape.circle,
          ),
        );
      },
      onEnd: () {},
    );
  }

  // ════════════════════════════════════════
  //  TAB 1 — SLA
  // ════════════════════════════════════════
  List<Widget> _buildSlaTab(HomeController c) {
    return [
      // Period dropdown
      _buildSlaPeriod(c),
      const SizedBox(height: 12),

      // SLA cards (مكان الـ table في الـ desktop — في الموبايل نستخدم cards)
      if (c.slaLoading.value)
        Container(
          padding: const EdgeInsets.all(40),
          decoration: BoxDecoration(
            color: AppColors.bgCard,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppColors.borderLight),
          ),
          child: const Center(
            child: CircularProgressIndicator(color: AppColors.accent),
          ),
        )
      else
        ...HomeController.devices.map((d) => _slaCard(c, d)),

      const SizedBox(height: 12),

      // Explanation
      const Padding(
        padding: EdgeInsets.symmetric(horizontal: 4),
        child: Text(
          'Disponibilité = (Total − Temps d\'arrêt Attaque − Temps d\'arrêt Hors Ligne) / Total × 100',
          style: TextStyle(color: AppColors.textMuted, fontSize: 10),
        ),
      ),
    ];
  }

  Widget _buildSlaPeriod(HomeController c) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: DropdownButton<String>(
        value: c.slaPeriod.value,
        isExpanded: true,
        underline: const SizedBox.shrink(),
        dropdownColor: AppColors.bgCard,
        style: const TextStyle(
          color: AppColors.textPrimary,
          fontSize: 13,
        ),
        items: const [
          DropdownMenuItem(value: '1h', child: Text('Dernière heure')),
          DropdownMenuItem(value: '24h', child: Text('Dernières 24h')),
          DropdownMenuItem(value: '7d', child: Text('Dernière semaine')),
          DropdownMenuItem(value: '30d', child: Text('Derniers 30 jours')),
        ],
        onChanged: (v) {
          if (v != null) c.changeSlaPeriod(v);
        },
      ),
    );
  }

  Widget _slaCard(HomeController c, String device) {
    final attackDt = (c.attackDowntime[device] as num?)?.toInt() ?? 0;
    final offlineDt = (c.offlineDowntime[device] as num?)?.toInt() ?? 0;
    final totalDt = attackDt + offlineDt;

    // total seconds based on period (نفس React)
    final totalSeconds = _slaTotalSeconds(c.slaPeriod.value);
    final availability =
        ((totalSeconds - totalDt) / totalSeconds * 100).clamp(0.0, 100.0);

    // React status logic
    String statusLabel;
    Color statusColor;
    if (availability >= 99.9) {
      statusLabel = 'SAIN';
      statusColor = AppColors.success;
    } else if (availability >= 99) {
      statusLabel = 'AVERTISSEMENT';
      statusColor = AppColors.warning;
    } else {
      statusLabel = 'CRITIQUE';
      statusColor = AppColors.danger;
    }

    return Container(
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
              Expanded(
                child: Text(
                  device,
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 14,
                    fontWeight: FontWeight.bold,
                    fontFamily: 'monospace',
                  ),
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: statusColor.withOpacity(0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  statusLabel,
                  style: TextStyle(
                    color: statusColor,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          // Availability % - large
          Text(
            '${availability.toStringAsFixed(2)}%',
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 22,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
          ),
          const SizedBox(height: 8),
          const Divider(color: AppColors.borderLight, height: 1),
          const SizedBox(height: 8),
          // Downtime breakdown
          Row(
            children: [
              Expanded(
                child: _slaItem(
                    'DT Attaque',
                    attackDt > 0 ? _formatDuration(attackDt) : '—',
                    AppColors.danger),
              ),
              Expanded(
                child: _slaItem(
                    'DT Hors Ligne',
                    offlineDt > 0 ? _formatDuration(offlineDt) : '—',
                    AppColors.textSecondary),
              ),
              Expanded(
                child: _slaItem(
                    'DT Total',
                    totalDt > 0 ? _formatDuration(totalDt) : '0s',
                    AppColors.textPrimary),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _slaItem(String label, String value, Color color) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style:
              const TextStyle(color: AppColors.textMuted, fontSize: 9),
        ),
        const SizedBox(height: 2),
        Text(
          value,
          style: TextStyle(
            color: color,
            fontSize: 11,
            fontWeight: FontWeight.w600,
            fontFamily: 'monospace',
          ),
        ),
      ],
    );
  }

  int _slaTotalSeconds(String period) {
    switch (period) {
      case '1h':
        return 3600;
      case '24h':
        return 86400;
      case '7d':
        return 604800;
      case '30d':
        return 2592000;
      default:
        return 86400;
    }
  }

  // ──────────────────────────────────────────
  //  Helpers
  // ──────────────────────────────────────────

  String _formatDateTime(String? iso) {
    if (iso == null) return '—';
    try {
      final d = DateTime.parse(iso).toLocal();
      return DateFormat('MMM d, HH:mm:ss').format(d);
    } catch (_) {
      return iso;
    }
  }

  String _formatTimeShort(String iso) {
    try {
      final d = DateTime.parse(iso).toLocal();
      return DateFormat('HH:mm:ss').format(d);
    } catch (_) {
      return '';
    }
  }

  /// نفس React formatDuration:
  ///   < 60s  → Ns
  ///   < 1h   → Mm Ss
  ///   else   → Hh Mm
  String _formatDuration(int seconds) {
    if (seconds < 60) return '${seconds}s';
    if (seconds < 3600) {
      return '${seconds ~/ 60}m ${seconds % 60}s';
    }
    return '${seconds ~/ 3600}h ${(seconds % 3600) ~/ 60}m';
  }

  String _sourceLabel(String src) {
    switch (src) {
      case 'AI':
        return 'Détection IA';
      case 'TRIGGER_RULE':
        return 'Règle Déclenchée';
      case 'INTERFACE_DOWN':
        return 'Interface Inactive';
      case 'DEVICE_OFFLINE':
        return 'Appareil Hors Ligne';
      default:
        return src;
    }
  }
}
