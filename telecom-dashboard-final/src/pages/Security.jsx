import React, { useState, useEffect } from 'react';
import { getAttackHistory, getSessionStats, getAuditLog, getLatestPredictions, getActiveSessions, executeProtection } from '../services/api';

function Security({ onDeviceClick, onAttackClick, latestPredictions }) {
  const [activeTab, setActiveTab] = useState(0);
  const tabs = ['🔴 Live', '📜 History', '📋 Activity Log'];

  return (
    <div>
      <div className="flex gap-4 mb-6">
        {tabs.map((tab, index) => (
          <button
            key={tab}
            onClick={() => setActiveTab(index)}
            className={`px-4 py-2 rounded-lg text-sm transition-colors ${
              activeTab === index ? 'bg-accent/10 text-accent' : 'text-gray-400 hover:text-gray-200'
            }`}
          >
            {tab}
          </button>
        ))}
      </div>

      {activeTab === 0 && <TabLive onDeviceClick={onDeviceClick} latestPredictions={latestPredictions} />}
      {activeTab === 1 && <TabHistory onAttackClick={onAttackClick} />}
      {activeTab === 2 && <TabActivityLog />}
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Tab 1 — Live (from WebSocket)         */
/* ═══════════════════════════════════════ */

function TabLive({ onDeviceClick, latestPredictions }) {
  const [restPredictions, setRestPredictions] = useState({});
  const [activeSessions, setActiveSessions] = useState([]);
  const [mitigating, setMitigating] = useState(null);

  // Fetch predictions from REST API on mount
  useEffect(() => {
    getLatestPredictions()
      .then((list) => {
        const map = {};
        for (const pred of list) {
          const key = `${pred.deviceName}::${pred.interfaceName}`;
          if (!map[key] || pred.id > (map[key].id || 0)) {
            map[key] = pred;
          }
        }
        setRestPredictions(map);
      })
      .catch(() => {});
  }, []);

  // Merge: WebSocket predictions override REST predictions
  const merged = { ...restPredictions, ...(latestPredictions || {}) };

  // All monitored devices
  const ALL_DEVICES = ['edge-router', 'core-router', 'web-server', 'dns-server', 'ftp-server', 'db-server'];

  // Build ONE row per device (not per interface)
  // Take the worst status across all interfaces
  const deviceMap = {};

  for (const [key, pred] of Object.entries(merged)) {
    const [device, iface] = key.split('::');
    if (!ALL_DEVICES.includes(device)) continue;

    let status = !pred.predictedAttack || pred.predictedAttack === 'normal'
      ? 'normal'
      : pred.predictedAttack === 'transit' ? 'transit' : 'attack';

    // Cross-reference with active sessions — both attack AND transit
    // (stale predictions remain in DB after attack ends; verify session is active)
    let activeSession = null;  // 🆕 keep the matching session for status flip below
    if (status === 'attack' || status === 'transit') {
      activeSession = activeSessions.find(s =>
        s.deviceName === device && s.interfaceName === iface
        && s.attackType === pred.predictedAttack && s.status === 'ACTIVE'
      );
      if (!activeSession) {
        status = 'normal';
      }
    }

    // 🆕 If operator already clicked Mitigate on this attack (mitigatedAt set
    // but status still ACTIVE because the attack didn't actually stop yet),
    // flip the row to 'mitigating' — orange instead of red.
    if (status === 'attack' && activeSession && activeSession.mitigatedAt) {
      status = 'mitigating';
    }

    // Keep worst status per device: attack > mitigating > transit > normal
    const priority = { attack: 0, mitigating: 1, transit: 2, normal: 3 };
    if (!deviceMap[device] || priority[status] < priority[deviceMap[device].status]) {
      // If we flipped to normal, don't carry over the stale attack's confidence
      const cleanPred = status === 'normal'
        ? { predictedAttack: 'normal', confidence: null, topFeatures: '[]' }
        : pred;
      deviceMap[device] = {
        device,
        iface,
        status,
        ...cleanPred,
      };
    }
  }

  // Add missing devices that have no predictions
  for (const device of ALL_DEVICES) {
    if (!deviceMap[device]) {
      deviceMap[device] = {
        device, iface: '—', status: 'normal',
        predictedAttack: 'normal', confidence: null,
      };
    }
  }

  // 🆕 Add rows for active non-AI sessions (Trigger Rules, Interface Down, Device Offline)
  // Each gets a row in the Live table with appropriate styling.
  //
  //   TRIGGER_RULE CRITICAL → 'attack'  (red)
  //   TRIGGER_RULE WARNING  → 'warning' (yellow)
  //   INTERFACE_DOWN        → 'offline' (grey) — not a warning, just "not working"
  //   DEVICE_OFFLINE        → 'offline' (grey)
  const triggerRows = activeSessions
    .filter(s => s.sessionSource === 'TRIGGER_RULE'
              || s.sessionSource === 'INTERFACE_DOWN'
              || s.sessionSource === 'DEVICE_OFFLINE')
    .map(s => {
      let status;
      if (s.sessionSource === 'INTERFACE_DOWN' || s.sessionSource === 'DEVICE_OFFLINE') {
        status = 'offline';
      } else if (s.severity === 'CRITICAL') {
        status = 'attack';
      } else {
        status = 'warning';
      }
      // 🆕 If a CRITICAL trigger was already mitigated, flip to 'mitigating'
      // (orange) — same visual cue as AI attacks. Offline is left grey.
      if (status === 'attack' && s.mitigatedAt) {
        status = 'mitigating';
      }
      return {
        device: s.deviceName,
        iface: s.interfaceName || '-',
        status,
        predictedAttack: s.attackType,
        confidence: s.avgConfidence,
        topFeatures: '[]',
        sessionSource: s.sessionSource,
        _triggerSession: s,
        _isTrigger: true,
      };
    });

  // Sort: attacks first, then mitigating, then warnings, then offline, then transit, then normal
  const rows = [
    ...triggerRows,
    ...Object.values(deviceMap),
  ].sort((a, b) => {
    const order = { attack: 0, mitigating: 1, warning: 2, offline: 3, transit: 4, normal: 5 };
    return (order[a.status] ?? 5) - (order[b.status] ?? 5);
  });

  // 'mitigating' = ACTIVE attack but operator already clicked Mitigate
  // (mitigatedAt is set, but status is still ACTIVE because the attack
  //  hasn't actually stopped yet). Visually orange — "under control,
  //  monitoring." Distinct from the red of an unhandled attack.
  const statusColor = { attack: 'text-danger', mitigating: 'text-warning', warning: 'text-warning', offline: 'text-gray-400', transit: 'text-warning', normal: 'text-success' };
  const statusDot   = { attack: 'bg-danger',   mitigating: 'bg-warning',   warning: 'bg-warning',   offline: 'bg-gray-500',   transit: 'bg-warning', normal: 'bg-success' };

  // Refresh active sessions when predictions change
  useEffect(() => {
    getActiveSessions().then(setActiveSessions).catch(() => {});
  }, [latestPredictions]);

  const findSession = (device, iface, attack) => {
    return activeSessions.find(s =>
      s.deviceName === device && s.interfaceName === iface && s.attackType === attack
    );
  };

  const handleMitigate = async (e, session) => {
    e.stopPropagation();
    if (!session) {
      console.warn('❌ Mitigate: no session found');
      return;
    }
    console.log('🛡️ Mitigate clicked:', {
      sessionId: session.id,
      attackType: session.attackType,
      device: session.deviceName,
      interface: session.interfaceName,
      status: session.status,
    });
    setMitigating(session.id);
    try {
      // Playbook system auto-picks the highest-priority enabled playbook
      // for this session's trigger. No more hardcoded action mapping.
      console.log('🛡️ Triggering mitigation for session:', session.id, session.attackType);
      const result = await executeProtection(session.id);
      console.log('🛡️ Mitigation result:', result);

      // Backend returns { status, message, playbook?, ... }
      if (result?.status === 'no_match') {
        alert(`No playbook matches "${session.attackType}". Add one in Settings → Response Playbooks.`);
      } else if (result?.status === 'executed') {
        // Optional: short success toast — keeping silent for now to avoid noise.
        // Audit log shows the full execution detail.
      }

      getActiveSessions().then(setActiveSessions).catch(() => {});
    } catch (err) {
      console.error('❌ Mitigation failed:', err);
      alert('Mitigation failed: ' + (err.message || 'unknown error'));
    }
    setMitigating(null);
  };

  if (rows.length === 0) {
    return (
      <div className="bg-bg-card rounded-xl p-12 border border-gray-800 text-center text-gray-600">
        <p>Waiting for predictions...</p>
        <p className="text-xs text-gray-700 mt-2">Make sure AI Engine is running and Live Prediction is started.</p>
      </div>
    );
  }

  return (
    <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-700">
            <th className="text-left p-3 text-gray-500 font-medium">Device</th>
            <th className="text-left p-3 text-gray-500 font-medium">Interface</th>
            <th className="text-left p-3 text-gray-500 font-medium">Status</th>
            <th className="text-left p-3 text-gray-500 font-medium">Attack</th>
            <th className="text-left p-3 text-gray-500 font-medium">Confidence</th>
            <th className="text-left p-3 text-gray-500 font-medium">Protection</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row, i) => {
            // For AI attacks: look up the session via the active-sessions list.
            // 'mitigating' is also an attack-state — needs the session lookup too.
            const aiSession = (row.status === 'attack' || row.status === 'mitigating') && !row._isTrigger
              ? findSession(row.device, row.iface, row.predictedAttack) : null;

            const triggerSession = row._triggerSession;

            // Unified session reference — whichever applies on this row.
            // Used by the Protection cell (Mitigate button / Mitigated badge)
            // for BOTH AI attacks and CRITICAL trigger rules.
            const session = aiSession || triggerSession || null;

            // Display-friendly attack name
            let displayAttack = row.predictedAttack || '—';
            if (row._isTrigger && row.sessionSource === 'TRIGGER_RULE') {
              const m = displayAttack.match(/^trigger:(.+?)([><]=?)(.+)$/);
              if (m) displayAttack = `Rule: ${m[1]} ${m[2]} ${m[3]}`;
            } else if (row._isTrigger && row.sessionSource === 'INTERFACE_DOWN') {
              displayAttack = 'Interface Down';
            } else if (row._isTrigger && row.sessionSource === 'DEVICE_OFFLINE') {
              displayAttack = 'Device Offline';
            }

            // Confidence display:
            //   AI attacks     → percentage (e.g., "87%") — the model's certainty
            //   Trigger Rules  → "—" (the rule fired deterministically, no probability)
            //   Interface Down → "—" (hard fact, not a prediction)
            //   Device Offline → "—" (hard fact, not a prediction)
            const isAiAttack = !row._isTrigger && row.status === 'attack';
            let confDisplay = '—';
            if (isAiAttack && row.confidence != null) {
              confDisplay = `${(row.confidence * 100).toFixed(0)}%`;
            }

            return (
              <tr
                key={i}
                onClick={() => onDeviceClick(
                  { name: row.device, type: row.device.includes('router') ? 'router' : 'server' },
                  row.status === 'attack' ? 1 : 0
                )}
                className={`border-b border-gray-800 cursor-pointer transition-colors hover:bg-bg-hover ${
                  row.status === 'attack' ? 'bg-danger/5' :
                  row.status === 'mitigating' ? 'bg-warning/10' :
                  row.status === 'warning' ? 'bg-warning/5' :
                  row.status === 'offline' ? 'bg-gray-500/5' : ''
                }`}
              >
                <td className="p-3 text-gray-200">{row.device}</td>
                <td className="p-3 font-mono text-gray-400">{row.iface}</td>
                <td className="p-3">
                  <span className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${statusDot[row.status]}`}></span>
                    <span className={statusColor[row.status]}>{row.status.toUpperCase()}</span>
                  </span>
                </td>
                <td className={`p-3 font-mono ${statusColor[row.status]}`}>
                  {displayAttack}
                </td>
                <td className="p-3 font-mono text-gray-300">
                  {confDisplay}
                </td>
                <td className="p-3">
                  {/* Four states for the Protection cell:
                       1. Session ACTIVE and not yet mitigated  → 🛡️ Mitigate button
                       2. Session ACTIVE but already mitigated  → ⏳ Mitigated (monitoring)
                            — operator clicked Mitigate, but the attack is still
                              live (AI keeps detecting it). Playbook ran; we're
                              waiting for the attack to actually stop.
                       3. Session MITIGATED (closed by scheduler)→ ✅ Mitigated
                       4. Anything else (transit, ended naturally, etc.) → "—" */}
                  {session && session.status === 'ACTIVE' && !session.mitigatedAt ? (
                    <button
                      onClick={(e) => handleMitigate(e, session)}
                      disabled={mitigating === session.id}
                      className="px-2 py-1 text-xs rounded bg-accent/10 text-accent
                               hover:bg-accent/20 transition-colors disabled:opacity-50"
                    >
                      {mitigating === session.id ? '...' : '🛡️ Mitigate'}
                    </button>
                  ) : session && session.status === 'ACTIVE' && session.mitigatedAt ? (
                    <span className="text-xs text-warning"
                          title="Mitigation playbook already executed. Waiting for the attack to actually stop.">
                      ⏳ Mitigated (monitoring)
                    </span>
                  ) : session?.status === 'MITIGATED' || session?.mitigatedAt ? (
                    <span className="text-xs text-success">✅ Mitigated</span>
                  ) : row.status === 'attack' || row.status === 'mitigating' ? (
                    <span className="text-xs text-gray-600">—</span>
                  ) : null}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Tab 2 — History (from AttackSession)  */
/*  Has two sub-tabs:                      */
/*    - Attacks → AI sessions only         */
/*    - Alerts  → Triggers + Interface     */
/*                Down + Device Offline    */
/* ═══════════════════════════════════════ */

function TabHistory({ onAttackClick }) {
  // 'attacks' → AI only (sessionSource == 'AI' or null)
  // 'alerts'  → Trigger Rules + Interface Down + Device Offline
  const [subTab, setSubTab] = useState('attacks');

  const [sessions, setSessions] = useState([]);
  const [stats, setStats] = useState({});
  const [deviceFilter, setDeviceFilter] = useState('');
  const [periodFilter, setPeriodFilter] = useState('');
  const [loading, setLoading] = useState(true);

  const fetchData = () => {
    setLoading(true);
    Promise.all([
      // getAttackHistory returns ALL historical sessions — we filter client-side
      // by sessionSource to split them into Attacks vs Alerts.
      getAttackHistory(deviceFilter || null, periodFilter || null),
      getSessionStats(),
    ]).then(([hist, st]) => {
      setSessions(hist);
      setStats(st);
    }).catch(() => {})
      .finally(() => setLoading(false));
  };

  useEffect(() => { fetchData(); }, [deviceFilter, periodFilter]);

  // Split sessions by origin
  const attackSessions = sessions.filter(s =>
    !s.sessionSource || s.sessionSource === 'AI'
  );
  const alertSessions = sessions.filter(s =>
    s.sessionSource === 'TRIGGER_RULE'
    || s.sessionSource === 'INTERFACE_DOWN'
    || s.sessionSource === 'DEVICE_OFFLINE'
  );

  const formatDateTime = (isoStr) => {
    if (!isoStr) return '—';
    const d = new Date(isoStr);
    return d.toLocaleDateString('en-CA') + ' ' + d.toLocaleTimeString();
  };

  // Build a human-readable label + type-icon pair for the Alerts table
  // Each kind of alert gets its own display treatment so the table is clear.
  const alertLabel = (s) => {
    if (s.sessionSource === 'INTERFACE_DOWN') {
      return { icon: '🔌', label: 'Interface Down', colorClass: 'text-gray-300' };
    }
    if (s.sessionSource === 'DEVICE_OFFLINE') {
      return { icon: '⚫', label: 'Device Offline', colorClass: 'text-gray-300' };
    }
    if (s.sessionSource === 'TRIGGER_RULE') {
      const m = s.attackType.match(/^trigger:(.+?)([><]=?)(.+)$/);
      const txt = m ? `${m[1]} ${m[2]} ${m[3]}` : s.attackType;
      const isCrit = s.severity === 'CRITICAL';
      return {
        icon: isCrit ? '🚨' : '⚠️',
        label: `Rule: ${txt}`,
        colorClass: isCrit ? 'text-danger' : 'text-warning',
      };
    }
    return { icon: '•', label: s.attackType, colorClass: 'text-gray-400' };
  };

  return (
    <div className="space-y-4">
      {/* Stats Cards — show counts for the current sub-tab */}
      <div className="grid grid-cols-4 gap-3">
        <StatCard label="Total Attacks" value={
          (stats.totalEnded || 0) + (stats.totalMitigated || 0)
        } />
        <StatCard label="Most Targeted" value={stats.mostTargeted || '—'} />
        <StatCard label="Most Common" value={stats.mostCommon || '—'} />
        <StatCard label="Active Now" value={stats.totalActive || 0} />
      </div>

      {/* Sub-tab switcher — Attacks / Alerts */}
      <div className="flex gap-1 border-b border-gray-800">
        <button
          onClick={() => setSubTab('attacks')}
          className={`px-4 py-2 text-sm transition-colors border-b-2 ${
            subTab === 'attacks'
              ? 'border-danger text-danger'
              : 'border-transparent text-gray-500 hover:text-gray-300'
          }`}
        >
          🚨 Attacks ({attackSessions.length})
        </button>
        <button
          onClick={() => setSubTab('alerts')}
          className={`px-4 py-2 text-sm transition-colors border-b-2 ${
            subTab === 'alerts'
              ? 'border-warning text-warning'
              : 'border-transparent text-gray-500 hover:text-gray-300'
          }`}
        >
          ⚠️ Alerts ({alertSessions.length})
        </button>
      </div>

      {/* Filters */}
      <div className="flex gap-3">
        <select
          value={deviceFilter}
          onChange={(e) => setDeviceFilter(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300"
        >
          <option value="">All Devices</option>
          {['web-server','dns-server','ftp-server','db-server','edge-router','core-router'].map(d => (
            <option key={d} value={d}>{d}</option>
          ))}
        </select>
        <select
          value={periodFilter}
          onChange={(e) => setPeriodFilter(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300"
        >
          <option value="">All time</option>
          <option value="1h">Last hour</option>
          <option value="24h">Last 24h</option>
          <option value="7d">Last week</option>
        </select>
        <button onClick={fetchData}
          className="px-3 py-2 bg-bg-primary border border-gray-700 rounded-lg text-sm text-gray-400
                     hover:text-accent hover:border-accent/30 transition-colors">
          🔄 Refresh
        </button>
      </div>

      {/* ═══ ATTACKS TABLE (AI only) ═══ */}
      {subTab === 'attacks' && (
        <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
          {loading ? (
            <div className="p-8 text-center text-gray-600">Loading...</div>
          ) : attackSessions.length === 0 ? (
            <div className="p-8 text-center text-gray-600">No attacks recorded yet</div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-700">
                  <th className="text-left p-3 text-gray-500 font-medium">Status</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Attack</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Device</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Interface</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Start</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Duration</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Avg Conf</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Protection</th>
                </tr>
              </thead>
              <tbody>
                {attackSessions.map((s) => (
                  <tr
                    key={s.id}
                    onClick={() => onAttackClick(s)}
                    className="border-b border-gray-800 hover:bg-bg-hover cursor-pointer transition-colors"
                  >
                    <td className="p-3">
                      <span className={`text-xs px-2 py-1 rounded ${
                        s.status === 'MITIGATED' ? 'bg-success/10 text-success' : 'bg-gray-700 text-gray-300'
                      }`}>
                        {s.status}
                      </span>
                    </td>
                    <td className="p-3 font-mono text-danger">{s.attackType}</td>
                    <td className="p-3 text-gray-200">{s.deviceName}</td>
                    <td className="p-3 font-mono text-gray-400">{s.interfaceName}</td>
                    <td className="p-3 text-gray-400 text-xs">{formatDateTime(s.startedAt)}</td>
                    <td className="p-3 font-mono text-gray-300">{s.durationFormatted || '—'}</td>
                    <td className="p-3 font-mono text-gray-300">
                      {s.avgConfidence ? `${(s.avgConfidence * 100).toFixed(0)}%` : '—'}
                    </td>
                    <td className="p-3 text-gray-400">
                      {/* Mitigation column. Show "✅ Mitigated" for MITIGATED status,
                          or the legacy protectionAction string if older row, or "—". */}
                      {s.status === 'MITIGATED' || s.mitigatedAt
                        ? <span className="text-success">✅ Mitigated</span>
                        : (s.protectionAction || '—')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {/* ═══ ALERTS TABLE (Trigger Rules + Interface Down + Device Offline) ═══ */}
      {subTab === 'alerts' && (
        <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
          {loading ? (
            <div className="p-8 text-center text-gray-600">Loading...</div>
          ) : alertSessions.length === 0 ? (
            <div className="p-8 text-center text-gray-600">No alerts recorded yet</div>
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-700">
                  <th className="text-left p-3 text-gray-500 font-medium">Type</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Event</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Device</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Interface</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Severity</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Start</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Duration</th>
                  <th className="text-left p-3 text-gray-500 font-medium">Status</th>
                </tr>
              </thead>
              <tbody>
                {alertSessions.map((s) => {
                  const { icon, label, colorClass } = alertLabel(s);
                  return (
                    <tr
                      key={s.id}
                      onClick={() => onAttackClick(s)}
                      className="border-b border-gray-800 hover:bg-bg-hover cursor-pointer transition-colors"
                    >
                      <td className="p-3">
                        <span className="text-lg">{icon}</span>
                      </td>
                      <td className={`p-3 font-mono ${colorClass}`}>{label}</td>
                      <td className="p-3 text-gray-200">{s.deviceName}</td>
                      <td className="p-3 font-mono text-gray-400">
                        {s.interfaceName && s.interfaceName !== '-' ? s.interfaceName : '—'}
                      </td>
                      <td className="p-3">
                        <span className={`text-xs px-2 py-0.5 rounded ${
                          s.severity === 'CRITICAL' ? 'bg-danger/10 text-danger' :
                          s.severity === 'WARNING'  ? 'bg-warning/10 text-warning' :
                          'bg-gray-700 text-gray-400'
                        }`}>
                          {s.severity || 'INFO'}
                        </span>
                      </td>
                      <td className="p-3 text-gray-400 text-xs">{formatDateTime(s.startedAt)}</td>
                      <td className="p-3 font-mono text-gray-300">{s.durationFormatted || '—'}</td>
                      <td className="p-3">
                        <span className={`text-xs px-2 py-1 rounded ${
                          s.status === 'ACTIVE' ? 'bg-warning/10 text-warning' : 'bg-gray-700 text-gray-400'
                        }`}>
                          {s.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
      )}
    </div>
  );
}

/* ═══════════════════════════════════════ */
/*  Tab 3 — Activity Log (from AuditLog) */
/* ═══════════════════════════════════════ */

function TabActivityLog() {
  const [events, setEvents] = useState([]);
  const [typeFilter, setTypeFilter] = useState('');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    getAuditLog(typeFilter || null, null)
      .then(setEvents)
      .catch(() => setEvents([]))
      .finally(() => setLoading(false));
  }, [typeFilter]);

  const typeIcon = {
    PROTECTION: '🛡️',
    ESCALATION: '⚠️',
    ACKNOWLEDGE: '✅',
    TERMINAL: '🖥️',
    AI: '🤖',
    SETTINGS: '⚙️',
    AUTH: '🔑',
    DEVICE_CONTROL: '🔌',
  };

  return (
    <div className="space-y-4">
      <div className="flex gap-3">
        <select
          value={typeFilter}
          onChange={(e) => setTypeFilter(e.target.value)}
          className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300"
        >
          <option value="">All Types</option>
          {Object.keys(typeIcon).map(t => (
            <option key={t} value={t}>{typeIcon[t]} {t}</option>
          ))}
        </select>
      </div>

      <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
        {loading ? (
          <div className="p-8 text-center text-gray-600">Loading...</div>
        ) : events.length === 0 ? (
          <div className="p-8 text-center text-gray-600">No events recorded</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-700">
                <th className="text-left p-3 text-gray-500 font-medium">Time</th>
                <th className="text-left p-3 text-gray-500 font-medium">User</th>
                <th className="text-left p-3 text-gray-500 font-medium">Type</th>
                <th className="text-left p-3 text-gray-500 font-medium">Description</th>
              </tr>
            </thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id} className="border-b border-gray-800">
                  <td className="p-3 font-mono text-gray-400 text-xs">
                    {e.timestamp ? new Date(e.timestamp).toLocaleString() : '—'}
                  </td>
                  <td className="p-3 text-xs text-accent">{e.username || '—'}</td>
                  <td className="p-3">
                    <span className="text-xs">{typeIcon[e.type] || '🔔'} {e.type}</span>
                  </td>
                  <td className="p-3 text-gray-300">{e.description}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function StatCard({ label, value }) {
  return (
    <div className="bg-bg-card rounded-lg p-3 border border-gray-800">
      <p className="text-xs text-gray-500">{label}</p>
      <p className="text-sm font-semibold text-gray-200 mt-1">{value}</p>
    </div>
  );
}

export default Security;