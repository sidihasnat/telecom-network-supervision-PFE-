import React, { useState, useEffect } from 'react';
import {
  getRecentSessions, getSessionStats, getDowntime,
  getDeviceTimeline, getOfflineDowntime, getAttackTimeline
} from '../services/api';
import { getAiStatus } from '../services/aiApi';
import TopologyMap from '../components/TopologyMap';

const DEVICES = ['edge-router','core-router','web-server','dns-server','ftp-server','db-server'];

function Home({ onDeviceClick, latestMetrics, latestPredictions, deviceLastSeen, activeSessions = [] }) {
  const [activeTab, setActiveTab] = useState(0);

  return (
    <div>
      <div className="flex gap-4 mb-6">
        <button
          onClick={() => setActiveTab(0)}
          className={`px-4 py-2 rounded-lg text-sm transition-colors ${
            activeTab === 0 ? 'bg-accent/10 text-accent' : 'text-gray-400 hover:text-gray-200'
          }`}
        >🏠 Overview</button>
        <button
          onClick={() => setActiveTab(1)}
          className={`px-4 py-2 rounded-lg text-sm transition-colors ${
            activeTab === 1 ? 'bg-accent/10 text-accent' : 'text-gray-400 hover:text-gray-200'
          }`}
        >📋 SLA</button>
      </div>

      {activeTab === 0 && (
        <TabOverview
          onDeviceClick={onDeviceClick}
          latestMetrics={latestMetrics}
          latestPredictions={latestPredictions}
          deviceLastSeen={deviceLastSeen}
          activeSessions={activeSessions}
        />
      )}
      {activeTab === 1 && <TabSLA />}
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Tab 1 — Overview                      */
/* ═══════════════════════════════════════ */

function TabOverview({ onDeviceClick, latestMetrics, latestPredictions, deviceLastSeen, activeSessions = [] }) {
  const [stats, setStats] = useState({});
  const [recentSessions, setRecentSessions] = useState([]);
  const [aiStatus, setAiStatus] = useState('—');
  const [timeline, setTimeline] = useState([]);
  const [attackTimeline, setAttackTimeline] = useState([]);
  const [timelinePeriod, setTimelinePeriod] = useState('6h');

  useEffect(() => {
    getSessionStats().then(setStats).catch(() => {});
    getRecentSessions().then(setRecentSessions).catch(() => {});
    getAiStatus()
      .then((d) => setAiStatus(d.livePredictionActive ? 'Live' : 'Stopped'))
      .catch(() => setAiStatus('Disconnected'));
  }, []);

  // Auto-refresh timeline every 30s (so events added after page load appear)
  // and refetch when activeSessions change (so newly ended attacks show up right away).
  // Also refetches whenever the period dropdown is changed.
  useEffect(() => {
    const refresh = () => {
      getDeviceTimeline(timelinePeriod).then(setTimeline).catch(() => {});
      getAttackTimeline(timelinePeriod).then(setAttackTimeline).catch(() => {});
    };
    refresh();
    const id = setInterval(refresh, 30000);
    return () => clearInterval(id);
  }, [timelinePeriod, activeSessions]);

  // Refresh recent sessions when predictions change
  useEffect(() => {
    getRecentSessions().then(setRecentSessions).catch(() => {});
  }, [latestPredictions]);

  // Count attacks, warnings, normal devices based on activeSessions.
  //
  // - AI attacks                          → attack (red)
  // - TRIGGER_RULE with CRITICAL severity → attack (red)
  // - TRIGGER_RULE with WARNING severity  → warning (yellow)
  // - INTERFACE_DOWN / DEVICE_OFFLINE     → NEITHER (they're shown as grey/offline elsewhere)
  let attackDevices = new Set();
  let warningDevices = new Set();

  for (const s of activeSessions) {
    if (s.attackType === 'transit') continue;

    const isAi = !s.sessionSource || s.sessionSource === 'AI';
    const isCritTrigger = s.sessionSource === 'TRIGGER_RULE' && s.severity === 'CRITICAL';
    const isWarnTrigger = s.sessionSource === 'TRIGGER_RULE' && s.severity !== 'CRITICAL';

    if (isAi || isCritTrigger) {
      attackDevices.add(s.deviceName);
    } else if (isWarnTrigger) {
      warningDevices.add(s.deviceName);
    }
    // INTERFACE_DOWN / DEVICE_OFFLINE intentionally ignored here.
  }
  for (const d of attackDevices) warningDevices.delete(d);

  const normalCount = DEVICES.filter(d => !attackDevices.has(d) && !warningDevices.has(d)).length;

  return (
    <div className="space-y-6">
      {/* Summary Cards */}
      <div className="grid grid-cols-4 gap-4">
        <SummaryCard label="Active Attacks" value={attackDevices.size} color="danger" />
        <SummaryCard label="Warnings" value={warningDevices.size} color="warning" />
        <SummaryCard label="Normal Devices" value={normalCount} color="success" />
        <SummaryCard label="AI Status" value={aiStatus} color="accent" />
      </div>

      {/* Topology Map */}
      <div className="bg-bg-card rounded-xl p-6 border border-gray-800">
        <h3 className="text-sm text-gray-400 mb-2">Network Topology</h3>
        <TopologyMap
          latestPredictions={latestPredictions}
          onDeviceClick={onDeviceClick}
          deviceLastSeen={deviceLastSeen}
          activeSessions={activeSessions}
        />
      </div>

      <div className="grid grid-cols-2 gap-4">
        {/* Problem Timeline */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm text-gray-400">Problem Timeline</h3>
            <select
              value={timelinePeriod}
              onChange={(e) => {
                setTimelinePeriod(e.target.value);
                getDeviceTimeline(e.target.value).then(setTimeline).catch(() => {});
                getAttackTimeline(e.target.value).then(setAttackTimeline).catch(() => {});
              }}
              className="bg-bg-primary border border-gray-700 rounded px-2 py-1 text-xs text-gray-400"
            >
              <option value="1h">Last hour</option>
              <option value="6h">Last 6h</option>
              <option value="24h">Last 24h</option>
              <option value="7d">Last week</option>
            </select>
          </div>
          <ProblemTimeline timeline={timeline} attackTimeline={attackTimeline} period={timelinePeriod} />
        </div>

        {/* Live Attack Feed */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Live Attack Feed</h3>
          <div className="space-y-2 max-h-48 overflow-y-auto">
            {recentSessions.length === 0 ? (
              <p className="text-gray-600 text-sm text-center py-4">No recent attacks</p>
            ) : (
              recentSessions.slice(0, 8).map((s) => (
                <div
                  key={s.id}
                  onClick={() => onDeviceClick(
                    { name: s.deviceName, type: s.deviceName.includes('router') ? 'router' : 'server' },
                    1
                  )}
                  className="flex items-center gap-2 text-xs cursor-pointer hover:bg-bg-hover p-1.5 rounded transition-colors"
                >
                  <span className={`w-1.5 h-1.5 rounded-full ${
                    s.status === 'ACTIVE' ? 'bg-danger animate-pulse' :
                    s.status === 'MITIGATED' ? 'bg-success' : 'bg-gray-500'
                  }`}></span>
                  <span className="font-mono text-gray-500">
                    {s.startedAt ? new Date(s.startedAt).toLocaleTimeString() : ''}
                  </span>
                  <span className={`font-mono ${s.status === 'ACTIVE' ? 'text-danger' : 'text-gray-400'}`}>
                    {s.attackType}
                  </span>
                  <span className="text-gray-500">{s.deviceName}::{s.interfaceName}</span>
                  <span className="text-gray-600 ml-auto">
                    {s.avgConfidence ? `${(s.avgConfidence * 100).toFixed(0)}%` : ''}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Problem Timeline Component            */
/* ═══════════════════════════════════════ */

function ProblemTimeline({ timeline, attackTimeline = [], period = '6h' }) {
  const [tooltip, setTooltip] = useState(null);

  if (!timeline || timeline.length === 0) {
    return (
      <div className="h-40 flex items-center justify-center text-gray-600 text-sm">
        No status events recorded yet.
      </div>
    );
  }

  // Group status events by device
  const grouped = {};
  for (const entry of timeline) {
    if (!grouped[entry.deviceName]) grouped[entry.deviceName] = [];
    grouped[entry.deviceName].push(entry);
  }

  // UX-6: Group attack sessions by device
  const attackGrouped = {};
  for (const session of attackTimeline) {
    if (session.attackType === 'transit') continue; // skip transit
    if (!attackGrouped[session.deviceName]) attackGrouped[session.deviceName] = [];
    attackGrouped[session.deviceName].push(session);
  }

  // Time range based on period
  const now = new Date();
  const periodMs = {
    '1h': 3600000, '6h': 21600000, '24h': 86400000, '7d': 604800000
  };
  const rangeMs = periodMs[period] || 21600000;
  const startTime = new Date(now.getTime() - rangeMs);
  const totalMs = now.getTime() - startTime.getTime();

  // Helper: does [start, end] overlap [startTime, now]?
  // Accepts ISO strings or Date; null end = ongoing (treated as now).
  const overlapsWindow = (startIso, endIso) => {
    if (!startIso) return false;
    const s = new Date(startIso).getTime();
    const e = endIso ? new Date(endIso).getTime() : now.getTime();
    return e >= startTime.getTime() && s <= now.getTime();
  };

  return (
    <div className="space-y-2 relative">
      {DEVICES.map(device => {
        // Apply window filter HERE so a log entry that ended a week ago never
        // draws inside a 6h window, even if Home fetched a wider range.
        const events  = (grouped[device]  || []).filter(ev  => overlapsWindow(ev.startedAt,  ev.endedAt));
        const attacks = (attackGrouped[device] || []).filter(atk => overlapsWindow(atk.startedAt, atk.endedAt));
        return (
          <div key={device} className="flex items-center gap-2">
            <span className="text-[10px] text-gray-500 w-20 truncate font-mono">{device}</span>
            <div className="flex-1 h-5 bg-bg-primary rounded-sm relative overflow-hidden">
              {/* No-data baseline: invisible hover layer behind everything.
                  When the user hovers over a blank region (no event/attack coverage),
                  we show a "No data recorded for this period" tooltip so they know
                  why that region is dark. Actual bars are painted above with
                  z-index: 1/2 so they intercept their own hovers first. */}
              <div
                className="absolute inset-0"
                style={{ zIndex: 0 }}
                onMouseEnter={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  const xFrac = (e.clientX - rect.left) / rect.width;
                  const hoverTime = new Date(startTime.getTime() + xFrac * totalMs);
                  setTooltip({
                    x: e.clientX,
                    y: rect.top - 10,
                    text: `No data recorded around ${hoverTime.toLocaleString()}`,
                    device: device,
                  });
                }}
                onMouseMove={(e) => {
                  const rect = e.currentTarget.getBoundingClientRect();
                  const xFrac = (e.clientX - rect.left) / rect.width;
                  const hoverTime = new Date(startTime.getTime() + xFrac * totalMs);
                  setTooltip({
                    x: e.clientX,
                    y: rect.top - 10,
                    text: `No data recorded around ${hoverTime.toLocaleString()}`,
                    device: device,
                  });
                }}
                onMouseLeave={() => setTooltip(null)}
              />

              {/* Online/Offline bars */}
              {events.map((ev, i) => {
                // Clip BOTH start and end to the window before computing position.
                // Without this, a log running 18:30 yesterday → 19:30 today would
                // render as a bar starting at left=0% with width=100% (because
                // start is far before the window AND end - start is huge), making
                // it fill the whole timeline incorrectly.
                const startRaw = new Date(ev.startedAt);
                const endRaw = ev.endedAt ? new Date(ev.endedAt) : now;
                const startClipped = startRaw < startTime ? startTime : startRaw;
                const endClipped   = endRaw > now ? now : endRaw;
                if (endClipped <= startClipped) return null;  // safety: outside window

                const leftPct  = (startClipped.getTime() - startTime.getTime()) / totalMs * 100;
                const widthPct = Math.max(0.5,
                    (endClipped.getTime() - startClipped.getTime()) / totalMs * 100);
                const isOffline = ev.status === 'OFFLINE';
                const color = isOffline ? 'bg-gray-500' : 'bg-success/40';

                return (
                  <div
                    key={`status-${i}`}
                    className={`absolute top-0 h-full ${color} rounded-sm cursor-pointer
                               hover:brightness-125 transition-all`}
                    style={{ left: `${leftPct}%`, width: `${widthPct}%`, zIndex: 1 }}
                    onMouseEnter={(e) => {
                      const rect = e.target.getBoundingClientRect();
                      const duration = ev.durationSeconds
                        ? formatDuration(ev.durationSeconds)
                        : 'ongoing';
                      setTooltip({
                        x: rect.left + rect.width / 2,
                        y: rect.top - 10,
                        text: `${ev.status}: ${new Date(ev.startedAt).toLocaleString()} → ${
                          ev.endedAt ? new Date(ev.endedAt).toLocaleString() : 'now'
                        } (${duration})`,
                        device: device,
                      });
                    }}
                    onMouseLeave={() => setTooltip(null)}
                  />
                );
              })}

              {/* Timeline bars:
                    - AI attacks + CRITICAL triggers  → red
                    - WARNING trigger rules           → yellow
                    - INTERFACE_DOWN / DEVICE_OFFLINE → grey (not a warning, just "not working")
              */}
              {attacks.map((atk, i) => {
                // Same clipping logic as event bars above.
                const startRaw = new Date(atk.startedAt);
                const endRaw = atk.endedAt ? new Date(atk.endedAt) : now;
                const startClipped = startRaw < startTime ? startTime : startRaw;
                const endClipped   = endRaw > now ? now : endRaw;
                if (endClipped <= startClipped) return null;

                const leftPct  = (startClipped.getTime() - startTime.getTime()) / totalMs * 100;
                const widthPct = Math.max(0.5,
                    (endClipped.getTime() - startClipped.getTime()) / totalMs * 100);

                const isOfflineKind = atk.sessionSource === 'INTERFACE_DOWN' || atk.sessionSource === 'DEVICE_OFFLINE';
                const isTriggerWarn = atk.sessionSource === 'TRIGGER_RULE' && atk.severity !== 'CRITICAL';

                let barColor, icon, label;
                if (isOfflineKind) {
                  barColor = 'bg-gray-500/60 hover:bg-gray-500/80';
                  icon = atk.sessionSource === 'INTERFACE_DOWN' ? '🔌' : '⚫';
                  label = atk.sessionSource === 'INTERFACE_DOWN' ? 'Interface down' : 'Device offline';
                } else if (isTriggerWarn) {
                  barColor = 'bg-warning/60 hover:bg-warning/80';
                  icon = '⚠️';
                  const m = atk.attackType.match(/^trigger:(.+?)([><]=?)(.+)$/);
                  label = m ? `Rule: ${m[1]} ${m[2]} ${m[3]}` : atk.attackType;
                } else {
                  barColor = 'bg-danger/60 hover:bg-danger/80';
                  icon = '🚨';
                  label = atk.attackType;
                }

                return (
                  <div
                    key={`attack-${i}`}
                    className={`absolute top-0 h-full rounded-sm cursor-pointer transition-all ${barColor}`}
                    style={{ left: `${leftPct}%`, width: `${widthPct}%`, zIndex: 2 }}
                    onMouseEnter={(e) => {
                      const rect = e.target.getBoundingClientRect();
                      const dur = atk.durationFormatted || (atk.endedAt ? '' : 'ongoing');
                      setTooltip({
                        x: rect.left + rect.width / 2,
                        y: rect.top - 10,
                        text: `${icon} ${label}: ${new Date(atk.startedAt).toLocaleString()} → ${
                          atk.endedAt ? new Date(atk.endedAt).toLocaleString() : 'now'
                        } (${dur})`,
                        device: device,
                      });
                    }}
                    onMouseLeave={() => setTooltip(null)}
                  />
                );
              })}
            </div>
          </div>
        );
      })}

      {/* Tooltip */}
      {tooltip && (
        <div
          className="fixed bg-bg-card border border-gray-700 rounded-lg px-3 py-2 text-xs
                     text-gray-200 shadow-xl z-50 pointer-events-none whitespace-nowrap"
          style={{ left: tooltip.x, top: tooltip.y, transform: 'translate(-50%, -100%)' }}
        >
          <span className="text-accent font-mono">{tooltip.device}</span>
          <span className="text-gray-400 ml-2">{tooltip.text}</span>
        </div>
      )}

      {/* Time axis — repères below the bars */}
      <div className="flex items-center gap-2">
        <span className="w-20"></span>
        <div className="flex-1 relative h-4">
          {(() => {
            // Generate 5 evenly-spaced time markers
            const markers = [];
            for (let i = 0; i <= 4; i++) {
              const pct = (i / 4) * 100;
              const time = new Date(startTime.getTime() + (i / 4) * totalMs);
              // Show date if period > 24h, otherwise just time
              const label = rangeMs > 86400000
                ? `${time.toLocaleDateString('en-CA', { month: '2-digit', day: '2-digit' })} ${time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`
                : time.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
              markers.push(
                <span
                  key={i}
                  className="absolute text-[8px] text-gray-600 font-mono"
                  style={{ left: `${pct}%`, transform: i === 4 ? 'translateX(-100%)' : i === 0 ? 'none' : 'translateX(-50%)' }}
                >
                  {label}
                </span>
              );
            }
            return markers;
          })()}
        </div>
      </div>

      {/* Legend — UX-6: Added attack (red) */}
      <div className="flex gap-4 mt-1 text-[9px] text-gray-600">
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-sm bg-success/40"></span> Online
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-sm bg-gray-500"></span> Offline
        </span>
        <span className="flex items-center gap-1">
          <span className="w-2 h-2 rounded-sm bg-danger/60"></span> Under Attack
        </span>
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Tab 2 — SLA (real downtime)           */
/* ═══════════════════════════════════════ */

function TabSLA() {
  const [attackDowntime, setAttackDowntime] = useState({});
  const [offlineDowntime, setOfflineDowntime] = useState({});
  const [period, setPeriod] = useState('24h');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    Promise.all([
      getDowntime(period).catch(() => ({})),
      getOfflineDowntime(period).catch(() => ({})),
    ]).then(([attack, offline]) => {
      setAttackDowntime(attack);
      setOfflineDowntime(offline);
    }).finally(() => setLoading(false));
  }, [period]);

  const totalSeconds = period === '1h' ? 3600 : period === '24h' ? 86400 : period === '7d' ? 604800 : 2592000;

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <select
          value={period}
          onChange={(e) => setPeriod(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300"
        >
          <option value="1h">Last hour</option>
          <option value="24h">Last 24h</option>
          <option value="7d">Last week</option>
          <option value="30d">Last 30 days</option>
        </select>
      </div>

      <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-600">Loading...</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-700">
                <th className="text-left p-3 text-gray-500 font-medium">Device</th>
                <th className="text-left p-3 text-gray-500 font-medium">Attack Downtime</th>
                <th className="text-left p-3 text-gray-500 font-medium">Offline Downtime</th>
                <th className="text-left p-3 text-gray-500 font-medium">Total Downtime</th>
                <th className="text-left p-3 text-gray-500 font-medium">Availability</th>
                <th className="text-left p-3 text-gray-500 font-medium">Status</th>
              </tr>
            </thead>
            <tbody>
              {DEVICES.map((device) => {
                const attackDt = attackDowntime[device] || 0;
                const offlineDt = offlineDowntime[device] || 0;
                const totalDt = attackDt + offlineDt;
                const availability = ((totalSeconds - totalDt) / totalSeconds * 100).toFixed(2);
                return (
                  <tr key={device} className="border-b border-gray-800">
                    <td className="p-3 text-gray-200">{device}</td>
                    <td className="p-3 font-mono text-danger text-xs">
                      {attackDt > 0 ? formatDuration(attackDt) : '—'}
                    </td>
                    <td className="p-3 font-mono text-gray-400 text-xs">
                      {offlineDt > 0 ? formatDuration(offlineDt) : '—'}
                    </td>
                    <td className="p-3 font-mono text-gray-300">
                      {totalDt > 0 ? formatDuration(totalDt) : '0s'}
                    </td>
                    <td className="p-3 font-mono text-gray-300">{availability}%</td>
                    <td className="p-3">
                      <span className={`text-xs px-2 py-1 rounded ${
                        parseFloat(availability) >= 99.9 ? 'bg-success/10 text-success' :
                        parseFloat(availability) >= 99 ? 'bg-warning/10 text-warning' :
                        'bg-danger/10 text-danger'
                      }`}>
                        {parseFloat(availability) >= 99.9 ? 'HEALTHY' :
                         parseFloat(availability) >= 99 ? 'WARNING' : 'CRITICAL'}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </div>

      {/* SLA Explanation */}
      <div className="text-xs text-gray-600 px-1">
        Availability = (Total Time − Attack Downtime − Offline Downtime) / Total Time × 100
      </div>
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Helpers                               */
/* ═══════════════════════════════════════ */

function SummaryCard({ label, value, color }) {
  const colorMap = {
    danger: 'text-danger',
    warning: 'text-warning',
    success: 'text-success',
    accent: 'text-accent',
  };
  return (
    <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className={`text-2xl font-bold font-mono ${colorMap[color]}`}>{value}</p>
    </div>
  );
}

function formatDuration(seconds) {
  if (seconds < 60) return `${seconds}s`;
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ${seconds % 60}s`;
  return `${Math.floor(seconds / 3600)}h ${Math.floor((seconds % 3600) / 60)}m`;
}

export default Home;
