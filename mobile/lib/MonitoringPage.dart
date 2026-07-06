import 'package:flutter/material.dart';
import 'package:fl_chart/fl_chart.dart';
import 'package:get/get.dart';

import 'app_theme.dart';
import 'controllers/monitoring_controller.dart';

// ──────────────────────────────────────────────────────────────
//  MonitoringPage
//  مرجعها: React pages/Monitoring.jsx
//
//  ── الإصلاحات في هذا الـ batch ──
//  1. القيمة الحالية تظهر دائماً فوق كل bar (بدون اسم الجهاز)
//     — اسم الجهاز موجود تحت في الـ X-axis
//     — استخدمنا `showingTooltipIndicators: [0]` مع
//        getTooltipItem يرجع فقط الرقم
//  2. الضغط على bar لا يفتح صفحة detail (فقط من Drawer)
// ──────────────────────────────────────────────────────────────
class MonitoringPage extends StatelessWidget {
  const MonitoringPage({super.key});

  static const List<String> devices = [
    'edge-router',
    'core-router',
    'web-server',
    'dns-server',
    'ftp-server',
    'db-server',
    'supervision-app',
  ];

  static const List<String> servers = [
    'web-server',
    'dns-server',
    'ftp-server',
    'db-server',
    'supervision-app',
  ];

  // Colors
  static const Color colorCpu = Color(0xFF10B981);
  static const Color colorIn = Color(0xFF3B82F6);
  static const Color colorOut = Color(0xFF10B981);
  static const Color colorLatency = Color(0xFFF59E0B);
  static const Color colorConn = Color(0xFF8B5CF6);
  static const Color colorMem = Color(0xFF3B82F6);
  static const Color colorDisk = Color(0xFFA855F7);

  @override
  Widget build(BuildContext context) {
    final MonitoringController c = Get.put(MonitoringController());

    return RefreshIndicator(
      onRefresh: () => c.refresh(),
      color: AppColors.accent,
      backgroundColor: AppColors.bgCard,
      child: Obx(() {
        final m = c.deviceMetrics;

        return ListView(
          padding: const EdgeInsets.all(12),
          children: [
            const Text(
              'Surveillance Réseau',
              style: TextStyle(
                color: AppColors.textPrimary,
                fontSize: 18,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 16),

            if (m.isEmpty)
              _buildEmptyState()
            else ...[
              _buildSingleSeriesCard(
                title: 'Utilisation CPU (%)',
                series: _buildCpuData(m),
                color: colorCpu,
                yMaxFixed: 100,
                unit: '%',
                decimals: 1,
              ),
              const SizedBox(height: 12),
              _buildThroughputCard(m),
              const SizedBox(height: 12),
              _buildSingleSeriesCard(
                title: 'Latence (ms)',
                series: _buildLatencyData(m),
                color: colorLatency,
                unit: 'ms',
                decimals: 1,
              ),
              const SizedBox(height: 12),
              _buildSingleSeriesCard(
                title: 'Connexions (Serveurs)',
                series: _buildConnectionsData(m),
                color: colorConn,
                unit: '',
                decimals: 0,
              ),
              const SizedBox(height: 12),
              _buildSingleSeriesCard(
                title: 'Utilisation Mémoire (%)',
                series: _buildMemData(m),
                color: colorMem,
                yMaxFixed: 100,
                unit: '%',
                decimals: 1,
              ),
              const SizedBox(height: 12),
              _buildDiskCard(m),
            ],

            const SizedBox(height: 80),
          ],
        );
      }),
    );
  }

  // ─── Data builders ───
  List<_BarValue> _buildCpuData(Map<String, Map<String, dynamic>> m) {
    return devices.map((d) {
      final cpu = (m[d]?['cpuUsage'] as num?)?.toDouble() ?? 0.0;
      return _BarValue(device: d, value: double.parse(cpu.toStringAsFixed(1)));
    }).toList();
  }

  List<_BarValue> _buildMemData(Map<String, Map<String, dynamic>> m) {
    return devices.map((d) {
      final mem = (m[d]?['memoryUsage'] as num?)?.toDouble() ?? 0.0;
      return _BarValue(device: d, value: double.parse(mem.toStringAsFixed(1)));
    }).toList();
  }

  List<_ThroughputData> _buildThroughputData(
      Map<String, Map<String, dynamic>> m) {
    return devices.map((d) {
      final ifaces = m[d]?['interfaces'] as List?;
      double totalIn = 0, totalOut = 0;
      if (ifaces != null) {
        for (final i in ifaces) {
          if (i is Map) {
            totalIn += (i['throughputIn'] as num?)?.toDouble() ?? 0;
            totalOut += (i['throughputOut'] as num?)?.toDouble() ?? 0;
          }
        }
      }
      return _ThroughputData(
        device: d,
        inValue: double.parse(totalIn.toStringAsFixed(2)),
        outValue: double.parse(totalOut.toStringAsFixed(2)),
      );
    }).toList();
  }

  List<_BarValue> _buildLatencyData(Map<String, Map<String, dynamic>> m) {
    return devices.map((d) {
      final ifaces = m[d]?['interfaces'] as List?;
      double maxLat = 0;
      if (ifaces != null) {
        for (final i in ifaces) {
          if (i is Map) {
            final lat = (i['latency'] as num?)?.toDouble() ?? 0;
            if (lat > maxLat) maxLat = lat;
          }
        }
      }
      return _BarValue(
          device: d, value: double.parse(maxLat.toStringAsFixed(1)));
    }).toList();
  }

  List<_BarValue> _buildConnectionsData(
      Map<String, Map<String, dynamic>> m) {
    return servers.map((d) {
      final conn = (m[d]?['connections'] as num?)?.toDouble() ?? 0.0;
      return _BarValue(device: d, value: conn);
    }).toList();
  }

  // ─── Single-series chart with always-visible value labels (React renderBarLabel pattern) ───
  Widget _buildSingleSeriesCard({
    required String title,
    required List<_BarValue> series,
    required Color color,
    double? yMaxFixed,
    String unit = '',
    int decimals = 1,
  }) {
    final maxVal = series.isEmpty
        ? 1.0
        : series.map((d) => d.value).reduce((a, b) => a > b ? a : b);
    final yMax = yMaxFixed ??
        (maxVal < 10 ? 10.0 : (maxVal * 1.25).ceilToDouble());

    String fmt(double v) {
      if (decimals == 0) return v.toStringAsFixed(0);
      return v.toStringAsFixed(decimals);
    }

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
          Text(
            title,
            style: const TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
            ),
          ),
          // مساحة كافية فوق كل bar للقيمة (مثل React margin: { top: 20 })
          const SizedBox(height: 18),
          SizedBox(
            height: 220,
            child: BarChart(
              BarChartData(
                alignment: BarChartAlignment.spaceAround,
                maxY: yMax,
                minY: 0,
                groupsSpace: 6,
                barGroups: series.asMap().entries.map((entry) {
                  // React: skip zero values
                  final showLabel = entry.value.value > 0;
                  return BarChartGroupData(
                    x: entry.key,
                    barRods: [
                      BarChartRodData(
                        toY: entry.value.value,
                        color: color,
                        width: 16,
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(4),
                          topRight: Radius.circular(4),
                        ),
                      ),
                    ],
                    showingTooltipIndicators: showLabel ? [0] : [],
                  );
                }).toList(),
                titlesData: _buildAxesTitles(
                    series.map((d) => d.device).toList(), yMax),
                gridData: _buildGrid(yMax),
                borderData: FlBorderData(show: false),
                barTouchData: BarTouchData(
                  enabled: true,
                  handleBuiltInTouches: false,
                  touchTooltipData: BarTouchTooltipData(
                    getTooltipColor: (_) => Colors.transparent,
                    tooltipBorder: BorderSide.none,
                    tooltipPadding: EdgeInsets.zero,
                    tooltipMargin: 2,
                    fitInsideHorizontally: true,
                    fitInsideVertically: true,
                    getTooltipItem: (group, _, rod, __) {
                      final v = series[group.x].value;
                      // نفس لون React renderBarLabel: #9ca3af (gray-400)
                      return BarTooltipItem(
                        fmt(v),
                        const TextStyle(
                          color: Color(0xFF9CA3AF),
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          fontFamily: 'monospace',
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Throughput card (2 bars: In + Out) ───
  Widget _buildThroughputCard(Map<String, Map<String, dynamic>> m) {
    final data = _buildThroughputData(m);
    final maxVal = data.isEmpty
        ? 1.0
        : data
            .map((d) => d.inValue > d.outValue ? d.inValue : d.outValue)
            .reduce((a, b) => a > b ? a : b);
    final yMax = maxVal < 1 ? 1.0 : (maxVal * 1.3).ceilToDouble();

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
          const Text(
            'Débit (Mbps)',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _legendItem(colorIn, 'Entrée'),
              const SizedBox(width: 12),
              _legendItem(colorOut, 'Sortie'),
            ],
          ),
          const SizedBox(height: 14),
          SizedBox(
            height: 220,
            child: BarChart(
              BarChartData(
                alignment: BarChartAlignment.spaceAround,
                maxY: yMax,
                minY: 0,
                groupsSpace: 8,
                barGroups: data.asMap().entries.map((entry) {
                  // React: skip zero values; show both In and Out labels
                  final showIn = entry.value.inValue > 0;
                  final showOut = entry.value.outValue > 0;
                  final indicators = <int>[];
                  if (showIn) indicators.add(0);
                  if (showOut) indicators.add(1);

                  return BarChartGroupData(
                    x: entry.key,
                    barsSpace: 2,
                    barRods: [
                      BarChartRodData(
                        toY: entry.value.inValue,
                        color: colorIn,
                        width: 9,
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(3),
                          topRight: Radius.circular(3),
                        ),
                      ),
                      BarChartRodData(
                        toY: entry.value.outValue,
                        color: colorOut,
                        width: 9,
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(3),
                          topRight: Radius.circular(3),
                        ),
                      ),
                    ],
                    showingTooltipIndicators: indicators,
                  );
                }).toList(),
                titlesData: _buildAxesTitles(
                    data.map((d) => d.device).toList(), yMax),
                gridData: _buildGrid(yMax),
                borderData: FlBorderData(show: false),
                barTouchData: BarTouchData(
                  enabled: true,
                  handleBuiltInTouches: false,
                  touchTooltipData: BarTouchTooltipData(
                    getTooltipColor: (_) => Colors.transparent,
                    tooltipBorder: BorderSide.none,
                    tooltipPadding: EdgeInsets.zero,
                    tooltipMargin: 2,
                    fitInsideHorizontally: true,
                    fitInsideVertically: true,
                    getTooltipItem: (group, _, rod, rodIdx) {
                      final v = rod.toY;
                      // نفس React: لون رمادي #9ca3af
                      return BarTooltipItem(
                        v.toStringAsFixed(1),
                        const TextStyle(
                          color: Color(0xFF9CA3AF),
                          fontSize: 9,
                          fontWeight: FontWeight.w600,
                          fontFamily: 'monospace',
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Disk card with always-visible % label ───
  Widget _buildDiskCard(Map<String, Map<String, dynamic>> m) {
    final data = devices.map((d) {
      final disk = (m[d]?['diskUsage'] as num?)?.toDouble() ?? 0.0;
      final used = (m[d]?['diskUsedGb'] as num?)?.toDouble() ?? 0.0;
      final total = (m[d]?['diskTotalGb'] as num?)?.toDouble() ?? 0.0;
      return _DiskData(
        device: d,
        disk: double.parse(disk.toStringAsFixed(1)),
        used: double.parse(used.toStringAsFixed(1)),
        total: double.parse(total.toStringAsFixed(1)),
      );
    }).toList();

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
          const Text(
            'Utilisation Disque (%)',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 13,
            ),
          ),
          const SizedBox(height: 18),
          SizedBox(
            height: 220,
            child: BarChart(
              BarChartData(
                alignment: BarChartAlignment.spaceAround,
                maxY: 100,
                minY: 0,
                groupsSpace: 6,
                barGroups: data.asMap().entries.map((entry) {
                  return BarChartGroupData(
                    x: entry.key,
                    barRods: [
                      BarChartRodData(
                        toY: entry.value.disk,
                        color: colorDisk,
                        width: 16,
                        borderRadius: const BorderRadius.only(
                          topLeft: Radius.circular(4),
                          topRight: Radius.circular(4),
                        ),
                      ),
                    ],
                    showingTooltipIndicators:
                        entry.value.disk > 0 ? [0] : [],
                  );
                }).toList(),
                titlesData: _buildAxesTitles(
                    data.map((d) => d.device).toList(), 100),
                gridData: _buildGrid(100),
                borderData: FlBorderData(show: false),
                barTouchData: BarTouchData(
                  enabled: true,
                  handleBuiltInTouches: false,
                  touchTooltipData: BarTouchTooltipData(
                    getTooltipColor: (_) => Colors.transparent,
                    tooltipBorder: BorderSide.none,
                    tooltipPadding: EdgeInsets.zero,
                    tooltipMargin: 2,
                    fitInsideHorizontally: true,
                    fitInsideVertically: true,
                    getTooltipItem: (group, _, rod, __) {
                      final d = data[group.x];
                      // نفس React: لون رمادي
                      return BarTooltipItem(
                        d.disk.toStringAsFixed(1),
                        const TextStyle(
                          color: Color(0xFF9CA3AF),
                          fontSize: 10,
                          fontWeight: FontWeight.w600,
                          fontFamily: 'monospace',
                        ),
                      );
                    },
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  // ─── Shared helpers ───
  FlTitlesData _buildAxesTitles(List<String> deviceNames, double yMax) {
    return FlTitlesData(
      bottomTitles: AxisTitles(
        sideTitles: SideTitles(
          showTitles: true,
          reservedSize: 40,
          getTitlesWidget: (value, meta) {
            final idx = value.toInt();
            if (idx < 0 || idx >= deviceNames.length) {
              return const SizedBox();
            }
            final label = deviceNames[idx].replaceAll('-', '\n');
            return Padding(
              padding: const EdgeInsets.only(top: 4),
              child: Text(
                label,
                style: const TextStyle(
                  color: AppColors.textMuted,
                  fontSize: 8,
                ),
                textAlign: TextAlign.center,
              ),
            );
          },
        ),
      ),
      leftTitles: AxisTitles(
        sideTitles: SideTitles(
          showTitles: true,
          reservedSize: 35,
          interval: yMax / 4,
          getTitlesWidget: (value, meta) {
            final txt = value < 10
                ? value.toStringAsFixed(1)
                : value.toStringAsFixed(0);
            return Text(
              txt,
              style: const TextStyle(
                color: AppColors.textMuted,
                fontSize: 9,
              ),
            );
          },
        ),
      ),
      topTitles: const AxisTitles(
          sideTitles: SideTitles(showTitles: false)),
      rightTitles: const AxisTitles(
          sideTitles: SideTitles(showTitles: false)),
    );
  }

  FlGridData _buildGrid(double yMax) {
    return FlGridData(
      show: true,
      drawVerticalLine: false,
      horizontalInterval: yMax / 4,
      getDrawingHorizontalLine: (_) => const FlLine(
        color: AppColors.borderLight,
        strokeWidth: 0.5,
        dashArray: [3, 3],
      ),
    );
  }

  Widget _legendItem(Color color, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 10,
          height: 10,
          decoration: BoxDecoration(
            color: color,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
        const SizedBox(width: 4),
        Text(
          text,
          style: const TextStyle(
            color: AppColors.textSecondary,
            fontSize: 11,
          ),
        ),
      ],
    );
  }

  Widget _buildEmptyState() {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 60, horizontal: 20),
      decoration: BoxDecoration(
        color: AppColors.bgCard,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.borderLight),
      ),
      child: const Column(
        children: [
          Icon(Icons.bar_chart_outlined,
              color: AppColors.textMuted, size: 50),
          SizedBox(height: 12),
          Text(
            'En attente des métriques en direct...',
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 14,
            ),
          ),
          SizedBox(height: 6),
          Text(
            'Assurez-vous que le moteur IA est en cours d\'exécution',
            style: TextStyle(
              color: AppColors.textMuted,
              fontSize: 11,
            ),
          ),
        ],
      ),
    );
  }
}

// ─── Data classes ───
class _BarValue {
  final String device;
  final double value;
  _BarValue({required this.device, required this.value});
}

class _ThroughputData {
  final String device;
  final double inValue;
  final double outValue;
  _ThroughputData({
    required this.device,
    required this.inValue,
    required this.outValue,
  });
}

class _DiskData {
  final String device;
  final double disk;
  final double used;
  final double total;
  _DiskData({
    required this.device,
    required this.disk,
    required this.used,
    required this.total,
  });
}
