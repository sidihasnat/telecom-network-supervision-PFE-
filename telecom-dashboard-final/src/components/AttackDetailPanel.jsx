import React, { useState, useEffect } from 'react';
import {
  LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  ReferenceArea, Legend
} from 'recharts';
import { X, Clock, Shield, AlertTriangle, CheckCircle, Activity } from 'lucide-react';
import { getAttackImpact } from '../services/api';
import { MITRE_MAP, ATTACK_INFO } from '../data/mitre';

/**
 * AttackDetailPanel — side drawer showing the impact of a past attack.
 *
 * Opens when user clicks on a row in Security → History tab.
 * Shows:
 *   1. Header — attack type, device, time, duration
 *   2. Summary — confidence, status, MITRE, protection
 *   3. Impact Charts — 4-6 metrics (chosen based on attack type) showing
 *      before / during / after the attack, with shaded attack region
 *   4. Top Features — from the original prediction
 *   5. Timeline of Actions — from AuditLog
 *
 * Replaces the old "click row → open SidePanel on AI Result tab" behavior
 * which showed CURRENT state, not the attack's historical state.
 */

// ═══ Metric groups per attack type ══════════════════════════
// Chooses which charts to display based on attack signature.
// Each entry = list of { key, label, unit, source }
// source: 'metric' (MetricData), 'tcp' (TcpStats), 'security' (SecurityMetric),
//         'iface' (sum across interfaces)
const METRICS_BY_ATTACK = {
  synflood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'halfOpenConnections', label: 'Half-Open Connections', unit: '', source: 'security' },
    { key: 'passiveOpensRate', label: 'SYN Rate', unit: '/s', source: 'tcp' },
    { key: 'inOutSegRatio', label: 'In/Out Segment Ratio', unit: '', source: 'tcp' },
  ],
  router_synflood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'halfOpenConnections', label: 'Half-Open Connections', unit: '', source: 'security' },
    { key: 'passiveOpensRate', label: 'SYN Rate', unit: '/s', source: 'tcp' },
    { key: 'forwardedPackets', label: 'Forwarded Packets', unit: '', source: 'tcp' },
  ],
  dos: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'packetsPerSecRecv', label: 'Packets/s Received', unit: '/s', source: 'iface' },
    { key: 'icmpInRate', label: 'ICMP Rate', unit: '/s', source: 'tcp' },
  ],
  ddos: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'packetsPerSecRecv', label: 'Packets/s Received', unit: '/s', source: 'iface' },
    { key: 'uniqueSourceIPs', label: 'Unique Source IPs', unit: '', source: 'security' },
  ],
  router_dos: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'icmpInRate', label: 'ICMP Rate', unit: '/s', source: 'tcp' },
    { key: 'forwardedPackets', label: 'Forwarded Packets', unit: '', source: 'tcp' },
  ],
  udp_flood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'udpInRate', label: 'UDP Received Rate', unit: '/s', source: 'tcp' },
    { key: 'udpNoPortRate', label: 'UDP No-Port Rate', unit: '/s', source: 'tcp' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
  ],
  dns_flood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'udpInRate', label: 'UDP Received Rate', unit: '/s', source: 'tcp' },
    { key: 'udpOutRate', label: 'UDP Sent Rate', unit: '/s', source: 'tcp' },
    { key: 'udpInOutRatio', label: 'UDP In/Out Ratio', unit: '', source: 'tcp' },
  ],
  http_flood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'connections', label: 'Connections', unit: '', source: 'metric' },
    { key: 'halfOpenConnections', label: 'Half-Open Connections', unit: '', source: 'security' },
    { key: 'passiveOpensRate', label: 'SYN Rate', unit: '/s', source: 'tcp' },
  ],
  slowloris: [
    { key: 'connections', label: 'Connections', unit: '', source: 'metric' },
    { key: 'avgConnectionDuration', label: 'Avg Connection Duration', unit: 's', source: 'security' },
    { key: 'longConnectionRatio', label: 'Long Connection Ratio', unit: '', source: 'security' },
    { key: 'halfOpenConnections', label: 'Half-Open Connections', unit: '', source: 'security' },
  ],
  portscan: [
    { key: 'uniqueDestinationPorts', label: 'Unique Dest. Ports', unit: '', source: 'security' },
    { key: 'sensitivePortsHit', label: 'Sensitive Ports Hit', unit: '', source: 'security' },
    { key: 'outRstsRate', label: 'Outgoing RST Rate', unit: '/s', source: 'tcp' },
    { key: 'udpNoPortRate', label: 'UDP No-Port Rate', unit: '/s', source: 'tcp' },
  ],
  rst_flood: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'outRstsRate', label: 'Outgoing RST Rate', unit: '/s', source: 'tcp' },
    { key: 'estabResetsRate', label: 'Connection Reset Rate', unit: '/s', source: 'tcp' },
    { key: 'rstPerConnection', label: 'RST per Connection', unit: '', source: 'tcp' },
  ],
  ping_of_death: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'icmpInRate', label: 'ICMP Rate', unit: '/s', source: 'tcp' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'packetsPerSecRecv', label: 'Packets/s Received', unit: '/s', source: 'iface' },
  ],
  bruteforce: [
    { key: 'failedLoginsRate', label: 'Failed Logins Rate', unit: '/s', source: 'security' },
    { key: 'failedLogins', label: 'Failed Logins (total)', unit: '', source: 'security' },
    { key: 'sshConnectionAttempts', label: 'SSH Connection Attempts', unit: '', source: 'security' },
    { key: 'uniqueSourceIPs', label: 'Unique Source IPs', unit: '', source: 'security' },
  ],
  fault: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'latency', label: 'Latency', unit: 'ms', source: 'iface' },
    { key: 'packetLoss', label: 'Packet Loss', unit: '%', source: 'iface' },
  ],
  // Default for unrecognized types
  default: [
    { key: 'cpuUsage', label: 'CPU Usage', unit: '%', source: 'metric' },
    { key: 'throughputIn', label: 'Throughput In', unit: 'Mbps', source: 'iface' },
    { key: 'connections', label: 'Connections', unit: '', source: 'metric' },
    { key: 'passiveOpensRate', label: 'SYN Rate', unit: '/s', source: 'tcp' },
  ],
};

function formatAttackName(type) {
  const names = {
    synflood: 'SYN Flood', dos: 'DoS', ddos: 'DDoS',
    udp_flood: 'UDP Flood', http_flood: 'HTTP Flood', dns_flood: 'DNS Flood',
    portscan: 'Port Scan', ping_of_death: 'Ping of Death', rst_flood: 'RST Flood',
    router_dos: 'Router DoS', router_synflood: 'Router SYN Flood',
    slowloris: 'Slowloris', bruteforce: 'Brute Force', fault: 'Fault',
  };
  return names[type] || type?.toUpperCase() || 'Unknown';
}

function formatDuration(seconds) {
  if (!seconds && seconds !== 0) return '—';
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

function formatDateTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleDateString('en-CA') + ' ' + d.toLocaleTimeString();
}

function formatTime(iso) {
  if (!iso) return '—';
  return new Date(iso).toLocaleTimeString();
}

// Extract value from a MetricData row based on source
function extractValue(metric, key, source) {
  if (source === 'metric') {
    return metric[key];
  }
  if (source === 'tcp') {
    return metric.tcpStats ? metric.tcpStats[key] : null;
  }
  if (source === 'security') {
    return metric.securityMetric ? metric.securityMetric[key] : null;
  }
  if (source === 'iface') {
    // sum or max across interfaces
    const ifaces = metric.interfaces || [];
    if (ifaces.length === 0) return null;
    if (key === 'latency') {
      return Math.max(0, ...ifaces.map(i => i[key] || 0));
    }
    // default: sum
    return ifaces.reduce((sum, i) => sum + (i[key] || 0), 0);
  }
  return null;
}

// 🆕 Given a feature name returned by the AI model, figure out WHERE to find it
// in a MetricData row. The AI uses flat names like "cpuUsage" or "halfOpenConnections"
// but the JSON is nested (tcpStats / securityMetric / interfaces). We probe a sample
// metric to see which nested object actually has this field, then return
// { source, key } so the chart-builder can extract values.
//
// Known top-level fields on MetricData directly (not nested).
const METRIC_LEVEL_FIELDS = new Set([
  'cpuUsage', 'memoryUsage', 'diskUsage', 'diskTotalGb', 'diskUsedGb',
  'diskFreeGb', 'connections',
]);

function locateFeature(sampleMetric, featureName) {
  if (!sampleMetric || !featureName) return null;
  if (METRIC_LEVEL_FIELDS.has(featureName)) {
    return { source: 'metric', key: featureName };
  }
  if (sampleMetric.tcpStats && featureName in sampleMetric.tcpStats) {
    return { source: 'tcp', key: featureName };
  }
  if (sampleMetric.securityMetric && featureName in sampleMetric.securityMetric) {
    return { source: 'security', key: featureName };
  }
  if (Array.isArray(sampleMetric.interfaces) && sampleMetric.interfaces.length > 0) {
    if (featureName in sampleMetric.interfaces[0]) {
      return { source: 'iface', key: featureName };
    }
  }
  return null;
}

function AttackDetailPanel({ session, onClose }) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);
    getAttackImpact(session.id)
      .then((res) => {
        setData(res);
      })
      .catch((err) => {
        setError(err.message || 'Failed to load');
      })
      .finally(() => setLoading(false));
  }, [session.id]);

  const mitre = MITRE_MAP[session.attackType];
  const info = ATTACK_INFO[session.attackType];
  const severity = info?.severity || 'UNKNOWN';

  // Parse top features from session.topFeatures JSON
  let topFeatures = [];
  try {
    if (session.topFeatures) {
      topFeatures = JSON.parse(session.topFeatures);
    }
  } catch {}

  // Build chart data for each metric
  const metricsConfig = METRICS_BY_ATTACK[session.attackType] || METRICS_BY_ATTACK.default;

  const chartData = metricsConfig.map((config) => {
    if (!data) return { ...config, points: [], attackStartIdx: null, attackEndIdx: null };

    const allMetrics = [
      ...(data.metricsBefore || []),
      ...(data.metricsDuring || []),
      ...(data.metricsAfter || []),
    ].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp));

    const points = allMetrics.map((m) => ({
      time: new Date(m.timestamp).getTime(),
      timeLabel: formatTime(m.timestamp),
      value: extractValue(m, config.key, config.source),
    })).filter(p => p.value != null);

    const attackStart = data.window?.attackStart ? new Date(data.window.attackStart).getTime() : null;
    const attackEnd = data.window?.attackEnd ? new Date(data.window.attackEnd).getTime() : null;

    return { ...config, points, attackStart, attackEnd };
  });

  // 🆕 Build extra charts for the features the AI model flagged as most important.
  //
  // topFeatures is already parsed above. We de-duplicate against the manually-curated
  // `chartData` (so we don't draw the same CPU chart twice) and then build a chart
  // per remaining feature using the sample metric to locate where it lives.
  const manualKeys = new Set(metricsConfig.map(c => c.key));
  const allMetricsFlat = data ? [
    ...(data.metricsBefore || []),
    ...(data.metricsDuring || []),
    ...(data.metricsAfter || []),
  ].sort((a, b) => new Date(a.timestamp) - new Date(b.timestamp)) : [];
  const sampleMetric = allMetricsFlat[0] || null;

  const aiTopFeaturesCharts = topFeatures
    .filter(f => f && f.name && !manualKeys.has(f.name))
    .map(f => {
      const loc = locateFeature(sampleMetric, f.name);
      if (!loc) return null;

      const points = allMetricsFlat.map((m) => ({
        time: new Date(m.timestamp).getTime(),
        timeLabel: formatTime(m.timestamp),
        value: extractValue(m, loc.key, loc.source),
      })).filter(p => p.value != null);

      return {
        key: f.name,
        label: f.name,
        unit: '',
        source: loc.source,
        points,
        attackStart: data?.window?.attackStart ? new Date(data.window.attackStart).getTime() : null,
        attackEnd: data?.window?.attackEnd ? new Date(data.window.attackEnd).getTime() : null,
        // Importance value from the model (e.g. 0.24). Shown in the chart header.
        importance: typeof f.value === 'number' ? f.value : null,
      };
    })
    .filter(Boolean);

  return (
    <div className="fixed inset-y-0 right-0 w-[600px] max-w-full bg-bg-card
                    border-l border-gray-800 shadow-2xl z-50 overflow-hidden flex flex-col">
      {/* Header */}
      <div className={`px-5 py-4 border-b border-gray-800 ${
        session.status === 'MITIGATED' ? 'bg-success/5' : 'bg-danger/5'
      }`}>
        <div className="flex items-start justify-between gap-3">
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 mb-1">
              <span className="text-lg">🚨</span>
              <h2 className="text-base font-semibold text-gray-100 truncate">
                {formatAttackName(session.attackType)}
              </h2>
              <StatusBadge status={session.status} />
            </div>
            <p className="text-sm text-gray-400 font-mono">
              {session.deviceName}::{session.interfaceName}
            </p>
            <p className="text-xs text-gray-500 mt-1">
              {formatDateTime(session.startedAt)} → {formatDateTime(session.endedAt)}
              <span className="mx-2">·</span>
              <span className="text-gray-400">{formatDuration(session.durationSeconds)}</span>
            </p>
          </div>
          <button onClick={onClose}
            className="p-1.5 text-gray-400 hover:text-gray-200 hover:bg-gray-800 rounded transition-colors">
            <X size={18} />
          </button>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto p-5 space-y-5">
        {loading && (
          <div className="text-center py-12 text-gray-500 text-sm">Loading attack impact...</div>
        )}

        {error && (
          <div className="bg-danger/10 border border-danger/30 rounded-lg p-3 text-sm text-danger">
            Failed to load: {error}
          </div>
        )}

        {data && (
          <>
            {/* ─── Attack Summary ─── */}
            <SectionCard title="Attack Summary">
              <InfoRow label="Type" value={formatAttackName(session.attackType)} valueClass="text-danger font-mono" />
              <InfoRow label="Confidence"
                value={
                  <span className="font-mono">
                    avg {session.avgConfidence ? `${(session.avgConfidence * 100).toFixed(0)}%` : '—'}
                    {' · '}
                    max {session.maxConfidence ? `${(session.maxConfidence * 100).toFixed(0)}%` : '—'}
                  </span>
                }
              />
              <InfoRow label="Detections" value={`${session.predictionCount || 0} predictions`} />
              {/* Mitigation summary — pulls from protectionActions array (new structure)
                  with backward-compat fallback to legacy session.protectionAction field. */}
              {data?.protectionActions && data.protectionActions.length > 0 ? (
                <InfoRow label="Mitigation"
                  value={
                    <span className="text-success">
                      🛡️ {data.protectionActions[data.protectionActions.length - 1].actionName}
                      {data.protectionActions.length > 1 && (
                        <span className="text-gray-500 ml-1">
                          (+{data.protectionActions.length - 1} more)
                        </span>
                      )}
                    </span>
                  }
                />
              ) : session.protectionAction ? (
                <InfoRow label="Protection"
                  value={<span className="text-success">🛡️ {session.protectionAction}</span>}
                />
              ) : null}
              {mitre && (
                <InfoRow label="MITRE ATT&CK"
                  value={<span className="font-mono text-accent">{mitre.id} — {mitre.tactic}</span>}
                />
              )}
              {info && (
                <InfoRow label="Severity"
                  value={
                    <span className={severity === 'CRITICAL' ? 'text-danger' : 'text-warning'}>
                      {severity}
                    </span>
                  }
                />
              )}
              {info?.impact && (
                <div className="pt-2 border-t border-gray-800 mt-2">
                  <p className="text-xs text-gray-500 mb-1">Impact</p>
                  <p className="text-xs text-gray-300">{info.impact}</p>
                </div>
              )}
              {info?.recommendation && (
                <div className="pt-2 border-t border-gray-800">
                  <p className="text-xs text-gray-500 mb-1">Recommendation</p>
                  <p className="text-xs text-accent">{info.recommendation}</p>
                </div>
              )}
            </SectionCard>

            {/* ─── Impact Charts ─── */}
            <SectionCard title="Network Impact (before · during · after)">
              {chartData.every(c => c.points.length === 0) ? (
                <div className="py-6 text-center text-xs text-gray-500">
                  No metrics available for this time window.
                  <br />
                  <span className="text-gray-600">
                    (Metrics may not have been recorded during this period)
                  </span>
                </div>
              ) : (
                <div className="space-y-4">
                  {chartData.map((chart) => (
                    <ImpactChart key={chart.key} config={chart} />
                  ))}
                </div>
              )}
            </SectionCard>

            {/* ─── AI Top Features (charts + importances) ─── */}
            {topFeatures.length > 0 && (
              <SectionCard title="AI Top Features">
                <p className="text-[10px] text-gray-500 mb-3">
                  Features the AI model ranked as most influential in this detection.
                  Charts below show how each of these evolved before / during / after the attack.
                </p>

                {/* Compact list with importance values */}
                <div className="space-y-1 mb-4">
                  {topFeatures.slice(0, 6).map((f, i) => (
                    <div key={i} className="flex items-baseline justify-between py-1.5 px-2
                                            bg-bg-primary rounded text-xs">
                      <span className="font-mono text-accent">{f.name}</span>
                      <div className="text-right">
                        <span className="font-mono text-gray-200">
                          {typeof f.value === 'number' ? f.value.toFixed(3) : f.value}
                        </span>
                        {f.normal != null && (
                          <span className="text-gray-600 ml-2">
                            (norm: {typeof f.normal === 'number' ? f.normal.toFixed(1) : f.normal})
                          </span>
                        )}
                      </div>
                    </div>
                  ))}
                </div>

                {/* One chart per topFeature (that we could resolve). These complement —
                    not replace — the manually-curated Network Impact charts above. */}
                {aiTopFeaturesCharts.length > 0 && (
                  <div className="space-y-4 border-t border-gray-800 pt-4">
                    <p className="text-[10px] text-gray-500">
                      Timeline of each top feature during the attack window:
                    </p>
                    {aiTopFeaturesCharts.map((chart) => (
                      <ImpactChart key={chart.key} config={chart} />
                    ))}
                  </div>
                )}
                {aiTopFeaturesCharts.length === 0 && topFeatures.length > 0 && data && (
                  <p className="text-[10px] text-gray-600 text-center pt-2 border-t border-gray-800">
                    No time-series data available to chart these features.
                  </p>
                )}
              </SectionCard>
            )}

            {/* ─── Timeline of Actions ─── */}
            <SectionCard title="Timeline of Actions">
              <Timeline session={session} data={data} />
            </SectionCard>
          </>
        )}
      </div>
    </div>
  );
}

/* ═══ Sub-components ═══ */

function ImpactChart({ config }) {
  if (config.points.length < 2) {
    return (
      <div className="bg-bg-primary rounded-lg p-3">
        <div className="flex justify-between items-baseline mb-1">
          <span className="text-xs text-gray-400">{config.label}</span>
          <span className="text-xs text-gray-600">{config.unit}</span>
        </div>
        <p className="text-xs text-gray-600 py-3 text-center">Not enough data</p>
      </div>
    );
  }

  const maxVal = Math.max(...config.points.map(p => p.value));

  return (
    <div className="bg-bg-primary rounded-lg p-3">
      <div className="flex justify-between items-baseline mb-2">
        <span className="text-xs text-gray-300 font-medium">{config.label}</span>
        <span className="text-xs text-gray-500 font-mono">
          {config.importance != null && (
            <span className="text-accent mr-2">★ {config.importance.toFixed(3)}</span>
          )}
          peak: {typeof maxVal === 'number' ? maxVal.toFixed(1) : maxVal} {config.unit}
        </span>
      </div>
      <ResponsiveContainer width="100%" height={120}>
        <LineChart data={config.points} margin={{ top: 5, right: 5, left: 0, bottom: 0 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" vertical={false} />
          <XAxis
            dataKey="time"
            type="number"
            domain={['dataMin', 'dataMax']}
            tickFormatter={(t) => new Date(t).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
            tick={{ fill: '#6b7280', fontSize: 9 }}
            axisLine={{ stroke: '#374151' }}
            tickLine={false}
          />
          <YAxis
            tick={{ fill: '#6b7280', fontSize: 9 }}
            axisLine={{ stroke: '#374151' }}
            tickLine={false}
            width={35}
          />
          <Tooltip
            contentStyle={{
              backgroundColor: '#141923',
              border: '1px solid #374151',
              borderRadius: 6,
              fontSize: 11,
            }}
            labelFormatter={(t) => new Date(t).toLocaleString()}
            formatter={(v) => [
              `${typeof v === 'number' ? v.toFixed(2) : v} ${config.unit}`,
              config.label,
            ]}
          />
          {/* Shaded attack region */}
          {config.attackStart && config.attackEnd && (
            <ReferenceArea
              x1={config.attackStart}
              x2={config.attackEnd}
              strokeOpacity={0}
              fill="#ef4444"
              fillOpacity={0.1}
            />
          )}
          <Line
            type="monotone"
            dataKey="value"
            stroke="#06b6d4"
            strokeWidth={2}
            dot={false}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function Timeline({ session, data }) {
  // Build timeline: start + audit events + mitigated + ended
  const events = [];

  // Attack detected
  events.push({
    time: session.startedAt,
    icon: <AlertTriangle size={14} className="text-danger" />,
    label: 'Attack detected by AI',
    detail: `${formatAttackName(session.attackType)} · ${session.avgConfidence ? `${(session.avgConfidence * 100).toFixed(0)}%` : ''}`,
    color: 'danger',
  });

  // Audit log events during window (sorted ascending)
  const auditLogs = (data.auditLogs || []).slice().reverse();
  for (const log of auditLogs) {
    let icon = <Activity size={14} className="text-gray-400" />;
    let color = 'gray';
    if (log.type === 'PROTECTION') {
      icon = <Shield size={14} className="text-success" />;
      color = 'success';
    } else if (log.type === 'ACKNOWLEDGE') {
      icon = <CheckCircle size={14} className="text-accent" />;
      color = 'accent';
    }
    events.push({
      time: log.timestamp,
      icon,
      label: log.description,
      detail: log.username ? `by ${log.username}` : '',
      color,
    });
  }

  // Mitigation timestamp (if MITIGATED)
  if (session.mitigatedAt && !events.some(e => e.time === session.mitigatedAt)) {
    // Pick the last executed playbook name from protectionActions, fall back
    // to legacy session.protectionAction, then to a generic label.
    const lastAction = data?.protectionActions?.length
      ? data.protectionActions[data.protectionActions.length - 1].actionName
      : null;
    events.push({
      time: session.mitigatedAt,
      icon: <Shield size={14} className="text-success" />,
      label: `Protection applied: ${lastAction || session.protectionAction || 'mitigation'}`,
      detail: '',
      color: 'success',
    });
  }

  // Ended
  if (session.endedAt) {
    events.push({
      time: session.endedAt,
      icon: <CheckCircle size={14} className="text-gray-400" />,
      label: session.status === 'MITIGATED' ? 'Attack stopped (mitigated)' : 'Attack ended',
      detail: '',
      color: 'gray',
    });
  }

  // Sort by time
  events.sort((a, b) => new Date(a.time) - new Date(b.time));

  return (
    <div className="relative">
      {/* Vertical line */}
      <div className="absolute left-[7px] top-2 bottom-2 w-px bg-gray-700" />
      <div className="space-y-3">
        {events.map((e, i) => (
          <div key={i} className="flex gap-3 items-start relative">
            <div className="w-[14px] flex-shrink-0 mt-0.5 relative z-10 bg-bg-card rounded-full">
              {e.icon}
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex items-baseline justify-between gap-2">
                <p className="text-xs text-gray-200 flex-1">{e.label}</p>
                <span className="text-xs text-gray-600 font-mono whitespace-nowrap">
                  {formatTime(e.time)}
                </span>
              </div>
              {e.detail && (
                <p className="text-[10px] text-gray-500 mt-0.5">{e.detail}</p>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function SectionCard({ title, children }) {
  return (
    <div className="bg-bg-primary rounded-lg border border-gray-800 overflow-hidden">
      <div className="px-4 py-2 border-b border-gray-800 bg-bg-card/50">
        <h3 className="text-xs font-semibold text-gray-300 uppercase tracking-wider">{title}</h3>
      </div>
      <div className="p-4">{children}</div>
    </div>
  );
}

function InfoRow({ label, value, valueClass = 'text-gray-200' }) {
  return (
    <div className="flex items-baseline justify-between py-1 text-sm">
      <span className="text-xs text-gray-500">{label}</span>
      <span className={`text-sm ${valueClass}`}>{value}</span>
    </div>
  );
}

function StatusBadge({ status }) {
  const styles = {
    ACTIVE: 'bg-danger/20 text-danger',
    MITIGATED: 'bg-success/20 text-success',
    ENDED: 'bg-gray-700 text-gray-300',
  };
  return (
    <span className={`text-[10px] px-2 py-0.5 rounded font-medium ${styles[status] || styles.ENDED}`}>
      {status}
    </span>
  );
}

export default AttackDetailPanel;