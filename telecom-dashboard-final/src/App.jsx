import React, { useState, useEffect, useCallback, useRef } from 'react';
import { BrowserRouter as Router, Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Header from './components/Header';
import SidePanel from './components/SidePanel';
import AttackDetailPanel from './components/AttackDetailPanel';
import Home from './pages/Home';
import Monitoring from './pages/Monitoring';
import Security from './pages/Security';
import Terminal from './pages/Terminal';
import Settings from './pages/Settings';
import Login from './pages/Login';
import { connectWebSocket, subscribe } from './services/websocket';
import { getOfflineDevices, getActiveSessions } from './services/api';
import { MITRE_MAP, ATTACK_INFO } from './data/mitre';

function App() {
  // ── Auth state ────────────────────────────────────────────
  const [user, setUser] = useState(() => {
    const saved = localStorage.getItem('user');
    return saved ? JSON.parse(saved) : null;
  });
  const [token, setToken] = useState(() => localStorage.getItem('token'));

  const handleLogin = (data) => {
    // Update React state so App re-renders immediately without a page refresh.
    // (Previously token was read only once from localStorage — Login wouldn't trigger re-render.)
    setToken(data.token);
    setUser({ username: data.username, role: data.role, fullName: data.fullName });
  };

  const handleLogout = () => {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    setToken(null);
    setUser(null);
  };

  // ── If not logged in, show Login page ─────────────────────
  if (!user || !token) {
    return <Login onLogin={handleLogin} />;
  }

  return <AuthenticatedApp user={user} onLogout={handleLogout} />;
}

function AuthenticatedApp({ user, onLogout }) {
  // ── Side Panel state ──────────────────────────────────────
  const [selectedDevice, setSelectedDevice] = useState(null);
  const [sidePanelOpen, setSidePanelOpen] = useState(false);
  const [sidePanelDefaultTab, setSidePanelDefaultTab] = useState(0);

  // ── Attack Detail Panel state ─────────────────────────────
  const [selectedAttackSession, setSelectedAttackSession] = useState(null);

  // ── Real-time data (shared across pages) ──────────────────
  const [latestMetrics, setLatestMetrics] = useState({});
  const [latestPredictions, setLatestPredictions] = useState({});
  const [notifications, setNotifications] = useState([]);

  // 🆕 Active sessions (AI attacks + Trigger Rules + Interface Down) — shared
  const [activeSessions, setActiveSessions] = useState([]);

  // ── Toast alerts (persistent attack banners) ──────────────
  const [toastAlerts, setToastAlerts] = useState([]);

  // ── Device last-seen timestamps for offline detection ─────
  const deviceLastSeen = useRef({});

  // ── SEC-2: Fetch offline devices on mount ─────────────────
  useEffect(() => {
    getOfflineDevices().then(offlineList => {
      if (offlineList && offlineList.length > 0) {
        // Mark them as "long ago" so they appear offline immediately
        for (const device of offlineList) {
          deviceLastSeen.current[device] = 1; // epoch = very old
        }
      }
    }).catch(() => {});
  }, []);

  // ── Connect WebSocket on mount ────────────────────────────
  useEffect(() => {
    connectWebSocket();

    subscribe('/topic/metrics', (metric) => {
      setLatestMetrics((prev) => ({
        ...prev,
        [metric.deviceName]: metric,
      }));
      deviceLastSeen.current[metric.deviceName] = Date.now();
    });

    subscribe('/topic/predictions', (pred) => {
      const key = `${pred.deviceName}::${pred.interfaceName}`;
      setLatestPredictions((prev) => ({
        ...prev,
        [key]: pred,
      }));
    });

    subscribe('/topic/notifications', (notif) => {
      setNotifications((prev) => [notif, ...prev].slice(0, 50));

      // Any NEW_ATTACK or ATTACK_ENDED should refresh activeSessions
      // (covers AI attacks + TRIGGER_RULE + INTERFACE_DOWN since all share this channel)
      if (notif.type === 'NEW_ATTACK' || notif.type === 'ATTACK_ENDED') {
        getActiveSessions().then(setActiveSessions).catch(() => {});
      }

      // Add persistent toast for NEW_ATTACK — handles all sources:
      //   AI             → red (CRITICAL), full MITRE
      //   TRIGGER_RULE   → yellow (WARNING) or red (CRITICAL)
      //   INTERFACE_DOWN → grey (WARNING)
      //   DEVICE_OFFLINE → grey (CRITICAL — full device loss)
      if (notif.type === 'NEW_ATTACK') {
        const source = notif.source || 'AI';
        const isNonAi = source !== 'AI';
        // Severity from payload; default: AI/DEVICE_OFFLINE=CRITICAL, others=WARNING
        const defaultSev = (source === 'DEVICE_OFFLINE' || source === 'AI') ? 'CRITICAL' : 'WARNING';
        const severity = notif.severity || defaultSev;

        const toastId = `${notif.deviceName}::${notif.interfaceName}::${notif.attackType}`;
        setToastAlerts((prev) => {
          if (prev.find(t => t.id === toastId)) return prev;
          return [...prev, {
            id: toastId,
            type: notif.attackType,
            device: notif.deviceName,
            iface: notif.interfaceName,
            confidence: notif.confidence,
            timestamp: notif.timestamp || new Date().toISOString(),
            source,              // 'AI' | 'TRIGGER_RULE' | 'INTERFACE_DOWN' | 'DEVICE_OFFLINE'
            severity,
            ruleName: notif.ruleName,
            actualValue: notif.actualValue,
          }];
        });
      }

      // Auto-remove toast when attack ends
      if (notif.type === 'ATTACK_ENDED') {
        const toastId = `${notif.deviceName}::${notif.interfaceName}::${notif.attackType}`;
        setToastAlerts((prev) => prev.filter(t => t.id !== toastId));

        // BUG-6 FIX: Clear the prediction for this interface so Live tab updates
        const predKey = `${notif.deviceName}::${notif.interfaceName}`;
        setLatestPredictions((prev) => {
          const next = { ...prev };
          if (next[predKey]) {
            next[predKey] = {
              ...next[predKey],
              predictedAttack: 'normal',
              confidence: 1.0,
              topFeatures: '[]',
            };
          }
          return next;
        });
      }
    });

    subscribe('/topic/device-status', (event) => {
      if (event.status === 'OFFLINE') {
        deviceLastSeen.current[event.deviceName] = 1; // force offline
        // Clear metrics so Monitoring, SidePanel etc. show 0
        setLatestMetrics((prev) => {
          const next = { ...prev };
          delete next[event.deviceName];
          return next;
        });
      } else if (event.status === 'ONLINE') {
        deviceLastSeen.current[event.deviceName] = Date.now();
      }
    });
  }, []);

  // 🆕 Fetch activeSessions on mount + poll every 10s as safety net
  // (WebSocket notification push is primary; polling is fallback)
  useEffect(() => {
    getActiveSessions().then(setActiveSessions).catch(() => {});
    const interval = setInterval(() => {
      getActiveSessions().then(setActiveSessions).catch(() => {});
    }, 10000);
    return () => clearInterval(interval);
  }, []);

  // ── Periodic check: clear metrics for devices that stopped sending ──
  useEffect(() => {
    const OFFLINE_THRESHOLD = 45000; // same as Sidebar
    const interval = setInterval(() => {
      const now = Date.now();
      setLatestMetrics((prev) => {
        let changed = false;
        const next = { ...prev };
        for (const deviceName of Object.keys(next)) {
          const lastSeen = deviceLastSeen.current[deviceName];
          if (lastSeen && (lastSeen === 1 || (now - lastSeen) > OFFLINE_THRESHOLD)) {
            delete next[deviceName];
            changed = true;
          }
        }
        return changed ? next : prev;
      });
    }, 10000); // check every 10s
    return () => clearInterval(interval);
  }, []);

  const dismissToast = useCallback((toastId) => {
    setToastAlerts((prev) => prev.filter(t => t.id !== toastId));
  }, []);

  const openSidePanel = useCallback((device, defaultTab = 0) => {
    setSelectedDevice(device);
    setSidePanelDefaultTab(defaultTab);
    setSidePanelOpen(true);
  }, []);

  const closeSidePanel = useCallback(() => {
    setSidePanelOpen(false);
    setSelectedDevice(null);
  }, []);

  const openAttackDetail = useCallback((session) => {
    setSelectedAttackSession(session);
  }, []);

  const closeAttackDetail = useCallback(() => {
    setSelectedAttackSession(null);
  }, []);

  const clearNotification = (index) => {
    setNotifications((prev) => prev.filter((_, i) => i !== index));
  };

  const clearAllNotifications = () => {
    setNotifications([]);
    setToastAlerts([]);
  };

  const isAdmin = user?.role === 'ADMIN';

  // ── Helper: format attack name ────────────────────────────
  const formatAttackName = (type) => {
    const names = {
      synflood: 'SYN Flood', dos: 'DoS', ddos: 'DDoS',
      udp_flood: 'UDP Flood', http_flood: 'HTTP Flood', dns_flood: 'DNS Flood',
      portscan: 'Port Scan', ping_of_death: 'Ping of Death', rst_flood: 'RST Flood',
      router_dos: 'Router DoS', router_synflood: 'Router SYN Flood',
      slowloris: 'Slowloris',
    };
    return names[type] || type?.toUpperCase() || 'Unknown';
  };

  return (
    <Router>
      <div className="flex h-screen bg-bg-primary overflow-hidden">
        <Sidebar
          onDeviceClick={(device) => openSidePanel(device, 0)}
          latestMetrics={latestMetrics}
          latestPredictions={latestPredictions}
          deviceLastSeen={deviceLastSeen}
          activeSessions={activeSessions}
        />

        <div className="flex-1 flex flex-col overflow-hidden">
          <Header
            notifications={notifications}
            onClearNotification={clearNotification}
            onClearAll={clearAllNotifications}
            user={user}
            onLogout={onLogout}
          />

          {/* Toast Alerts — persistent attack banners (red for AI, yellow for triggers/interface-down) */}
          {toastAlerts.length > 0 && (
            <div className="px-6 pt-2 space-y-2">
              {toastAlerts.map((toast) => {
                const source = toast.source || 'AI';
                const isTrigger = source === 'TRIGGER_RULE';
                const isInterfaceDown = source === 'INTERFACE_DOWN';
                const isWarning = toast.severity === 'WARNING';

                // ── AI attack: red, full MITRE + features ──
                if (source === 'AI') {
                  const mitre = MITRE_MAP[toast.type];
                  const info = ATTACK_INFO[toast.type];
                  const predKey = `${toast.device}::${toast.iface}`;
                  const livePred = latestPredictions[predKey];
                  let topFeatures = [];
                  try { topFeatures = JSON.parse(livePred?.topFeatures || '[]'); } catch {}

                  return (
                    <div
                      key={toast.id}
                      className="bg-danger/10 border border-danger/30 rounded-lg px-4 py-3"
                    >
                      <div className="flex items-center gap-3 mb-2">
                        <span className="w-2 h-2 rounded-full bg-danger animate-pulse"></span>
                        <span className="text-lg">🚨</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-semibold text-danger">
                            {formatAttackName(toast.type)} detected on {toast.device}
                            <span className="text-danger/60 font-normal"> (interface: {toast.iface})</span>
                          </p>
                          <p className="text-xs text-gray-400">
                            {toast.confidence ? `${(toast.confidence * 100).toFixed(0)}% confidence` : ''}
                            {info ? ` · Severity: ` : ''}
                            {info && <span className={info.severity === 'CRITICAL' ? 'text-danger' : 'text-warning'}>{info.severity}</span>}
                            {mitre ? ` · ${mitre.id} — ${mitre.tactic}` : ''}
                          </p>
                        </div>
                        <span className="text-xs text-gray-500 whitespace-nowrap">
                          {toast.timestamp ? new Date(toast.timestamp).toLocaleTimeString() : ''}
                        </span>
                        <button
                          onClick={() => dismissToast(toast.id)}
                          className="px-3 py-1.5 text-xs rounded bg-danger/20 text-danger
                                     hover:bg-danger/30 transition-colors whitespace-nowrap font-medium"
                        >
                          ACK
                        </button>
                      </div>

                      <div className="flex gap-4 text-xs">
                        {topFeatures.length > 0 && (
                          <div className="flex-1 flex flex-wrap gap-x-4 gap-y-0.5">
                            {topFeatures.slice(0, 4).map((f, i) => (
                              <span key={i} className="text-gray-400">
                                <span className="text-gray-500">{f.name}:</span>{' '}
                                <span className="text-gray-200 font-mono">{typeof f.value === 'number' ? f.value.toFixed(1) : f.value}</span>
                                {f.normal != null && (
                                  <span className="text-gray-600"> (norm: {typeof f.normal === 'number' ? f.normal.toFixed(1) : f.normal})</span>
                                )}
                              </span>
                            ))}
                          </div>
                        )}
                        {info && (
                          <p className="text-accent whitespace-nowrap">
                            💡 {info.recommendation.split('·')[0].trim()}
                          </p>
                        )}
                      </div>
                    </div>
                  );
                }

                // ── Trigger Rule / Interface Down / Device Offline ──
                //   TRIGGER_RULE WARNING  → yellow (real warning — operator threshold)
                //   TRIGGER_RULE CRITICAL → red
                //   INTERFACE_DOWN        → grey (not a warning, just "not working")
                //   DEVICE_OFFLINE        → grey (device stopped sending metrics)
                const isGrey = isInterfaceDown || source === 'DEVICE_OFFLINE';

                let colorClass, textColor, dotColor, btnColor;
                if (isGrey) {
                  colorClass = 'bg-gray-500/10 border-gray-500/30';
                  textColor = 'text-gray-300';
                  dotColor = 'bg-gray-400';
                  btnColor = 'bg-gray-500/20 text-gray-300 hover:bg-gray-500/30';
                } else if (isWarning) {
                  colorClass = 'bg-warning/10 border-warning/30';
                  textColor = 'text-warning';
                  dotColor = 'bg-warning';
                  btnColor = 'bg-warning/20 text-warning hover:bg-warning/30';
                } else {
                  colorClass = 'bg-danger/10 border-danger/30';
                  textColor = 'text-danger';
                  dotColor = 'bg-danger';
                  btnColor = 'bg-danger/20 text-danger hover:bg-danger/30';
                }
                const icon = source === 'DEVICE_OFFLINE'
                  ? '⚫'
                  : isInterfaceDown ? '🔌' : '⚠️';

                let title, subtitle;
                if (source === 'DEVICE_OFFLINE') {
                  title = `Device offline: ${toast.device}`;
                  subtitle = `Stopped sending metrics · Severity: ${toast.severity}`;
                } else if (isInterfaceDown) {
                  title = `Interface down: ${toast.device} :: ${toast.iface}`;
                  subtitle = `Link is offline · Severity: ${toast.severity}`;
                } else {
                  title = `Rule triggered on ${toast.device}`;
                  const ruleLabel = toast.ruleName || toast.type;
                  const valLabel = toast.actualValue != null
                    ? `current: ${typeof toast.actualValue === 'number' ? toast.actualValue.toFixed(2) : toast.actualValue}`
                    : '';
                  subtitle = `${ruleLabel}${valLabel ? ' · ' + valLabel : ''} · Severity: ${toast.severity}`;
                }

                return (
                  <div
                    key={toast.id}
                    className={`${colorClass} border rounded-lg px-4 py-3`}
                  >
                    <div className="flex items-center gap-3">
                      <span className={`w-2 h-2 rounded-full ${dotColor} animate-pulse`}></span>
                      <span className="text-lg">{icon}</span>
                      <div className="flex-1 min-w-0">
                        <p className={`text-sm font-semibold ${textColor}`}>
                          {title}
                        </p>
                        <p className="text-xs text-gray-400">
                          {subtitle}
                        </p>
                      </div>
                      <span className="text-xs text-gray-500 whitespace-nowrap">
                        {toast.timestamp ? new Date(toast.timestamp).toLocaleTimeString() : ''}
                      </span>
                      <button
                        onClick={() => dismissToast(toast.id)}
                        className={`px-3 py-1.5 text-xs rounded ${btnColor} transition-colors whitespace-nowrap font-medium`}
                      >
                        ACK
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          <main className="flex-1 overflow-y-auto p-6">
            <Routes>
              <Route path="/" element={
                <Home
                  onDeviceClick={(device) => openSidePanel(device, 0)}
                  latestMetrics={latestMetrics}
                  latestPredictions={latestPredictions}
                  deviceLastSeen={deviceLastSeen}
                  activeSessions={activeSessions}
                />
              } />
              <Route path="/monitoring" element={
                <Monitoring
                  onDeviceClick={(device) => openSidePanel(device, 0)}
                  latestMetrics={latestMetrics}
                />
              } />
              <Route path="/security" element={
                <Security
                  onDeviceClick={(device, tab) => openSidePanel(device, tab)}
                  onAttackClick={openAttackDetail}
                  latestPredictions={latestPredictions}
                />
              } />
              <Route path="/terminal" element={
                isAdmin ? <Terminal /> : <AccessDenied />
              } />
              <Route path="/settings" element={
                isAdmin ? <Settings user={user} /> : <AccessDenied />
              } />
            </Routes>
          </main>
        </div>

        {sidePanelOpen && selectedDevice && (
          <SidePanel
            device={selectedDevice}
            defaultTab={sidePanelDefaultTab}
            onClose={closeSidePanel}
            latestMetrics={latestMetrics}
            latestPredictions={latestPredictions}
            isAdmin={isAdmin}
            deviceLastSeen={deviceLastSeen}
          />
        )}

        {selectedAttackSession && (
          <AttackDetailPanel
            session={selectedAttackSession}
            onClose={closeAttackDetail}
          />
        )}
      </div>
    </Router>
  );
}

function AccessDenied() {
  return (
    <div className="flex items-center justify-center h-full">
      <div className="text-center">
        <p className="text-4xl mb-3">🔒</p>
        <p className="text-gray-400 text-lg">Access Denied</p>
        <p className="text-gray-600 text-sm mt-1">Admin privileges required</p>
      </div>
    </div>
  );
}

export default App;