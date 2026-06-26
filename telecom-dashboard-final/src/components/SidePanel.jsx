import React, { useState, useEffect, useRef } from 'react';
import { X, Power, RotateCw, PlayCircle } from 'lucide-react';import { LineChart, Line, ResponsiveContainer, Tooltip, YAxis, XAxis, CartesianGrid } from 'recharts';
import { getAvailableMetrics , getDeviceMetricHistory , getInterfaceTimeline, getDeviceArpTable, getDeviceMetrics, powerOffDevice, powerOnDevice, restartDevice, getDeviceStatus } from '../services/api';
import { MITRE_MAP, ATTACK_INFO } from '../data/mitre';

// UX-7: Removed "Attack Path" tab — we don't know attack origin
// Tabs depend on device.type: routers get an extra "Interfaces" tab
const BASE_TABS   = ['Overview', 'AI Result', 'KPI', 'History', 'Counters', 'Logs', 'Properties'];
const ROUTER_TABS = ['Overview', 'AI Result', 'KPI', 'Interfaces', 'History', 'Counters', 'Logs', 'Properties'];

// ── KPI definitions per device type ─────────────────────────
const KPI_MAP = {
  'web-server': ['halfOpenConnections', 'passiveOpensRate', 'icmpInRate', 'bytesPerPacketIn', 'avgConnectionDuration'],
  'ftp-server': ['failedLogins', 'failedLoginsRate', 'uniqueSourceIPs', 'bytesPerPacketIn', 'topAttackerRepeat'],
  'db-server': ['sensitivePortsHit', 'uniqueDestinationPorts', 'halfOpenConnections', 'outRstsRate', 'throughputIn'],
  'dns-server': ['udpInRate', 'udpOutRate', 'udpInOutRatio', 'udpNoPortRate', 'throughputIn'],
  'edge-router': ['forwardedPackets', 'routingTableSize', 'inDiscards', 'noRoutePackets', 'icmpInRate'],
  'core-router': ['forwardedPackets', 'routingTableSize', 'inDiscards', 'noRoutePackets', 'icmpInRate'],
};

function SidePanel({ device, defaultTab = 0, onClose, latestMetrics, latestPredictions, isAdmin, deviceLastSeen }) {
  const [activeTab, setActiveTab] = useState(defaultTab);
  const [historyMetrics, setHistoryMetrics] = useState([]);

  // BUG-1 FIX: Use ref to accumulate history, update when new metrics arrive
  const historyRef = useRef([]);

  // Fetch initial history on mount
  useEffect(() => {
    getDeviceMetrics(device.name)
      .then(data => {
        historyRef.current = data;
        setHistoryMetrics(data);
      })
      .catch(() => {
        historyRef.current = [];
        setHistoryMetrics([]);
      });
  }, [device.name]);

  // BUG-1 FIX: Append new metric to history when latestMetrics updates
  useEffect(() => {
    const current = latestMetrics?.[device.name];
    if (!current) return;

    const lastInHistory = historyRef.current[historyRef.current.length - 1];
    if (lastInHistory && lastInHistory.id === current.id) return;

    const updated = [...historyRef.current, current].slice(-20);
    historyRef.current = updated;
    setHistoryMetrics(updated);
  }, [latestMetrics, device.name]);

  // ── Detect if device is truly offline (no metrics for 45s) ──
  // Uses polling because deviceLastSeen is a ref (no re-render on change)
  const [isDeviceOffline, setIsDeviceOffline] = useState(false);

  useEffect(() => {
    const checkOffline = () => {
      const lastSeen = deviceLastSeen?.current?.[device.name];
      if (!lastSeen) {
        setIsDeviceOffline(false);
        return;
      }
      if (lastSeen === 1) {
        setIsDeviceOffline(true);
        return;
      }
      setIsDeviceOffline((Date.now() - lastSeen) > 45000);
    };

    checkOffline(); // check immediately
    const interval = setInterval(checkOffline, 5000); // re-check every 5s
    return () => clearInterval(interval);
  }, [device.name, deviceLastSeen]);

  // ── UX-1: Power toggle state from API ──────────────────────
  const [powerLoading, setPowerLoading] = useState(false);
  const [deviceRunning, setDeviceRunning] = useState(null); // null = loading
 
  // Fetch actual container state from API on mount
  useEffect(() => {
      const fetchStatus = () => {
        getDeviceStatus(device.name)
          .then(s => { if (s) setDeviceRunning(s.running); })
          .catch(() => setDeviceRunning(true));
      };
      fetchStatus();
      const interval = setInterval(fetchStatus, 5000);
      return () => clearInterval(interval);
    }, [device.name]);
    
  const handlePowerOff = async () => {
    if (!window.confirm(`Power off ${device.name}?`)) return;
    setPowerLoading(true);
    try {
      const res = await powerOffDevice(device.name);
      if (res?.success) setDeviceRunning(false);
    } catch (err) {
      console.error('❌ Power off error:', err);
    }
    setPowerLoading(false);
  };
 
  const handlePowerOn = async () => {
    setPowerLoading(true);
    try {
      const res = await powerOnDevice(device.name);
      if (res?.success) {
        setDeviceRunning(true);
        // Wait 10s for init script to complete
        setTimeout(() => {
          getDeviceStatus(device.name).then(s => s && setDeviceRunning(s.running));
        }, 10000);
      }
    } catch (err) {
      console.error('❌ Power on error:', err);
    }
    setPowerLoading(false);
  };
 
  const handleRestart = async () => {
    if (!window.confirm(`Restart ${device.name}?`)) return;
    setPowerLoading(true);
    try {
      const res = await restartDevice(device.name);
      if (res?.success) {
        // Container restarts, brief offline period
        setTimeout(() => {
          getDeviceStatus(device.name).then(s => s && setDeviceRunning(s.running));
        }, 8000);
      }
    } catch (err) {
      console.error('❌ Restart error:', err);
    }
    setPowerLoading(false);
  };

  if (!device) return null;

  return (
    <>
      {/* UX: animate-fadeIn on backdrop. REVERT: remove 'animate-fadeIn' class */}
      <div className="fixed inset-0 bg-black/40 z-40 animate-fadeIn" onClick={onClose} />
      {/* UX: animate-slideIn on panel. REVERT: remove 'animate-slideIn' class */}
      <div className="fixed right-0 top-0 h-full w-[520px] bg-bg-card border-l border-gray-700
                      shadow-2xl z-50 flex flex-col animate-slideIn">
        {/* Header — UX-1: Power toggle next to close button */}
        <div className="flex items-center justify-between p-4 border-b border-gray-700">
          <div>
            <h3 className="text-lg font-semibold text-gray-100">{device.name}</h3>
            <p className="text-xs text-gray-500">{device.type}</p>
          </div>
          <div className="flex items-center gap-2">
            {/* Power Controls — admin only — 3 buttons */}
  {isAdmin && deviceRunning !== null && (
    <>
      {deviceRunning ? (
        <>
          {/* Restart */}
          <button
            onClick={handleRestart}
            disabled={powerLoading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                       bg-warning/10 text-warning hover:bg-warning/20 transition-colors disabled:opacity-50"
            title="Restart this device"
          >
            <RotateCw size={14} />
            Restart
          </button>
 
          {/* Power Off */}
          <button
            onClick={handlePowerOff}
            disabled={powerLoading}
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                       bg-danger/10 text-danger hover:bg-danger/20 transition-colors disabled:opacity-50"
            title="Power off this device"
          >
            <Power size={14} />
            {powerLoading ? '...' : 'Power Off'}
          </button>
        </>
      ) : (
        /* Power On (only when off) */
        <button
          onClick={handlePowerOn}
          disabled={powerLoading}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium
                     bg-success/10 text-success hover:bg-success/20 transition-colors disabled:opacity-50"
          title="Power on this device"
        >
          <PlayCircle size={14} />
          {powerLoading ? '...' : 'Power On'}
        </button>
      )}
    </>
  )}
            <button onClick={onClose} className="p-1 text-gray-400 hover:text-gray-200 transition-colors">
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Tabs — routers get an extra "Interfaces" tab */}
        <div className="flex border-b border-gray-700 overflow-x-auto">
          {(device.type === 'router' ? ROUTER_TABS : BASE_TABS).map((tab, i) => (
            <button key={tab} onClick={() => setActiveTab(i)}
              className={`px-3 py-2 text-xs whitespace-nowrap transition-colors ${
                activeTab === i ? 'text-accent border-b-2 border-accent' : 'text-gray-500 hover:text-gray-300'
              }`}>
              {tab}
            </button>
          ))}
        </div>

        {/* Content — dispatch by tab NAME (not index) since router/server have different sets */}
        <div className="flex-1 overflow-y-auto p-4">
          {(() => {
            const tabs = device.type === 'router' ? ROUTER_TABS : BASE_TABS;
            const name = tabs[activeTab];
            switch (name) {
              case 'Overview':   return <TabOverview device={device} metrics={latestMetrics} history={historyMetrics} isOffline={isDeviceOffline} />;
              case 'AI Result':  return <TabAiResult device={device} predictions={latestPredictions} />;
              case 'KPI':        return <TabKpi device={device} history={historyMetrics} isOffline={isDeviceOffline} />;
              case 'Interfaces': return <TabInterfaces device={device} metrics={latestMetrics} />;
              case 'History':    return <TabHistory device={device} />;
              case 'Counters':   return <TabCounters device={device} metrics={latestMetrics} isOffline={isDeviceOffline} />;
              case 'Logs':       return <TabLogs device={device} />;
              case 'Properties': return <TabProperties device={device} metrics={latestMetrics} />;
              default:           return null;
            }
          })()}
        </div>
      </div>
    </>
  );
}

/* ═══ Tab 1 — Overview ═══ */
function TabOverview({ device, metrics, history, isOffline }) {
  // If device offline (no metrics for 45s) → show 0
  const current = isOffline ? null : metrics?.[device.name];
  const cpu = current?.cpuUsage ?? 0;
  const mem = current?.memoryUsage ?? 0;
  const conn = current?.connections ?? 0;
  const diskPct = current?.diskUsage ?? 0;
  const diskTotal = current?.diskTotalGb ?? 0;
  const diskUsed = current?.diskUsedGb ?? 0;
  const diskFree = current?.diskFreeGb ?? 0;

  // Build chart data from history — if offline, append 0 points
  let cpuHistory = history.map((m, i) => ({ i, v: m.cpuUsage || 0 }));
  let memHistory = history.map((m, i) => ({ i, v: m.memoryUsage || 0 }));

  if (isOffline && cpuHistory.length > 0) {
    // Add zero points so charts visually drop to 0
    const lastIdx = cpuHistory[cpuHistory.length - 1].i + 1;
    cpuHistory = [...cpuHistory, { i: lastIdx, v: 0 }];
    memHistory = [...memHistory, { i: lastIdx, v: 0 }];
  }

  // Color disk usage based on threshold
  const diskColor = diskPct >= 90 ? '#ef4444' : diskPct >= 80 ? '#f59e0b' : '#8b5cf6';

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-3 gap-3">
        <MetricBox label="CPU" value={`${cpu.toFixed(1)}%`} />
        <MetricBox label="Memory" value={`${mem.toFixed(1)}%`} />
        <MetricBox label="Connections" value={conn} />
      </div>

      {cpuHistory.length > 2 && (
        <>
          <MiniChart data={cpuHistory} label="CPU %" color="#10b981" />
          <MiniChart data={memHistory} label="Memory %" color="#3b82f6" />
        </>
      )}

      {/* Disk Usage card with detailed breakdown */}
      {diskTotal > 0 && (
        <div className="bg-bg-primary rounded-lg p-3">
          <div className="flex items-center justify-between mb-2">
            <span className="text-xs text-gray-400">Disk Usage</span>
            <span className="text-sm font-mono" style={{ color: diskColor }}>
              {diskPct.toFixed(1)}%
            </span>
          </div>
          {/* Progress bar */}
          <div className="h-2 bg-bg-card rounded-full overflow-hidden mb-2">
            <div
              className="h-full transition-all duration-300"
              style={{
                width: `${Math.min(100, diskPct)}%`,
                backgroundColor: diskColor,
              }}
            />
          </div>
          <div className="grid grid-cols-3 gap-1 text-xs">
            <div>
              <span className="text-gray-500 block">Used</span>
              <span className="text-gray-200 font-mono">{diskUsed.toFixed(1)} GB</span>
            </div>
            <div>
              <span className="text-gray-500 block">Free</span>
              <span className="text-gray-200 font-mono">{diskFree.toFixed(1)} GB</span>
            </div>
            <div>
              <span className="text-gray-500 block">Total</span>
              <span className="text-gray-200 font-mono">{diskTotal.toFixed(1)} GB</span>
            </div>
          </div>
        </div>
      )}

      {current?.interfaces?.map(iface => (
        <div key={iface.interfaceName} className="bg-bg-primary rounded-lg p-3">
          <p className="text-xs text-accent font-mono mb-2">{iface.interfaceName}</p>
          <div className="grid grid-cols-2 gap-2 text-xs">
            <span className="text-gray-500">Throughput In</span>
            <span className="text-gray-200 font-mono">{(iface.throughputIn || 0).toFixed(2)} Mbps</span>
            <span className="text-gray-500">Throughput Out</span>
            <span className="text-gray-200 font-mono">{(iface.throughputOut || 0).toFixed(2)} Mbps</span>
            <span className="text-gray-500">Latency</span>
            <span className="text-gray-200 font-mono">{(iface.latency || 0).toFixed(1)} ms</span>
            <span className="text-gray-500">Packet Loss</span>
            <span className="text-gray-200 font-mono">{iface.packetLoss || 0}%</span>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ═══ Tab 2 — AI Result ═══ */
function TabAiResult({ device, predictions }) {
  const preds = predictions || {};
  const devicePreds = Object.entries(preds)
    .filter(([k]) => k.startsWith(device.name + '::'))
    .map(([k, v]) => ({ iface: k.split('::')[1], ...v }));

  if (devicePreds.length === 0) {
    return <p className="text-gray-500 text-sm">No predictions for this device</p>;
  }

  return (
    <div className="space-y-4">
      {devicePreds.map(pred => {
        const isAttack = pred.predictedAttack && pred.predictedAttack !== 'normal';
        const mitre = MITRE_MAP[pred.predictedAttack];
        const info = ATTACK_INFO[pred.predictedAttack];
        let topFeatures = [];
        try { topFeatures = JSON.parse(pred.topFeatures || '[]'); } catch {}

        return (
          <div key={pred.iface} className={`rounded-lg p-4 ${isAttack ? 'bg-danger/5 border border-danger/20' : 'bg-bg-primary'}`}>
            <div className="flex items-center justify-between mb-3">
              <span className="font-mono text-accent text-sm">{pred.iface}</span>
              <span className={`text-xs px-2 py-1 rounded ${
                isAttack ? 'bg-danger/10 text-danger' : 'bg-success/10 text-success'
              }`}>{pred.predictedAttack || 'normal'}</span>
            </div>

            {/* Confidence bar */}
            {pred.confidence != null && (
              <div className="mb-3">
                <div className="flex justify-between text-xs text-gray-500 mb-1">
                  <span>Confidence</span>
                  <span>{(pred.confidence * 100).toFixed(0)}%</span>
                </div>
                <div className="h-2 bg-bg-card rounded-full overflow-hidden">
                  <div className={`h-full rounded-full ${isAttack ? 'bg-danger' : 'bg-success'}`}
                    style={{ width: `${pred.confidence * 100}%` }} />
                </div>
              </div>
            )}

            {/* Top Features */}
            {topFeatures.length > 0 && (
              <div className="mb-3">
                <p className="text-xs text-gray-500 mb-2">Top Features</p>
                {topFeatures.slice(0, 5).map((f, i) => (
                  <div key={i} className="flex items-center gap-2 text-xs mb-1">
                    <span className="text-gray-400 w-32 truncate">{f.name}</span>
                    <div className="flex-1 h-1.5 bg-bg-card rounded-full overflow-hidden">
                      <div className="h-full bg-accent rounded-full"
                        style={{ width: `${Math.min(100, (f.importance || f.deviation || 0.5) * 20)}%` }} />
                    </div>
                    <span className="text-gray-300 font-mono w-16 text-right">{f.value}</span>
                    {f.normal != null && (
                      <span className="text-gray-600 font-mono w-16 text-right text-[10px]">n:{f.normal}</span>
                    )}
                  </div>
                ))}
              </div>
            )}

            {/* Anomaly Score */}
            {pred.anomalyScore != null && (
              <div className="text-xs text-gray-500 mb-2">
                Anomaly Score: <span className={`font-mono ${pred.anomalyScore < 0 ? 'text-danger' : 'text-success'}`}>
                  {pred.anomalyScore.toFixed(3)}
                </span>
              </div>
            )}

            {/* MITRE ATT&CK */}
            {isAttack && mitre && (
              <div className="bg-bg-card rounded p-3 text-xs mt-2">
                <p className="text-gray-500 mb-1">MITRE ATT&CK</p>
                <p className="text-gray-300">{mitre.id} — {mitre.tactic} — {mitre.technique}</p>
              </div>
            )}

            {/* Attack Info */}
            {isAttack && info && (
              <div className="bg-bg-card rounded p-3 text-xs mt-2">
                <p className="text-gray-500 mb-1">Severity: <span className={
                  info.severity === 'CRITICAL' ? 'text-danger' : 'text-warning'
                }>{info.severity}</span></p>
                <p className="text-gray-400 mt-1">{info.impact}</p>
                <p className="text-accent mt-1">{info.recommendation}</p>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

/* ═══ Tab 3 — KPI ═══ */
function TabKpi({ device, history, isOffline }) {
  const [showAll, setShowAll] = useState(false);

  const deviceKpis = KPI_MAP[device.name] || KPI_MAP[device.type === 'router' ? 'edge-router' : 'web-server'];

  // All available KPI metrics
  const ALL_KPIS = [
    'halfOpenConnections', 'passiveOpensRate', 'activeOpensRate', 'attemptFailsRate',
    'outRstsRate', 'inSegsRate', 'outSegsRate', 'retransRate', 'icmpInRate',
    'inOutSegRatio', 'udpInRate', 'udpOutRate', 'udpNoPortRate', 'udpInOutRatio',
    'uniqueSourceIPs', 'topAttackerRepeat', 'uniqueDestinationPorts', 'sensitivePortsHit',
    'timeWaitConnections', 'avgConnectionDuration', 'longConnectionRatio',
    'failedLogins', 'failedLoginsRate', 'sshConnectionAttempts',
    'forwardedPackets', 'routingTableSize', 'inDiscards', 'noRoutePackets',
    'throughputIn', 'throughputOut', 'bytesPerPacketIn', 'bytesPerPacketOut',
    'cpuUsage', 'memoryUsage',
  ];

  const kpis = showAll ? ALL_KPIS : deviceKpis;

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-gray-400 text-sm">Key metrics for <span className="text-accent">{device.name}</span></p>
        <button
          onClick={() => setShowAll(!showAll)}
          className={`px-3 py-1 rounded text-xs transition-colors ${
            showAll ? 'bg-accent/10 text-accent' : 'bg-bg-primary text-gray-500 hover:text-gray-300'
          }`}
        >
          {showAll ? 'Device KPIs' : 'Show All'}
        </button>
      </div>
      {kpis.map(metric => {
        let data = history.map((m, i) => {
          let v = m[metric] ?? m.tcpStats?.[metric] ?? m.securityMetric?.[metric] ?? 0;
          // Also check interfaces for throughput/latency metrics
          if (v === 0 && m.interfaces) {
            for (const iface of m.interfaces) {
              if (iface[metric] != null && iface[metric] !== 0) {
                v = iface[metric];
                break;
              }
            }
          }
          return { i, v };
        });

        // When offline, show 0 and drop chart to zero
        let latest;
        if (isOffline) {
          latest = 0;
          if (data.length > 0) {
            data = [...data, { i: data[data.length - 1].i + 1, v: 0 }];
          }
        } else {
          latest = data.length > 0 ? data[data.length - 1].v : 0;
        }

        // Skip metrics with no data when showing all
        if (showAll && data.every(d => d.v === 0)) return null;
        return (
          <div key={metric} className="bg-bg-primary rounded-lg p-3">
            <div className="flex items-center justify-between mb-1">
              <span className="text-xs text-gray-400">{metric}</span>
              <span className="text-sm font-mono text-gray-200">{typeof latest === 'number' ? latest.toFixed(2) : latest}</span>
            </div>
            {data.length > 2 && (
              <ResponsiveContainer width="100%" height={50}>
                <LineChart data={data} margin={{ top: 2, right: 2, bottom: 2, left: 2 }}>
                  {/* Explicit category axis so activeDot + cursor line snap to data points.
                      Without type="category" recharts treats x as linear and computes
                      tooltip position from pixel interpolation — which causes the dot
                      to miss some actual data points. */}
                  <XAxis dataKey="i" hide type="category" allowDuplicatedCategory={false} />
                  <YAxis hide domain={['dataMin', 'dataMax']} />
                  <Tooltip
                    contentStyle={{
                      backgroundColor: '#141923',
                      border: '1px solid #374151',
                      borderRadius: 6,
                      fontSize: 11,
                      padding: '4px 8px',
                    }}
                    cursor={{ stroke: '#374151', strokeWidth: 1 }}
                    labelFormatter={(i) => `Reading ${Number(i) + 1}`}
                    formatter={(v) => [typeof v === 'number' ? v.toFixed(2) : v, metric]}
                  />
                  <Line
                    type="monotone"
                    dataKey="v"
                    stroke="#10b981"
                    strokeWidth={1.5}
                    dot={false}
                    activeDot={{ r: 4, fill: '#10b981', stroke: '#fff', strokeWidth: 1 }}
                    isAnimationActive={false}
                  />
                </LineChart>
              </ResponsiveContainer>
            )}
          </div>
        );
      }).filter(Boolean)}
    </div>
  );
}

/* ═══ Tab — History ═══ */
/**
 * Historical chart for a single metric over a chosen period.
 * Works for any device (routers + servers).
 *
 * - Metric picker: searchable dropdown populated from /api/alert-rules/available-metrics
 * - Period picker: 1h / 6h / 24h / 7d / 30d
 * - Line chart with tooltip showing timestamp + value
 * - Min / Max / Avg stats below chart
 *
 * Backend downsamples to 200 points via Max-per-bucket, so the chart stays
 * responsive even for 30-day windows.
 */
/* ═══ Tab — History ═══ */

/**
 * Converts a JS Date to the string format expected by <input type="datetime-local">:
 *   "2026-04-20T14:00"  (local time, no timezone suffix)
 */
function toLocalInput(d) {
  const pad = n => String(n).padStart(2, '0');
  return (
    d.getFullYear() + '-' +
    pad(d.getMonth() + 1) + '-' +
    pad(d.getDate()) + 'T' +
    pad(d.getHours()) + ':' +
    pad(d.getMinutes())
  );
}

function TabHistory({ device }) {
  const [metric, setMetric] = useState('cpuUsage');
  const [period, setPeriod] = useState('24h');  // preset value OR 'custom'
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [availableMetrics, setAvailableMetrics] = useState({ system: [], tcp: [], security: [] });

  // Custom range (used when period === 'custom')
  // Default initial values: last 6 hours
  const initialNow = new Date();
  const [customFrom, setCustomFrom] = useState(() => {
    const d = new Date(initialNow.getTime() - 6 * 3600000);
    return toLocalInput(d);
  });
  const [customTo, setCustomTo] = useState(() => toLocalInput(initialNow));

  // Fetch metric list once
  useEffect(() => {
    getAvailableMetrics().then(setAvailableMetrics).catch(() => {});
  }, []);

  // Fetch history when metric/period/range/device changes
  useEffect(() => {
    if (!metric) return;
    let alive = true;
    setLoading(true);

    const fetcher = period === 'custom'
      ? getDeviceMetricHistory(device.name, metric, null, { from: customFrom, to: customTo })
      : getDeviceMetricHistory(device.name, metric, period);

    fetcher
      .then(resp => { if (alive) setData(resp); })
      .catch(() => { if (alive) setData(null); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [device.name, metric, period, customFrom, customTo]);

  // Build chart-friendly data: { t: "HH:mm", value, fullTs }
  const chartData = (data?.points || []).map(p => {
    const dt = new Date(p.timestamp);
    let label;

    let spanHours = 24;
    if (data?.from && data?.to) {
      spanHours = (new Date(data.to) - new Date(data.from)) / 3600000;
    } else if (period === '1h')  spanHours = 1;
    else if (period === '6h')   spanHours = 6;
    else if (period === '24h')  spanHours = 24;
    else if (period === '7d')   spanHours = 168;
    else if (period === '30d')  spanHours = 720;

    if (spanHours <= 24) {
      label = dt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } else {
      label = dt.toLocaleDateString([], { month: 'short', day: 'numeric' }) +
              ' ' + dt.toLocaleTimeString([], { hour: '2-digit' });
    }
    return {
      t: label,
      value: p.value,
      fullTs: dt.toLocaleString(),
      ts: dt.getTime(),
    };
  });

  // Note: offline shading was removed — the previous logic could mark valid
  // data regions as grey. Now the chart simply shows what we have. Gaps in
  // the line indicate missing data, no overlay needed.

  const stats = data?.stats || {};

  // Find unit label if available
  const allMetrics = [
    ...(availableMetrics.system || []),
    ...(availableMetrics.tcp || []),
    ...(availableMetrics.security || []),
  ];
  const metricInfo = allMetrics.find(m => m.name === metric);
  const unit = metricInfo?.unit || '';

  return (
    <div className="space-y-4">
      {/* Controls */}
      <div className="flex items-center gap-2 flex-wrap">
        <HistoryMetricPicker
          value={metric}
          onChange={setMetric}
          availableMetrics={availableMetrics}
        />
        <select
          value={period}
          onChange={e => setPeriod(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded px-2 py-1 text-xs text-gray-300"
        >
          <option value="1h">Last hour</option>
          <option value="6h">Last 6h</option>
          <option value="24h">Last 24h</option>
          <option value="7d">Last 7 days</option>
          <option value="30d">Last 30 days</option>
          <option value="custom">Custom range…</option>
        </select>
      </div>

      {/* Custom date range inputs — visible only when period === 'custom' */}
      {period === 'custom' && (
        <div className="bg-bg-primary rounded-lg p-3 space-y-2">
          <div className="grid grid-cols-2 gap-2">
            <div>
              <label className="block text-[10px] text-gray-500 mb-1">From</label>
              <input
                type="datetime-local"
                value={customFrom}
                onChange={e => setCustomFrom(e.target.value)}
                className="w-full bg-bg-card border border-gray-700 rounded px-2 py-1 text-xs text-gray-200"
              />
            </div>
            <div>
              <label className="block text-[10px] text-gray-500 mb-1">To</label>
              <input
                type="datetime-local"
                value={customTo}
                onChange={e => setCustomTo(e.target.value)}
                className="w-full bg-bg-card border border-gray-700 rounded px-2 py-1 text-xs text-gray-200"
              />
            </div>
          </div>
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[10px] text-gray-500">Jump to:</span>
            {[
              { label: '1h ago', hours: 1 },
              { label: '6h ago', hours: 6 },
              { label: '24h ago', hours: 24 },
              { label: 'Yesterday', hours: 48 },
              { label: '3 days ago', hours: 72 },
            ].map(preset => (
              <button
                key={preset.label}
                onClick={() => {
                  const end = new Date();
                  const start = new Date(end.getTime() - preset.hours * 3600000);
                  setCustomFrom(toLocalInput(start));
                  setCustomTo(toLocalInput(end));
                }}
                className="text-[10px] px-2 py-0.5 rounded bg-bg-card border border-gray-700
                           text-gray-400 hover:text-accent hover:border-accent/30 transition-colors"
              >
                {preset.label} → now
              </button>
            ))}
          </div>
        </div>
      )}

      {loading && (
        <p className="text-center text-xs text-gray-600 py-6">Loading history...</p>
      )}

      {!loading && chartData.length === 0 && (
        <div className="text-center text-xs text-gray-600 py-6">
          <p>No data for <span className="font-mono text-gray-400">{metric}</span></p>
          <p className="mt-1 text-gray-700">Try a longer period or a different metric</p>
        </div>
      )}

      {!loading && chartData.length > 0 && (
        <>
          {/* Chart */}
          <div className="bg-bg-primary rounded-lg p-3">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs text-gray-400 font-mono">{metric}</span>
              <span className="text-[10px] text-gray-500">{chartData.length} points</span>
            </div>
            <ResponsiveContainer width="100%" height={180}>
              <LineChart data={chartData} margin={{ top: 5, right: 10, bottom: 5, left: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#1c2333" />
                <XAxis
                  dataKey="t"
                  type="category"
                  allowDuplicatedCategory={false}
                  tick={{ fill: '#6b7280', fontSize: 9 }}
                  interval="preserveStartEnd"
                  minTickGap={30}
                />
                <YAxis
                  tick={{ fill: '#6b7280', fontSize: 9 }}
                  width={35}
                  domain={[0, 'auto']}
                />

                <Tooltip
                  contentStyle={{
                    backgroundColor: '#141923',
                    border: '1px solid #374151',
                    borderRadius: 6,
                    fontSize: 11,
                    padding: '6px 10px',
                  }}
                  cursor={{ stroke: '#374151', strokeWidth: 1 }}
                  labelFormatter={(_, payload) =>
                    payload && payload[0] ? payload[0].payload.fullTs : ''
                  }
                  formatter={(v) => [
                    typeof v === 'number' ? v.toFixed(2) + (unit ? ' ' + unit : '') : v,
                    metric,
                  ]}
                />
                <Line
                  type="monotone"
                  dataKey="value"
                  stroke="#10b981"
                  strokeWidth={1.5}
                  dot={false}
                  activeDot={{ r: 5, fill: '#10b981', stroke: '#fff', strokeWidth: 1.5 }}
                  isAnimationActive={false}
                />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-2">
            <StatBox label="Min" value={stats.min} unit={unit} color="text-gray-300" />
            <StatBox label="Avg" value={stats.avg} unit={unit} color="text-accent" />
            <StatBox label="Max" value={stats.max} unit={unit} color="text-warning" />
          </div>
          <p className="text-[10px] text-gray-600 text-center">
            Based on {stats.count || 0} readings
          </p>
        </>
      )}
    </div>
  );
}

function StatBox({ label, value, unit, color }) {
  const display = typeof value === 'number' ? value.toFixed(2) : '—';
  return (
    <div className="bg-bg-primary rounded-lg p-2 text-center">
      <p className="text-[10px] text-gray-500 uppercase tracking-wider">{label}</p>
      <p className={`text-sm font-mono ${color} mt-0.5`}>
        {display}{unit && <span className="text-[10px] text-gray-500 ml-1">{unit}</span>}
      </p>
    </div>
  );
}

/**
 * Metric picker with searchable dropdown grouped by category (system / tcp / security).
 * Simpler than full autocomplete — dropdown shows all metrics since list is bounded.
 */
function HistoryMetricPicker({ value, onChange, availableMetrics }) {
  return (
    <select
      value={value}
      onChange={e => onChange(e.target.value)}
      className="bg-bg-primary border border-gray-700 rounded px-2 py-1 text-xs text-gray-300 font-mono min-w-[180px]"
    >
      {availableMetrics.system?.length > 0 && (
        <optgroup label="System">
          {availableMetrics.system.map(m => (
            <option key={m.name} value={m.name}>{m.name}{m.unit ? ` (${m.unit})` : ''}</option>
          ))}
        </optgroup>
      )}
      {availableMetrics.tcp?.length > 0 && (
        <optgroup label="Network / TCP">
          {availableMetrics.tcp.map(m => (
            <option key={m.name} value={m.name}>{m.name}{m.unit ? ` (${m.unit})` : ''}</option>
          ))}
        </optgroup>
      )}
      {availableMetrics.security?.length > 0 && (
        <optgroup label="Security">
          {availableMetrics.security.map(m => (
            <option key={m.name} value={m.name}>{m.name}{m.unit ? ` (${m.unit})` : ''}</option>
          ))}
        </optgroup>
      )}
    </select>
  );
}

/* ═══ Tab — Interfaces (routers only) ═══ */
/**
 * Per-interface timeline, mirroring the Home device timeline.
 *
 * Each interface gets a horizontal bar colored by its UP/DOWN history:
 *   - Green segments  = UP periods (from InterfaceStatusLog UP entries)
 *   - Grey segments   = DOWN periods (from InterfaceStatusLog DOWN entries)
 *   - Dark background = "no data" (no log covers this sub-range)
 *
 * Hovering any segment shows start time, end time, and duration.
 * Hovering a blank region shows "No data recorded around <time>".
 *
 * Auto-refreshes every 30s so recent transitions stay visible.
 */
function TabInterfaces({ device, metrics }) {
  const [period, setPeriod] = useState('24h');
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(false);
  const [tooltip, setTooltip] = useState(null);

  // Fetch + auto-refresh
  useEffect(() => {
    let alive = true;
    const refresh = () => {
      setLoading(true);
      getInterfaceTimeline(device.name, period)
        .then(data => { if (alive) setEvents(data || []); })
        .catch(() => { if (alive) setEvents([]); })
        .finally(() => { if (alive) setLoading(false); });
    };
    refresh();
    const id = setInterval(refresh, 30000);
    return () => { alive = false; clearInterval(id); };
  }, [device.name, period]);

  // Live interfaces from current metrics
  const currentIfaces = metrics?.[device.name]?.interfaces || [];

  // Group events by interface name
  const eventsByIface = {};
  for (const ev of events) {
    if (!eventsByIface[ev.interfaceName]) eventsByIface[ev.interfaceName] = [];
    eventsByIface[ev.interfaceName].push(ev);
  }

  // Combine names from history + live metrics
  const allNames = new Set([
    ...currentIfaces.map(i => i.interfaceName),
    ...Object.keys(eventsByIface),
  ]);
  const ifaceNames = Array.from(allNames).sort();

  // Time window
  const now = new Date();
  const periodMs = {
    '1h': 3600000, '6h': 21600000, '24h': 86400000,
    '7d': 604800000, '30d': 2592000000,
  };
  const rangeMs = periodMs[period] || 86400000;
  const startTime = new Date(now.getTime() - rangeMs);
  const totalMs = now.getTime() - startTime.getTime();

  // Helper: does [s,e] overlap [startTime, now]?
  const overlapsWindow = (startIso, endIso) => {
    if (!startIso) return false;
    const s = new Date(startIso).getTime();
    const e = endIso ? new Date(endIso).getTime() : now.getTime();
    return e >= startTime.getTime() && s <= now.getTime();
  };

  return (
    <div className="space-y-4">
      {/* Header + period selector */}
      <div className="flex items-center justify-between">
        <p className="text-xs text-gray-400">
          Interface UP/DOWN history for <span className="text-accent font-mono">{device.name}</span>
        </p>
        <select
          value={period}
          onChange={e => setPeriod(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded px-2 py-1 text-xs text-gray-300"
        >
          <option value="1h">Last hour</option>
          <option value="6h">Last 6h</option>
          <option value="24h">Last 24h</option>
          <option value="7d">Last week</option>
          <option value="30d">Last 30 days</option>
        </select>
      </div>

      {loading && events.length === 0 && (
        <p className="text-center text-xs text-gray-600 py-4">Loading timeline...</p>
      )}

      {!loading && ifaceNames.length === 0 && (
        <p className="text-center text-xs text-gray-600 py-4">No interfaces detected.</p>
      )}

      {/* Per-interface rows */}
      {ifaceNames.map(name => {
        const iface = currentIfaces.find(i => i.interfaceName === name);
        const isCurrentlyUp = iface ? iface.isUp !== false : null;
        const windowEvents = (eventsByIface[name] || [])
          .filter(ev => overlapsWindow(ev.startedAt, ev.endedAt));

        const downCount = windowEvents.filter(e => e.status === 'DOWN').length;

        return (
          <div key={name} className="bg-bg-primary rounded-lg p-3">
            <div className="flex items-center justify-between mb-2">
              <div className="flex items-center gap-2">
                <span className={`w-2 h-2 rounded-full ${
                  isCurrentlyUp === null ? 'bg-gray-500' :
                  isCurrentlyUp ? 'bg-success' : 'bg-gray-500'
                }`}></span>
                <span className="text-sm font-mono text-gray-200">{name}</span>
                <span className={`text-xs ${
                  isCurrentlyUp === null ? 'text-gray-500' :
                  isCurrentlyUp ? 'text-success' : 'text-gray-400'
                }`}>
                  {isCurrentlyUp === null ? 'unknown' : isCurrentlyUp ? 'UP' : 'DOWN'}
                </span>
              </div>
              <span className="text-[10px] text-gray-500">
                {downCount === 0 ? 'No outages in period' : `${downCount} outage${downCount > 1 ? 's' : ''}`}
              </span>
            </div>

            {/* Timeline bar — dark base, hover for no-data, overlaid UP/DOWN bars */}
            <div className="h-5 bg-bg-card rounded-sm relative overflow-hidden">
              {/* No-data hover layer — catches hover on blank regions */}
              <div
                className="absolute inset-0"
                style={{ zIndex: 0 }}
                onMouseMove={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  const xFrac = (e.clientX - rect.left) / rect.width;
                  const hoverTime = new Date(startTime.getTime() + xFrac * totalMs);
                  setTooltip({
                    x: e.clientX,
                    y: rect.top - 10,
                    text: `No data recorded around ${hoverTime.toLocaleString()}`,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />

              {/* UP + DOWN segments */}
              {windowEvents.map((ev, i) => {
                // Clip BOTH start and end to the window to avoid overflow bars.
                const startRaw = new Date(ev.startedAt).getTime();
                const endRaw = ev.endedAt ? new Date(ev.endedAt).getTime() : now.getTime();
                const startClipped = Math.max(startRaw, startTime.getTime());
                const endClipped   = Math.min(endRaw, now.getTime());
                if (endClipped <= startClipped) return null;

                const leftPct  = (startClipped - startTime.getTime()) / totalMs * 100;
                const widthPct = Math.max(0.3,
                    (endClipped - startClipped) / totalMs * 100);

                const isUp = ev.status === 'UP';
                const colorClass = isUp
                  ? 'bg-success/50 hover:bg-success/70'
                  : 'bg-gray-500/80 hover:bg-gray-500';

                return (
                  <div
                    key={i}
                    className={`absolute top-0 h-full ${colorClass} transition-colors cursor-pointer`}
                    style={{
                      left: `${leftPct}%`,
                      width: `${widthPct}%`,
                      zIndex: 1,
                    }}
                    onMouseEnter={(e) => {
                      const rect = e.target.getBoundingClientRect();
                      setTooltip({
                        x: rect.left + rect.width / 2,
                        y: rect.top - 8,
                        text: `${ev.status}: ${new Date(ev.startedAt).toLocaleString()} → ${
                          ev.endedAt ? new Date(ev.endedAt).toLocaleString() : 'now'
                        } (${ev.durationFormatted || 'ongoing'})`,
                      });
                    }}
                    onMouseLeave={() => setTooltip(null)}
                  />
                );
              })}
            </div>

            <div className="flex justify-between text-[9px] text-gray-600 mt-1">
              <span>{startTime.toLocaleString()}</span>
              <span>now</span>
            </div>
          </div>
        );
      })}

      {/* Floating tooltip */}
      {tooltip && (
        <div
          className="fixed bg-bg-card border border-gray-700 rounded-lg px-2 py-1 text-[11px]
                     text-gray-200 shadow-xl z-50 pointer-events-none whitespace-nowrap"
          style={{ left: tooltip.x, top: tooltip.y, transform: 'translate(-50%, -100%)' }}
        >
          {tooltip.text}
        </div>
      )}
    </div>
  );
}

/* ═══ Tab 4 — Counters ═══ */
function TabCounters({ device, metrics, isOffline }) {
  const current = isOffline ? null : metrics?.[device.name];
  const tcp = current?.tcpStats || {};
  const sec = current?.securityMetric || {};

  const counters = [
    ['passiveOpens', tcp.passiveOpens], ['activeOpens', tcp.activeOpens],
    ['inSegs', tcp.inSegs], ['outSegs', tcp.outSegs],
    ['inErrs', tcp.inErrs], ['outRsts', tcp.outRsts],
    ['tcpRetransmissions', tcp.tcpRetransmissions],
    ['icmpInMsgs', tcp.icmpInMsgs], ['icmpInErrors', tcp.icmpInErrors],
    ['udpInDatagrams', tcp.udpInDatagrams], ['udpOutDatagrams', tcp.udpOutDatagrams],
    ['udpInErrors', tcp.udpInErrors], ['udpNoPorts', tcp.udpNoPorts],
    ['halfOpenConnections', sec.halfOpenConnections],
    ['uniqueSourceIPs', sec.uniqueSourceIPs],
    ['failedLogins', sec.failedLogins],
    ['timeWaitConnections', sec.timeWaitConnections],
  ];

  return (
    <div className="space-y-1">
      <p className="text-gray-400 text-sm mb-3">Raw cumulative counters</p>
      {counters.map(([name, val]) => (
        <div key={name} className="flex justify-between py-1.5 border-b border-gray-800/50">
          <span className="text-xs text-gray-500">{name}</span>
          <span className="text-xs font-mono text-gray-300">{val ?? '—'}</span>
        </div>
      ))}
    </div>
  );
}

/* ═══ Tab 5 — Logs ═══ */
function TabLogs({ device }) {
  const [logs, setLogs] = useState('');
  const [loading, setLoading] = useState(false);

  const fetchLogs = async (cmd) => {
    setLoading(true);
    try {
      const { executeCommand } = await import('../services/api');
      const res = await executeCommand(device.name, cmd);
      setLogs(res.output || '(no output)');
    } catch {
      setLogs('Error: Cannot connect to terminal agent');
    }
    setLoading(false);
  };

  return (
    <div className="space-y-3">
      <div className="flex flex-wrap gap-2">
        <LogBtn label="messages" onClick={() => fetchLogs('tail -30 /var/log/messages')} />
        <LogBtn label="auth.log" onClick={() => fetchLogs('tail -30 /var/log/auth.log')} />
        <LogBtn label="ss -tn" onClick={() => fetchLogs('ss -tn')} />
        <LogBtn label="arp -n" onClick={() => fetchLogs('arp -n')} />
      </div>
      <div className="bg-black rounded-lg p-3 min-h-[200px] max-h-[400px] overflow-y-auto">
        {loading ? (
          <p className="text-yellow-400 text-xs animate-pulse">Loading...</p>
        ) : logs ? (
          <pre className="text-green-400 text-xs whitespace-pre-wrap break-all">{logs}</pre>
        ) : (
          <p className="text-gray-600 text-xs">Click a button above to fetch logs</p>
        )}
      </div>
    </div>
  );
}

function LogBtn({ label, onClick }) {
  return (
    <button onClick={onClick}
      className="px-2 py-1 bg-bg-primary border border-gray-700 rounded text-xs font-mono
               text-gray-400 hover:text-accent hover:border-accent/30 transition-colors">
      {label}
    </button>
  );
}

/* ═══ Tab 6 — Properties ═══ */
function TabProperties({ device, metrics }) {
  const current = metrics?.[device.name];
  const interfaces = current?.interfaces?.map(i => i.interfaceName).join(', ') || '—';
  const neighbors = current?.neighbors?.join(', ') || '—';

  // ARP table — fetched from the device's most recent metric snapshot.
  // Refreshes when the panel is reopened or the device changes.
  const [arpTable, setArpTable] = useState([]);
  const [arpLoading, setArpLoading] = useState(false);

  useEffect(() => {
    let alive = true;
    setArpLoading(true);
    getDeviceArpTable(device.name)
      .then(data => { if (alive) setArpTable(data || []); })
      .catch(() => { if (alive) setArpTable([]); })
      .finally(() => { if (alive) setArpLoading(false); });
    return () => { alive = false; };
  }, [device.name]);

  return (
    <div className="space-y-4">
      {/* Basic properties */}
      <div className="space-y-2">
        <PropRow label="Device Name" value={device.name} />
        <PropRow label="Device Type" value={device.type} />
        <PropRow label="Status" value={current?.status || 'unknown'} />
        <PropRow label="Interfaces" value={interfaces} />
        <PropRow label="Neighbors" value={neighbors} />
      </div>

      {/* ARP table — Layer-2 IP↔MAC pairs this device saw in its last metric.
          Useful for spotting unexpected devices or ARP anomalies. */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <p className="text-xs text-gray-400">
            ARP Table <span className="text-gray-600">(Layer-2 neighbors)</span>
          </p>
          <span className="text-[10px] text-gray-600">
            {arpLoading ? 'loading…' : `${arpTable.length} entries`}
          </span>
        </div>

        {!arpLoading && arpTable.length === 0 && (
          <p className="text-center text-xs text-gray-600 py-3 bg-bg-primary rounded">
            No ARP entries recorded
          </p>
        )}

        {arpTable.length > 0 && (
          <div className="bg-bg-primary rounded-lg overflow-hidden">
            <table className="w-full text-xs">
              <thead>
                <tr className="border-b border-gray-800">
                  <th className="text-left p-2 text-gray-500 font-medium">IP Address</th>
                  <th className="text-left p-2 text-gray-500 font-medium">MAC Address</th>
                </tr>
              </thead>
              <tbody>
                {arpTable.map((entry, i) => (
                  <tr key={i} className="border-b border-gray-800 last:border-0 hover:bg-bg-hover">
                    <td className="p-2 font-mono text-gray-300">{entry.ip || '—'}</td>
                    <td className="p-2 font-mono text-gray-400">{entry.mac || '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}

/* ═══ Helpers ═══ */

function MetricBox({ label, value }) {
  return (
    <div className="bg-bg-primary rounded-lg p-3">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className="text-lg font-mono text-gray-200">{value}</p>
    </div>
  );
}

function MiniChart({ data, label, color }) {
  return (
    <div className="bg-bg-primary rounded-lg p-3">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <ResponsiveContainer width="100%" height={50}>
        <LineChart data={data}>
          <YAxis hide domain={['auto', 'auto']} />
          <Tooltip
            contentStyle={{ background: '#141923', border: '1px solid #374151', borderRadius: 8, fontSize: 11 }}
            labelStyle={{ display: 'none' }}
            formatter={(v) => [v.toFixed(1), label]}
          />
          <Line type="monotone" dataKey="v" stroke={color} strokeWidth={1.5} dot={false} />
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

function PropRow({ label, value }) {
  return (
    <div className="flex justify-between items-center py-2 border-b border-gray-800">
      <span className="text-sm text-gray-500">{label}</span>
      <span className="text-sm text-gray-200 font-mono">{value}</span>
    </div>
  );
}

export default SidePanel;