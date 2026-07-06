import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:get/get.dart';
import 'package:intl/intl.dart';

import 'app_theme.dart';
import 'controllers/device_controller.dart';
import 'widgets/toast_alerts.dart';

// ──────────────────────────────────────────────────────────────
//  DeviceDetailPage
//  مرجعها: React components/SidePanel.jsx
//
//  Tabs:
//   - Overview: cpu/mem/conn + sparklines + disk + interfaces
//   - AI Result: predictions + topFeatures + MITRE
//   - KPI: device-specific metrics + sparklines (showAll toggle)
//   - Interfaces (router only): UP/DOWN timeline per interface
//   - History: chart of one metric over time + stats
//   - Counters: raw TCP+Security counters
//   - Properties: device info + neighbors + ARP table
//
//  Mobile excludes: Logs (admin terminal feature)
// ──────────────────────────────────────────────────────────────

// KPI map per device type (نفس React)
const Map<String, List<String>> kKpiMap = {
  'web-server': [
    'halfOpenConnections',
    'passiveOpensRate',
    'icmpInRate',
    'bytesPerPacketIn',
    'avgConnectionDuration',
  ],
  'ftp-server': [
    'failedLogins',
    'failedLoginsRate',
    'uniqueSourceIPs',
    'bytesPerPacketIn',
    'topAttackerRepeat',
  ],
  'db-server': [
    'sensitivePortsHit',
    'uniqueDestinationPorts',
    'halfOpenConnections',
    'outRstsRate',
    'throughputIn',
  ],
  'dns-server': [
    'udpInRate',
    'udpOutRate',
    'udpInOutRatio',
    'udpNoPortRate',
    'throughputIn',
  ],
  'edge-router': [
    'forwardedPackets',
    'routingTableSize',
    'inDiscards',
    'noRoutePackets',
    'icmpInRate',
  ],
  'core-router': [
    'forwardedPackets',
    'routingTableSize',
    'inDiscards',
    'noRoutePackets',
    'icmpInRate',
  ],
};

// All KPIs (للـ "Show All" toggle)
const List<String> kAllKpis = [
  'halfOpenConnections', 'passiveOpensRate', 'activeOpensRate',
  'attemptFailsRate', 'outRstsRate', 'inSegsRate', 'outSegsRate',
  'retransRate', 'icmpInRate', 'inOutSegRatio', 'udpInRate', 'udpOutRate',
  'udpNoPortRate', 'udpInOutRatio', 'uniqueSourceIPs', 'topAttackerRepeat',
  'uniqueDestinationPorts', 'sensitivePortsHit', 'timeWaitConnections',
  'avgConnectionDuration', 'longConnectionRatio', 'failedLogins',
  'failedLoginsRate', 'sshConnectionAttempts', 'forwardedPackets',
  'routingTableSize', 'inDiscards', 'noRoutePackets',
  'throughputIn', 'throughputOut', 'bytesPerPacketIn', 'bytesPerPacketOut',
  'cpuUsage', 'memoryUsage',
];

class DeviceDetailPage extends StatelessWidget {
  final String deviceName;
  const DeviceDetailPage({super.key, required this.deviceName});

  bool get isRouter => deviceName.contains('router');

  @override
  Widget build(BuildContext context) {
    final c = Get.put(DeviceController(deviceName), tag: deviceName);

    // Tabs (نفس React BASE_TABS / ROUTER_TABS, بدون Logs للموبايل)
    final tabs = [
      ('Aperçu', _buildOverviewTab(c)),
      ('Résultat IA', _buildAiResultTab(c)),
      ('KPI', _buildKpiTab(c)),
      if (isRouter) ('Interfaces', _buildInterfacesTab(c)),
      ('Historique', _buildHistoryTab(c)),
      ('Compteurs', _buildCountersTab(c)),
      ('Propriétés', _buildPropertiesTab(c)),
    ];

    return DefaultTabController(
      length: tabs.length,
      child: Scaffold(
        backgroundColor: AppColors.bgPrimary,
        appBar: AppBar(
          title: Row(
            children: [
              // Offline indicator (نفس React isOffline check)
              Obx(() => Container(
                    width: 8,
                    height: 8,
                    margin: const EdgeInsets.only(right: 8),
                    decoration: BoxDecoration(
                      color: c.isOffline.value
                          ? AppColors.textMuted
                          : AppColors.success,
                      shape: BoxShape.circle,
                    ),
                  )),
              Expanded(
                child: Text(
                  deviceName,
                  style: const TextStyle(
                    fontSize: 16,
                    fontFamily: 'monospace',
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          backgroundColor: AppColors.bgCard,
          iconTheme: const IconThemeData(color: AppColors.textPrimary),
          bottom: TabBar(
            indicatorColor: AppColors.accent,
            labelColor: AppColors.accent,
            unselectedLabelColor: AppColors.textSecondary,
            isScrollable: true,
            labelStyle:
                const TextStyle(fontSize: 12, fontWeight: FontWeight.w600),
            tabs: tabs.map((t) => Tab(text: t.$1)).toList(),
          ),
        ),
        body: RefreshIndicator(
          onRefresh: () => c.refresh(),
          color: AppColors.accent,
          backgroundColor: AppColors.bgCard,
          child: Column(
            children: [
              const ToastAlertsWidget(),
              Expanded(
                child: TabBarView(
                  children: tabs.map((t) => t.$2).toList(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB — OVERVIEW
  // ════════════════════════════════════════
  Widget _buildOverviewTab(DeviceController c) {
    return Obx(() {
      final m = c.isOffline.value ? null : c.latestMetric.value;
      final cpu = (m?['cpuUsage'] as num?)?.toDouble() ?? 0;
      final mem = (m?['memoryUsage'] as num?)?.toDouble() ?? 0;
      final conn = (m?['connections'] as num?)?.toInt() ?? 0;
      final diskPct = (m?['diskUsage'] as num?)?.toDouble() ?? 0;
      final diskTotal = (m?['diskTotalGb'] as num?)?.toDouble() ?? 0;
      final diskUsed = (m?['diskUsedGb'] as num?)?.toDouble() ?? 0;
      final diskFree = (m?['diskFreeGb'] as num?)?.toDouble() ?? 0;

      // Build sparkline data
      var cpuHist = c.metricsHistory
          .map((m) =>
              FlSpot(c.metricsHistory.indexOf(m).toDouble(),
                  (m['cpuUsage'] as num?)?.toDouble() ?? 0))
          .toList();
      var memHist = c.metricsHistory
          .map((m) =>
              FlSpot(c.metricsHistory.indexOf(m).toDouble(),
                  (m['memoryUsage'] as num?)?.toDouble() ?? 0))
          .toList();

      // Add 0 point if offline
      if (c.isOffline.value && cpuHist.isNotEmpty) {
        final lastIdx = cpuHist.last.x + 1;
        cpuHist = [...cpuHist, FlSpot(lastIdx, 0)];
        memHist = [...memHist, FlSpot(lastIdx, 0)];
      }

      // Disk color (نفس React threshold)
      final diskColor = diskPct >= 90
          ? AppColors.danger
          : diskPct >= 80
              ? AppColors.warning
              : const Color(0xFF8B5CF6);

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // 3 Metric boxes (CPU / Memory / Connections)
          Row(
            children: [
              Expanded(
                child: _metricBox('CPU', '${cpu.toStringAsFixed(1)}%'),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _metricBox('Mémoire', '${mem.toStringAsFixed(1)}%'),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: _metricBox('Connexions', conn.toString()),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Sparklines (only if 2+ points)
          if (cpuHist.length > 2) ...[
            _miniChart('CPU', cpuHist, AppColors.accent,
                currentValue: cpu, unit: '%'),
            const SizedBox(height: 8),
            _miniChart('Mémoire', memHist, const Color(0xFF3B82F6),
                currentValue: mem, unit: '%'),
            const SizedBox(height: 12),
          ],

          // Disk Usage card (نفس React)
          if (diskTotal > 0) ...[
            Container(
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
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      const Text('Utilisation Disque',
                          style: TextStyle(
                              color: AppColors.textSecondary,
                              fontSize: 12)),
                      Text(
                        '${diskPct.toStringAsFixed(1)}%',
                        style: TextStyle(
                          color: diskColor,
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  // Progress bar
                  Container(
                    height: 6,
                    decoration: BoxDecoration(
                      color: AppColors.bgPrimary,
                      borderRadius: BorderRadius.circular(3),
                    ),
                    child: FractionallySizedBox(
                      alignment: Alignment.centerLeft,
                      widthFactor: (diskPct / 100).clamp(0.0, 1.0),
                      child: Container(
                        decoration: BoxDecoration(
                          color: diskColor,
                          borderRadius: BorderRadius.circular(3),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Row(
                    children: [
                      Expanded(
                          child: _diskItem(
                              'Utilisé', '${diskUsed.toStringAsFixed(1)} GB')),
                      Expanded(
                          child: _diskItem(
                              'Libre', '${diskFree.toStringAsFixed(1)} GB')),
                      Expanded(
                          child: _diskItem('Total',
                              '${diskTotal.toStringAsFixed(1)} GB')),
                    ],
                  ),
                ],
              ),
            ),
            const SizedBox(height: 12),
          ],

          // Interfaces (per-interface info)
          if (m?['interfaces'] is List)
            ...(m!['interfaces'] as List).whereType<Map>().map((iface) {
              return _interfaceOverviewCard(
                  Map<String, dynamic>.from(iface));
            }),

          const SizedBox(height: 80),
        ],
      );
    });
  }

  Widget _metricBox(String label, String value) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label,
              style: const TextStyle(
                  color: AppColors.textMuted, fontSize: 11)),
          const SizedBox(height: 4),
          Text(
            value,
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 16,
              fontWeight: FontWeight.bold,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }

  Widget _diskItem(String label, String value) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: const TextStyle(
                color: AppColors.textMuted, fontSize: 9)),
        const SizedBox(height: 2),
        Text(
          value,
          style: const TextStyle(
            color: AppColors.textPrimary,
            fontSize: 11,
            fontFamily: 'monospace',
          ),
        ),
      ],
    );
  }

  // ──────────────────────────────────────────
  //  Mini sparkline chart (Overview tab)
  //  ✅ يعرض القيمة الحالية في الأعلى
  //  ✅ tooltip عند الضغط (نفس نمط _historyChart)
  // ──────────────────────────────────────────
  Widget _miniChart(
    String label,
    List<FlSpot> spots,
    Color color, {
    double? currentValue,
    String unit = '',
  }) {
    if (spots.isEmpty) return const SizedBox.shrink();
    final maxY = spots.map((s) => s.y).reduce((a, b) => a > b ? a : b);
    final displayValue = currentValue ??
        (spots.isEmpty ? 0.0 : spots.last.y);

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
          // ─── Label + current value (السطر فوق الـ chart) ───
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                label,
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 10),
              ),
              Text(
                '${displayValue.toStringAsFixed(displayValue < 10 ? 2 : 1)}$unit',
                style: TextStyle(
                  color: color,
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  fontFamily: 'monospace',
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          SizedBox(
            height: 60,
            child: LineChart(
              LineChartData(
                gridData: const FlGridData(show: false),
                titlesData: const FlTitlesData(show: false),
                borderData: FlBorderData(show: false),
                minY: 0,
                maxY: maxY < 1 ? 1 : maxY * 1.1,
                lineBarsData: [
                  LineChartBarData(
                    spots: spots,
                    isCurved: true,
                    color: color,
                    barWidth: 1.5,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: color.withOpacity(0.1),
                    ),
                  ),
                ],
                // ✅ tooltip عند الضغط — نفس نمط _historyChart
                lineTouchData: LineTouchData(
                  enabled: true,
                  touchTooltipData: LineTouchTooltipData(
                    getTooltipColor: (_) => AppColors.bgHover,
                    tooltipBorder:
                        const BorderSide(color: AppColors.borderLight),
                    getTooltipItems: (touched) {
                      return touched.map((s) {
                        final v = s.y;
                        return LineTooltipItem(
                          '${v.toStringAsFixed(v < 10 ? 2 : 1)}$unit',
                          TextStyle(
                            color: color,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        );
                      }).toList();
                    },
                  ),
                  // dot يظهر عند الـ touch
                  getTouchedSpotIndicator:
                      (LineChartBarData bar, List<int> indexes) {
                    return indexes.map((i) {
                      return TouchedSpotIndicatorData(
                        FlLine(color: color.withOpacity(0.5), strokeWidth: 1),
                        FlDotData(
                          show: true,
                          getDotPainter: (spot, _, __, ___) =>
                              FlDotCirclePainter(
                            radius: 3,
                            color: color,
                            strokeWidth: 1.5,
                            strokeColor: AppColors.bgCard,
                          ),
                        ),
                      );
                    }).toList();
                  },
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _interfaceOverviewCard(Map<String, dynamic> iface) {
    final name = iface['interfaceName']?.toString() ?? '';
    final tIn = (iface['throughputIn'] as num?)?.toDouble() ?? 0;
    final tOut = (iface['throughputOut'] as num?)?.toDouble() ?? 0;
    final lat = (iface['latency'] as num?)?.toDouble() ?? 0;
    final loss = (iface['packetLoss'] as num?)?.toInt() ?? 0;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
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
            name,
            style: const TextStyle(
              color: AppColors.accent,
              fontSize: 12,
              fontWeight: FontWeight.w600,
              fontFamily: 'monospace',
            ),
          ),
          const SizedBox(height: 8),
          _kvRow('Débit Entrant', '${tIn.toStringAsFixed(2)} Mbps'),
          _kvRow('Débit Sortant', '${tOut.toStringAsFixed(2)} Mbps'),
          _kvRow('Latence', '${lat.toStringAsFixed(1)} ms'),
          _kvRow('Perte de Paquets', '$loss%'),
        ],
      ),
    );
  }

  Widget _kvRow(String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(k,
              style: const TextStyle(
                  color: AppColors.textMuted, fontSize: 10)),
          Text(
            v,
            style: const TextStyle(
              color: AppColors.textPrimary,
              fontSize: 11,
              fontFamily: 'monospace',
            ),
          ),
        ],
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB — AI RESULT
  // ════════════════════════════════════════
  Widget _buildAiResultTab(DeviceController c) {
    return Obx(() {
      if (c.devicePredictions.isEmpty) {
        return const Center(
          child: Text(
            'Aucune prédiction pour cet appareil',
            style: TextStyle(color: AppColors.textMuted, fontSize: 13),
          ),
        );
      }

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          ...c.devicePredictions.entries
              .map((e) => _predictionCard(e.key, e.value)),
          const SizedBox(height: 80),
        ],
      );
    });
  }

  Widget _predictionCard(String iface, Map<String, dynamic> pred) {
    final attackType = pred['predictedAttack']?.toString() ?? 'normal';
    final isAttack = attackType.isNotEmpty && attackType != 'normal';
    final conf = pred['confidence'];
    final anomaly = pred['anomalyScore'];

    // Parse topFeatures (JSON string) — نفس React: JSON.parse(pred.topFeatures || '[]')
    List<dynamic> topFeatures = [];
    final raw = pred['topFeatures'] as String?;
    if (raw != null && raw.isNotEmpty && raw != '[]') {
      topFeatures = _safeJsonDecode(raw);
    }

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: isAttack
            ? AppColors.danger.withOpacity(0.05)
            : AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: isAttack
              ? AppColors.danger.withOpacity(0.2)
              : AppColors.borderLight,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                iface,
                style: const TextStyle(
                  color: AppColors.accent,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                  fontFamily: 'monospace',
                ),
              ),
              Container(
                padding: const EdgeInsets.symmetric(
                    horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: isAttack
                      ? AppColors.danger.withOpacity(0.15)
                      : AppColors.success.withOpacity(0.15),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  attackType,
                  style: TextStyle(
                    color:
                        isAttack ? AppColors.danger : AppColors.success,
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),

          // Confidence bar
          if (conf is num) ...[
            const SizedBox(height: 12),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Text('Confiance',
                    style: TextStyle(
                        color: AppColors.textMuted, fontSize: 10)),
                Text(
                  '${(conf * 100).toStringAsFixed(0)}%',
                  style: const TextStyle(
                    color: AppColors.textPrimary,
                    fontSize: 10,
                    fontFamily: 'monospace',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Container(
              height: 6,
              decoration: BoxDecoration(
                color: AppColors.bgPrimary,
                borderRadius: BorderRadius.circular(3),
              ),
              child: FractionallySizedBox(
                alignment: Alignment.centerLeft,
                widthFactor: conf.toDouble().clamp(0.0, 1.0),
                child: Container(
                  decoration: BoxDecoration(
                    color: isAttack
                        ? AppColors.danger
                        : AppColors.success,
                    borderRadius: BorderRadius.circular(3),
                  ),
                ),
              ),
            ),
          ],

          // Top features
          if (topFeatures.isNotEmpty) ...[
            const SizedBox(height: 12),
            const Text('Principales Caractéristiques',
                style: TextStyle(
                    color: AppColors.textMuted, fontSize: 10)),
            const SizedBox(height: 6),
            ...topFeatures.take(5).map((f) {
              if (f is! Map) return const SizedBox.shrink();
              final name = f['name']?.toString() ?? '';
              final value = f['value']?.toString() ?? '';
              final imp = (f['importance'] as num?)?.toDouble() ??
                  (f['deviation'] as num?)?.toDouble() ??
                  0.5;
              return Padding(
                padding: const EdgeInsets.only(bottom: 4),
                child: Row(
                  children: [
                    Expanded(
                      flex: 3,
                      child: Text(
                        name,
                        style: const TextStyle(
                            color: AppColors.textSecondary,
                            fontSize: 10),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    Expanded(
                      flex: 2,
                      child: Container(
                        height: 4,
                        decoration: BoxDecoration(
                          color: AppColors.bgPrimary,
                          borderRadius: BorderRadius.circular(2),
                        ),
                        child: FractionallySizedBox(
                          alignment: Alignment.centerLeft,
                          widthFactor:
                              ((imp * 20) / 100).clamp(0.05, 1.0),
                          child: Container(
                            decoration: BoxDecoration(
                              color: AppColors.accent,
                              borderRadius: BorderRadius.circular(2),
                            ),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    SizedBox(
                      width: 50,
                      child: Text(
                        value,
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 10,
                          fontFamily: 'monospace',
                        ),
                        textAlign: TextAlign.right,
                      ),
                    ),
                  ],
                ),
              );
            }),
          ],

          // Anomaly score
          if (anomaly is num) ...[
            const SizedBox(height: 8),
            Text(
              'Score d\'Anomalie : ${anomaly.toStringAsFixed(3)}',
              style: TextStyle(
                color: anomaly < 0
                    ? AppColors.danger
                    : AppColors.success,
                fontSize: 10,
                fontFamily: 'monospace',
              ),
            ),
          ],
        ],
      ),
    );
  }

  // Safe JSON decode للـ topFeatures (يجي كـ JSON string من backend)
  List<dynamic> _safeJsonDecode(String s) {
    try {
      final decoded = jsonDecode(s);
      if (decoded is List) return decoded;
      return [];
    } catch (_) {
      return [];
    }
  }

  // ════════════════════════════════════════
  //  TAB — KPI
  // ════════════════════════════════════════
  Widget _buildKpiTab(DeviceController c) {
    return _KpiTabBody(controller: c, deviceName: deviceName);
  }

  // ════════════════════════════════════════
  //  TAB — INTERFACES (router only)
  // ════════════════════════════════════════
  Widget _buildInterfacesTab(DeviceController c) {
    return Obx(() {
      // Group events by interface
      final grouped = <String, List<Map<String, dynamic>>>{};
      for (final ev in c.interfaceEvents) {
        final name = ev['interfaceName']?.toString() ?? '';
        if (name.isEmpty) continue;
        grouped.putIfAbsent(name, () => []).add(ev);
      }

      // Live interfaces from latestMetric
      final liveIfaces = c.latestMetric.value?['interfaces'] as List?;
      if (liveIfaces != null) {
        for (final i in liveIfaces) {
          if (i is Map) {
            final name = i['interfaceName']?.toString() ?? '';
            if (name.isNotEmpty) grouped.putIfAbsent(name, () => []);
          }
        }
      }

      final ifaceNames = grouped.keys.toList()..sort();

      // Time window
      final now = DateTime.now();
      final periodMs = _periodMillis(c.interfacesPeriod.value);
      final startTime = now.subtract(Duration(milliseconds: periodMs));

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // Period selector
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: DropdownButton<String>(
              value: c.interfacesPeriod.value,
              isExpanded: true,
              underline: const SizedBox.shrink(),
              dropdownColor: AppColors.bgCard,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 13,
              ),
              items: const [
                DropdownMenuItem(value: '1h', child: Text('Dernière heure')),
                DropdownMenuItem(value: '6h', child: Text('Dernières 6h')),
                DropdownMenuItem(
                    value: '24h', child: Text('Dernières 24h')),
                DropdownMenuItem(value: '7d', child: Text('Dernière semaine')),
                DropdownMenuItem(
                    value: '30d', child: Text('Derniers 30 jours')),
              ],
              onChanged: (v) {
                if (v != null) c.changeInterfacesPeriod(v);
              },
            ),
          ),
          const SizedBox(height: 12),

          if (c.interfacesLoading.value && c.interfaceEvents.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Center(
                  child:
                      CircularProgressIndicator(color: AppColors.accent)),
            )
          else if (ifaceNames.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(
                  vertical: 40, horizontal: 24),
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderLight),
              ),
              child: const Center(
                child: Text(
                  'Aucune interface détectée',
                  style: TextStyle(
                      color: AppColors.textMuted, fontSize: 13),
                ),
              ),
            )
          else
            ...ifaceNames.map((name) {
              final events = grouped[name] ?? [];
              // Live status
              bool? isUp;
              if (liveIfaces != null) {
                final live = liveIfaces.firstWhere(
                  (i) =>
                      i is Map && i['interfaceName'] == name,
                  orElse: () => null,
                );
                if (live is Map) {
                  isUp = live['isUp'] != false;
                }
              }

              final windowEvents = events.where((ev) {
                final s = DateTime.tryParse(ev['startedAt'] ?? '');
                if (s == null) return false;
                final e = DateTime.tryParse(ev['endedAt'] ?? '') ?? now;
                return e.isAfter(startTime) && !s.isAfter(now);
              }).toList();

              final downCount = windowEvents
                  .where((e) => e['status'] == 'DOWN')
                  .length;

              return _interfaceTimelineCard(
                  name, isUp, windowEvents, downCount, startTime, now);
            }),

          const SizedBox(height: 80),
        ],
      );
    });
  }

  Widget _interfaceTimelineCard(
    String name,
    bool? isUp,
    List<Map<String, dynamic>> events,
    int downCount,
    DateTime startTime,
    DateTime now,
  ) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                width: 8,
                height: 8,
                decoration: BoxDecoration(
                  color: isUp == null
                      ? AppColors.textMuted
                      : (isUp ? AppColors.success : AppColors.statusGrey),
                  shape: BoxShape.circle,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                name,
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 13,
                  fontFamily: 'monospace',
                  fontWeight: FontWeight.w600,
                ),
              ),
              const SizedBox(width: 8),
              Text(
                isUp == null ? 'inconnu' : (isUp ? 'ACTIF' : 'INACTIF'),
                style: TextStyle(
                  color: isUp == null
                      ? AppColors.textMuted
                      : (isUp ? AppColors.success : AppColors.statusGrey),
                  fontSize: 11,
                ),
              ),
              const Spacer(),
              Text(
                downCount == 0
                    ? 'Aucune panne'
                    : '$downCount panne${downCount > 1 ? "s" : ""}',
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 9),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Timeline bar
          LayoutBuilder(
            builder: (ctx, cons) {
              final width = cons.maxWidth;
              final totalMs =
                  now.difference(startTime).inMilliseconds.toDouble();
              return Container(
                height: 18,
                decoration: BoxDecoration(
                  color: AppColors.bgPrimary,
                  borderRadius: BorderRadius.circular(3),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(3),
                  child: Stack(
                    children: events.map((ev) {
                      final startRaw =
                          DateTime.tryParse(ev['startedAt'] ?? '');
                      if (startRaw == null) return const SizedBox.shrink();
                      final endRaw =
                          DateTime.tryParse(ev['endedAt'] ?? '') ?? now;

                      final s = startRaw.isBefore(startTime)
                          ? startTime
                          : startRaw;
                      final e = endRaw.isAfter(now) ? now : endRaw;
                      if (!e.isAfter(s)) return const SizedBox.shrink();

                      final leftPct = s
                              .difference(startTime)
                              .inMilliseconds
                              .toDouble() /
                          totalMs *
                          100;
                      final widthPct = e
                              .difference(s)
                              .inMilliseconds
                              .toDouble() /
                          totalMs *
                          100;

                      final isUpEv = ev['status'] == 'UP';
                      return Positioned(
                        left: width * leftPct / 100,
                        width: width *
                            widthPct.clamp(0.3, 100.0) /
                            100,
                        top: 0,
                        bottom: 0,
                        child: Container(
                          color: isUpEv
                              ? AppColors.success.withOpacity(0.5)
                              : AppColors.statusGrey,
                        ),
                      );
                    }).toList(),
                  ),
                ),
              );
            },
          ),
          const SizedBox(height: 4),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                DateFormat('MM-dd HH:mm').format(startTime.toLocal()),
                style: const TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 8,
                    fontFamily: 'monospace'),
              ),
              const Text(
                'maintenant',
                style: TextStyle(
                    color: AppColors.textMuted,
                    fontSize: 8,
                    fontFamily: 'monospace'),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB — HISTORY
  // ════════════════════════════════════════
  Widget _buildHistoryTab(DeviceController c) {
    return Obx(() {
      final allMetrics = <Map<String, dynamic>>[];
      c.availableMetrics.forEach((_, list) {
        allMetrics.addAll(list);
      });

      final metricInfo = allMetrics.firstWhere(
        (m) => m['name'] == c.historyMetric.value,
        orElse: () => {},
      );
      final unit = metricInfo['unit']?.toString() ?? '';

      final points = (c.historyData.value?['points'] as List?) ?? [];
      final stats = (c.historyData.value?['stats'] as Map?) ?? {};

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // Metric picker
          Container(
            padding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 4),
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: DropdownButton<String>(
              value: c.historyMetric.value,
              isExpanded: true,
              underline: const SizedBox.shrink(),
              dropdownColor: AppColors.bgCard,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 12,
                fontFamily: 'monospace',
              ),
              items: _buildMetricItems(c.availableMetrics),
              onChanged: (v) {
                if (v != null) c.changeHistoryMetric(v);
              },
            ),
          ),
          const SizedBox(height: 8),

          // Period
          Container(
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
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 12,
              ),
              items: const [
                DropdownMenuItem(value: '1h', child: Text('Dernière heure')),
                DropdownMenuItem(value: '6h', child: Text('Dernières 6h')),
                DropdownMenuItem(value: '24h', child: Text('Dernières 24h')),
                DropdownMenuItem(
                    value: '7d', child: Text('Derniers 7 jours')),
                DropdownMenuItem(
                    value: '30d', child: Text('Derniers 30 jours')),
              ],
              onChanged: (v) {
                if (v != null) c.changeHistoryPeriod(v);
              },
            ),
          ),
          const SizedBox(height: 16),

          // Chart
          if (c.historyLoading.value)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 40),
              child: Center(
                  child:
                      CircularProgressIndicator(color: AppColors.accent)),
            )
          else if (points.isEmpty)
            Container(
              padding: const EdgeInsets.symmetric(
                  vertical: 40, horizontal: 24),
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.borderLight),
              ),
              child: Center(
                child: Column(
                  children: [
                    Text(
                      'Aucune donnée pour ${c.historyMetric.value}',
                      style: const TextStyle(
                          color: AppColors.textMuted, fontSize: 13),
                    ),
                    const SizedBox(height: 4),
                    const Text(
                      'Essayez une période plus longue ou une autre métrique',
                      style: TextStyle(
                          color: AppColors.textMuted, fontSize: 10),
                    ),
                  ],
                ),
              ),
            )
          else ...[
            _historyChart(c, points, unit),
            const SizedBox(height: 12),
            // Stats: Min / Avg / Max
            Row(
              children: [
                Expanded(
                    child: _statTile(
                        'Min', stats['min'], unit, AppColors.textPrimary)),
                const SizedBox(width: 8),
                Expanded(
                    child: _statTile(
                        'Moy', stats['avg'], unit, AppColors.accent)),
                const SizedBox(width: 8),
                Expanded(
                    child: _statTile(
                        'Max', stats['max'], unit, AppColors.warning)),
              ],
            ),
            const SizedBox(height: 6),
            Center(
              child: Text(
                'Basé sur ${stats['count'] ?? 0} mesures',
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 10),
              ),
            ),
          ],
          const SizedBox(height: 80),
        ],
      );
    });
  }

  List<DropdownMenuItem<String>> _buildMetricItems(
      Map<String, List<Map<String, dynamic>>> available) {
    final items = <DropdownMenuItem<String>>[];

    void addCategory(String cat, List<Map<String, dynamic>> list) {
      if (list.isEmpty) return;
      for (final m in list) {
        final name = m['name']?.toString() ?? '';
        final unit = m['unit']?.toString() ?? '';
        items.add(DropdownMenuItem(
          value: name,
          child: Text(
            '$name${unit.isNotEmpty ? " ($unit)" : ""}',
            style: const TextStyle(
              fontSize: 11,
              fontFamily: 'monospace',
            ),
          ),
        ));
      }
    }

    addCategory('System', available['system'] ?? []);
    addCategory('TCP', available['tcp'] ?? []);
    addCategory('Security', available['security'] ?? []);

    // Default fallback
    if (items.isEmpty) {
      items.add(const DropdownMenuItem(
          value: 'cpuUsage', child: Text('cpuUsage')));
    }
    return items;
  }

  Widget _historyChart(
      DeviceController c, List points, String unit) {
    final spots = <FlSpot>[];
    for (int i = 0; i < points.length; i++) {
      final p = points[i];
      if (p is Map) {
        final v = (p['value'] as num?)?.toDouble() ?? 0;
        spots.add(FlSpot(i.toDouble(), v));
      }
    }

    if (spots.isEmpty) return const SizedBox();

    final maxY = spots.map((s) => s.y).reduce((a, b) => a > b ? a : b);

    return Container(
      height: 220,
      padding: const EdgeInsets.fromLTRB(8, 12, 16, 12),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                c.historyMetric.value,
                style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                  fontFamily: 'monospace',
                ),
              ),
              Text(
                '${points.length} points',
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 9),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Expanded(
            child: LineChart(
              LineChartData(
                minY: 0,
                maxY: maxY < 1 ? 1 : maxY * 1.1,
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: false,
                  horizontalInterval: maxY > 0 ? maxY / 4 : 1,
                  getDrawingHorizontalLine: (_) => const FlLine(
                    color: AppColors.borderLight,
                    strokeWidth: 0.5,
                    dashArray: [3, 3],
                  ),
                ),
                titlesData: FlTitlesData(
                  bottomTitles: const AxisTitles(
                      sideTitles: SideTitles(showTitles: false)),
                  rightTitles: const AxisTitles(
                      sideTitles: SideTitles(showTitles: false)),
                  topTitles: const AxisTitles(
                      sideTitles: SideTitles(showTitles: false)),
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 35,
                      interval: maxY > 0 ? maxY / 4 : 1,
                      getTitlesWidget: (value, _) => Text(
                        value < 10
                            ? value.toStringAsFixed(2)
                            : value < 100
                                ? value.toStringAsFixed(1)
                                : value.toStringAsFixed(0),
                        style: const TextStyle(
                            color: AppColors.textMuted, fontSize: 9),
                      ),
                    ),
                  ),
                ),
                borderData: FlBorderData(show: false),
                lineTouchData: LineTouchData(
                  enabled: true,
                  touchTooltipData: LineTouchTooltipData(
                    getTooltipColor: (_) => AppColors.bgHover,
                    tooltipBorder:
                        const BorderSide(color: AppColors.borderLight),
                    getTooltipItems: (touched) {
                      return touched.map((s) {
                        final idx = s.x.toInt();
                        final p =
                            (idx < points.length) ? points[idx] : null;
                        final ts = p is Map ? p['timestamp']?.toString() : null;
                        final fmt = ts != null
                            ? DateFormat('MM-dd HH:mm:ss')
                                .format(DateTime.parse(ts).toLocal())
                            : '';
                        return LineTooltipItem(
                          '${s.y.toStringAsFixed(2)}${unit.isNotEmpty ? " $unit" : ""}\n$fmt',
                          const TextStyle(
                              color: AppColors.textPrimary, fontSize: 11),
                        );
                      }).toList();
                    },
                  ),
                ),
                lineBarsData: [
                  LineChartBarData(
                    spots: spots,
                    isCurved: true,
                    color: AppColors.accent,
                    barWidth: 1.5,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                      show: true,
                      color: AppColors.accent.withOpacity(0.1),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _statTile(String label, dynamic value, String unit, Color color) {
    final display = value is num ? value.toStringAsFixed(2) : '—';
    return Container(
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: Column(
        children: [
          Text(label.toUpperCase(),
              style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 9,
                  letterSpacing: 1)),
          const SizedBox(height: 4),
          Text(
            display,
            style: TextStyle(
              color: color,
              fontSize: 14,
              fontFamily: 'monospace',
            ),
          ),
          if (unit.isNotEmpty)
            Text(
              unit,
              style: const TextStyle(
                  color: AppColors.textMuted, fontSize: 9),
            ),
        ],
      ),
    );
  }

  // ════════════════════════════════════════
  //  TAB — COUNTERS
  // ════════════════════════════════════════
  Widget _buildCountersTab(DeviceController c) {
    return Obx(() {
      final m = c.isOffline.value ? null : c.latestMetric.value;
      final tcp =
          (m?['tcpStats'] is Map) ? (m!['tcpStats'] as Map) : {};
      final sec = (m?['securityMetric'] is Map)
          ? (m!['securityMetric'] as Map)
          : {};

      // نفس React TabCounters list بالضبط
      final counters = <List<dynamic>>[
        ['passiveOpens', tcp['passiveOpens']],
        ['activeOpens', tcp['activeOpens']],
        ['inSegs', tcp['inSegs']],
        ['outSegs', tcp['outSegs']],
        ['inErrs', tcp['inErrs']],
        ['outRsts', tcp['outRsts']],
        ['tcpRetransmissions', tcp['tcpRetransmissions']],
        ['icmpInMsgs', tcp['icmpInMsgs']],
        ['icmpInErrors', tcp['icmpInErrors']],
        ['udpInDatagrams', tcp['udpInDatagrams']],
        ['udpOutDatagrams', tcp['udpOutDatagrams']],
        ['udpInErrors', tcp['udpInErrors']],
        ['udpNoPorts', tcp['udpNoPorts']],
        ['halfOpenConnections', sec['halfOpenConnections']],
        ['uniqueSourceIPs', sec['uniqueSourceIPs']],
        ['failedLogins', sec['failedLogins']],
        ['timeWaitConnections', sec['timeWaitConnections']],
      ];

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          const Padding(
            padding: EdgeInsets.only(bottom: 8),
            child: Text(
              'Compteurs cumulatifs bruts',
              style:
                  TextStyle(color: AppColors.textSecondary, fontSize: 12),
            ),
          ),
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: Column(
              children: counters.map((row) {
                return Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 12, vertical: 8),
                  decoration: const BoxDecoration(
                    border: Border(
                      bottom: BorderSide(
                          color: AppColors.borderLight, width: 0.5),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        row[0].toString(),
                        style: const TextStyle(
                          color: AppColors.textMuted,
                          fontSize: 11,
                          fontFamily: 'monospace',
                        ),
                      ),
                      Text(
                        row[1]?.toString() ?? '—',
                        style: const TextStyle(
                          color: AppColors.textPrimary,
                          fontSize: 11,
                          fontFamily: 'monospace',
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ],
                  ),
                );
              }).toList(),
            ),
          ),
          const SizedBox(height: 80),
        ],
      );
    });
  }

  // ════════════════════════════════════════
  //  TAB — PROPERTIES
  // ════════════════════════════════════════
  Widget _buildPropertiesTab(DeviceController c) {
    return Obx(() {
      final m = c.latestMetric.value;
      final ifaces = m?['interfaces'] is List
          ? (m!['interfaces'] as List)
              .whereType<Map>()
              .map((i) => i['interfaceName']?.toString() ?? '')
              .where((n) => n.isNotEmpty)
              .join(', ')
          : '—';
      final neighbors = m?['neighbors'] is List
          ? (m!['neighbors'] as List).join(', ')
          : '—';

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // Basic properties (نفس React)
          Container(
            decoration: BoxDecoration(
              color: AppColors.bgCard,
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: AppColors.borderLight),
            ),
            child: Column(
              children: [
                _propRow('Nom de l\'Appareil', deviceName),
                _propRow('Type d\'Appareil', _deviceType()),
                _propRow('Statut',
                    m?['status']?.toString() ?? 'inconnu'),
                _propRow('Interfaces',
                    ifaces.isEmpty ? '—' : ifaces),
                _propRow(
                    'Voisins', neighbors.isEmpty ? '—' : neighbors),
              ],
            ),
          ),

          const SizedBox(height: 16),

          // ARP Table (نفس React TabProperties)
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Row(
                children: [
                  Text(
                    'Table ARP ',
                    style: TextStyle(
                      color: AppColors.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                  Text(
                    '(Voisins Couche 2)',
                    style: TextStyle(
                      color: AppColors.textMuted,
                      fontSize: 10,
                    ),
                  ),
                ],
              ),
              Text(
                c.arpLoading.value
                    ? 'chargement…'
                    : '${c.arpEntries.length} entrées',
                style: const TextStyle(
                    color: AppColors.textMuted, fontSize: 10),
              ),
            ],
          ),
          const SizedBox(height: 8),

          if (!c.arpLoading.value && c.arpEntries.isEmpty)
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.bgPrimary,
                borderRadius: BorderRadius.circular(8),
              ),
              child: const Center(
                child: Text(
                  'Aucune entrée ARP enregistrée',
                  style: TextStyle(
                      color: AppColors.textMuted, fontSize: 11),
                ),
              ),
            )
          else
            Container(
              decoration: BoxDecoration(
                color: AppColors.bgCard,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.borderLight),
              ),
              child: Column(
                children: [
                  // Header
                  Container(
                    padding: const EdgeInsets.all(10),
                    decoration: const BoxDecoration(
                      border: Border(
                        bottom: BorderSide(color: AppColors.borderLight),
                      ),
                    ),
                    child: Row(
                      children: const [
                        Expanded(
                          flex: 4,
                          child: Text(
                            'Adresse IP',
                            style: TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                        Expanded(
                          flex: 5,
                          child: Text(
                            'Adresse MAC',
                            style: TextStyle(
                              color: AppColors.textMuted,
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  // Rows — fields: ip / mac (مش ipAddress / macAddress)
                  ...c.arpEntries.map((e) {
                    return Container(
                      padding: const EdgeInsets.all(10),
                      decoration: const BoxDecoration(
                        border: Border(
                          bottom: BorderSide(
                              color: AppColors.borderLight,
                              width: 0.5),
                        ),
                      ),
                      child: Row(
                        children: [
                          Expanded(
                            flex: 4,
                            child: Text(
                              e['ip']?.toString() ?? '—',
                              style: const TextStyle(
                                color: AppColors.textPrimary,
                                fontSize: 10,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ),
                          Expanded(
                            flex: 5,
                            child: Text(
                              e['mac']?.toString() ?? '—',
                              style: const TextStyle(
                                color: AppColors.textSecondary,
                                fontSize: 10,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ),
                        ],
                      ),
                    );
                  }),
                ],
              ),
            ),

          const SizedBox(height: 80),
        ],
      );
    });
  }

  Widget _propRow(String label, String value) {
    return Container(
      padding:
          const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
      decoration: const BoxDecoration(
        border: Border(
          bottom: BorderSide(color: AppColors.borderLight, width: 0.5),
        ),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(
                color: AppColors.textSecondary, fontSize: 12),
          ),
          Flexible(
            child: Text(
              value,
              style: const TextStyle(
                color: AppColors.textPrimary,
                fontSize: 12,
                fontFamily: 'monospace',
              ),
              textAlign: TextAlign.right,
            ),
          ),
        ],
      ),
    );
  }

  String _deviceType() {
    if (deviceName.contains('router')) return 'routeur';
    return 'serveur';
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
      case '30d':
        return 2592000000;
      default:
        return 86400000;
    }
  }
}

// ──────────────────────────────────────────
//  KPI Tab Body (StatefulWidget لـ showAll toggle)
// ──────────────────────────────────────────
class _KpiTabBody extends StatefulWidget {
  final DeviceController controller;
  final String deviceName;

  const _KpiTabBody({
    required this.controller,
    required this.deviceName,
  });

  @override
  State<_KpiTabBody> createState() => _KpiTabBodyState();
}

class _KpiTabBodyState extends State<_KpiTabBody> {
  bool showAll = false;

  @override
  Widget build(BuildContext context) {
    return Obx(() {
      // Get device-specific KPIs
      final deviceKpis = kKpiMap[widget.deviceName] ??
          kKpiMap[widget.deviceName.contains('router')
              ? 'edge-router'
              : 'web-server']!;

      final kpis = showAll ? kAllKpis : deviceKpis;

      return ListView(
        padding: const EdgeInsets.all(12),
        children: [
          // Header
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: RichText(
                  text: TextSpan(
                    style: const TextStyle(
                        color: AppColors.textSecondary, fontSize: 12),
                    children: [
                      const TextSpan(text: 'Métriques clés pour '),
                      TextSpan(
                        text: widget.deviceName,
                        style: const TextStyle(
                          color: AppColors.accent,
                          fontFamily: 'monospace',
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              GestureDetector(
                onTap: () => setState(() => showAll = !showAll),
                child: Container(
                  padding: const EdgeInsets.symmetric(
                      horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: showAll
                        ? AppColors.accent.withOpacity(0.1)
                        : AppColors.bgCard,
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(
                      color: showAll
                          ? AppColors.accent
                          : AppColors.borderLight,
                    ),
                  ),
                  child: Text(
                    showAll ? 'KPIs de l\'Appareil' : 'Tout Afficher',
                    style: TextStyle(
                      color: showAll
                          ? AppColors.accent
                          : AppColors.textSecondary,
                      fontSize: 11,
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // KPI cards with sparklines
          ...kpis.map((metric) {
            return _kpiCard(metric);
          }).where((w) => w != null).cast<Widget>(),

          const SizedBox(height: 80),
        ],
      );
    });
  }

  // Build sparkline data using same logic as React
  List<FlSpot> _buildSparkline(String metric) {
    final result = <FlSpot>[];
    for (int i = 0; i < widget.controller.metricsHistory.length; i++) {
      final m = widget.controller.metricsHistory[i];
      double v = (m[metric] as num?)?.toDouble() ?? 0;

      // Check tcpStats / securityMetric / interfaces
      if (v == 0) {
        final tcp = m['tcpStats'];
        if (tcp is Map && tcp[metric] is num) {
          v = (tcp[metric] as num).toDouble();
        }
      }
      if (v == 0) {
        final sec = m['securityMetric'];
        if (sec is Map && sec[metric] is num) {
          v = (sec[metric] as num).toDouble();
        }
      }
      if (v == 0) {
        final ifaces = m['interfaces'];
        if (ifaces is List) {
          for (final iface in ifaces) {
            if (iface is Map && iface[metric] is num) {
              final iv = (iface[metric] as num).toDouble();
              if (iv != 0) {
                v = iv;
                break;
              }
            }
          }
        }
      }
      result.add(FlSpot(i.toDouble(), v));
    }

    // If offline, append 0 point
    if (widget.controller.isOffline.value && result.isNotEmpty) {
      result.add(FlSpot(result.last.x + 1, 0));
    }

    return result;
  }

  Widget? _kpiCard(String metric) {
    final spots = _buildSparkline(metric);
    final latest = widget.controller.isOffline.value
        ? 0.0
        : (spots.isEmpty ? 0.0 : spots.last.y);

    // Skip if showAll و كل القيم 0
    if (showAll && spots.every((s) => s.y == 0)) return null;

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(10),
      decoration: BoxDecoration(
        color: AppColors.bgPrimary,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                metric,
                style: const TextStyle(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                  fontFamily: 'monospace',
                ),
              ),
              Text(
                latest.toStringAsFixed(2),
                style: const TextStyle(
                  color: AppColors.textPrimary,
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  fontFamily: 'monospace',
                ),
              ),
            ],
          ),
          if (spots.length > 2) ...[
            const SizedBox(height: 6),
            SizedBox(
              height: 40,
              child: LineChart(
                LineChartData(
                  gridData: const FlGridData(show: false),
                  titlesData: const FlTitlesData(show: false),
                  borderData: FlBorderData(show: false),
                  minY: 0,
                  lineBarsData: [
                    LineChartBarData(
                      spots: spots,
                      isCurved: true,
                      color: AppColors.accent,
                      barWidth: 1.5,
                      dotData: const FlDotData(show: false),
                      belowBarData: BarAreaData(
                        show: true,
                        color: AppColors.accent.withOpacity(0.1),
                      ),
                    ),
                  ],
                  lineTouchData: const LineTouchData(enabled: false),
                ),
              ),
            ),
          ],
        ],
      ),
    );
  }
}
