import React, { useState, useEffect } from 'react';
import { NavLink } from 'react-router-dom';
import { Home, BarChart3, Shield, TerminalSquare, Settings } from 'lucide-react';
import { getAiStatus } from '../services/aiApi';

const PAGES = [
  { path: '/', label: 'Home', icon: Home, adminOnly: false },
  { path: '/monitoring', label: 'Monitoring', icon: BarChart3, adminOnly: false },
  { path: '/security', label: 'Security / AI', icon: Shield, adminOnly: false },
  { path: '/terminal', label: 'Terminal', icon: TerminalSquare, adminOnly: true },
  { path: '/settings', label: 'Settings', icon: Settings, adminOnly: true },
];

// ─────────────────────────────────────────────────────────────
// Device Groups
// - Routers: المسؤولة عن الـ routing
// - Servers: ftp بدل ssh
// - Management: 🆕 Management Zone (supervision-app فقط للمراقبة)
// ─────────────────────────────────────────────────────────────
const DEVICES = {
  Routers: [
    { name: 'edge-router', type: 'router', monitored: true },
    { name: 'core-router', type: 'router', monitored: true },
  ],
  Servers: [
    { name: 'web-server', type: 'server', monitored: true },
    { name: 'dns-server', type: 'server', monitored: true },
    { name: 'ftp-server', type: 'server', monitored: true },  // 🆕 كان ssh-server
    { name: 'db-server',  type: 'server', monitored: true },
  ],
  Management: [
      { name: 'supervision-app', type: 'server', monitored: true },
  ],
  Clients: [
      { name: 'pc1', type: 'client', monitored: true },
      { name: 'pc2', type: 'client', monitored: true },
  ],
};

const OFFLINE_THRESHOLD_MS = 40000;

function Sidebar({ onDeviceClick, latestMetrics, latestPredictions, deviceLastSeen }) {
  const [aiStatus, setAiStatus] = useState('unknown');
  const [, forceRender] = useState(0);

  // Poll AI status every 15s
  useEffect(() => {
    const fetchStatus = () => {
      getAiStatus()
        .then((data) => setAiStatus(data.livePredictionActive ? 'live' : 'stopped'))
        .catch(() => setAiStatus('disconnected'));
    };
    fetchStatus();
    const interval = setInterval(fetchStatus, 15000);
    return () => clearInterval(interval);
  }, []);

  // Re-render every 10s to update offline status
  useEffect(() => {
    const interval = setInterval(() => forceRender(n => n + 1), 10000);
    return () => clearInterval(interval);
  }, []);

  // Get user role from localStorage
  const user = JSON.parse(localStorage.getItem('user') || '{}');
  const isAdmin = user.role === 'ADMIN';

  // Determine device status from predictions + offline detection
  const getDeviceStatus = (deviceName, monitored) => {
    // Devices that are display-only (supervision-web/ai/db) → always show as 'normal'
    if (!monitored) return 'display-only';

    // Check if device is offline
    const lastSeen = deviceLastSeen?.current?.[deviceName];
    if (!lastSeen || (Date.now() - lastSeen > OFFLINE_THRESHOLD_MS)) {
      const hasAnyData = Object.keys(deviceLastSeen?.current || {}).length > 0;
      if (hasAnyData && !lastSeen) return 'offline';
      if (lastSeen && (Date.now() - lastSeen > OFFLINE_THRESHOLD_MS)) return 'offline';
    }

    // Check predictions
    const keys = Object.keys(latestPredictions || {}).filter((k) =>
      k.startsWith(deviceName + '::')
    );
    for (const key of keys) {
      const pred = latestPredictions[key];
      if (pred.predictedAttack && pred.predictedAttack !== 'normal' && pred.predictedAttack !== 'transit') {
        return 'attack';
      }
      if (pred.predictedAttack === 'transit') {
        return 'transit';
      }
    }
    return 'normal';
  };

  const statusDot = {
    attack: 'bg-danger',
    transit: 'bg-warning',
    normal: 'bg-success',
    offline: 'bg-gray-500',
    'display-only': 'bg-indigo-500/40',  // 🆕 مظهر مختلف للأجهزة غير المراقبة
  };

  const aiStatusColor = {
    live: 'bg-success',
    stopped: 'bg-warning',
    disconnected: 'bg-danger',
    unknown: 'bg-gray-500',
  };

  return (
    <aside className="w-56 bg-bg-card border-r border-gray-800 flex flex-col h-full">
      {/* Logo */}
      <div className="p-4 border-b border-gray-800">
        <h1 className="text-accent font-bold text-lg flex items-center gap-2">
          🛡️ TelecomAI
        </h1>
      </div>

      {/* Navigation */}
      <nav className="p-3 space-y-1">
        {PAGES.filter(p => !p.adminOnly || isAdmin).map(({ path, label, icon: Icon }) => (
          <NavLink
            key={path}
            to={path}
            className={({ isActive }) =>
              `flex items-center gap-3 px-3 py-2 rounded-lg text-sm transition-colors ${
                isActive
                  ? 'bg-accent/10 text-accent'
                  : 'text-gray-400 hover:bg-bg-hover hover:text-gray-200'
              }`
            }
          >
            <Icon size={18} />
            {label}
          </NavLink>
        ))}
      </nav>

      {/* Device List */}
      <div className="flex-1 overflow-y-auto p-3 border-t border-gray-800">
        {Object.entries(DEVICES).map(([group, devices]) => (
          <div key={group} className="mb-4">
            <p className="text-xs text-gray-500 uppercase tracking-wider mb-2 px-3">
              {group}
            </p>
            {devices.map((device) => {
              const status = getDeviceStatus(device.name, device.monitored);
              const isClickable = device.monitored;
              return (
                <button
                  key={device.name}
                  onClick={() => isClickable && onDeviceClick(device)}
                  disabled={!isClickable}
                  className={`w-full flex items-center gap-2 px-3 py-1.5 rounded text-sm
                              transition-colors ${
                    isClickable
                      ? 'text-gray-400 hover:bg-bg-hover hover:text-gray-200 cursor-pointer'
                      : 'text-gray-600 cursor-default'
                  }`}
                  title={!isClickable ? 'Display only — not monitored' : ''}
                >
                  <span className={`w-2 h-2 rounded-full ${statusDot[status]}`}></span>
                  <span className={status === 'offline' ? 'opacity-50' : ''}>
                    {device.name}
                  </span>
                  {status === 'offline' && (
                    <span className="ml-auto text-[9px] text-gray-600">offline</span>
                  )}
                  {status === 'display-only' && (
                    <span className="ml-auto text-[9px] text-indigo-400/60">view</span>
                  )}
                </button>
              );
            })}
          </div>
        ))}
      </div>

      {/* AI Status */}
      <div className="p-3 border-t border-gray-800">
        <div className="flex items-center gap-2 text-xs text-gray-500">
          <span>🤖 AI:</span>
          <span className={`w-2 h-2 rounded-full ${aiStatusColor[aiStatus]}`}></span>
          <span className={aiStatus === 'live' ? 'text-success' : aiStatus === 'stopped' ? 'text-warning' : 'text-danger'}>
            {aiStatus === 'live' ? 'Live' : aiStatus === 'stopped' ? 'Stopped' : 'Disconnected'}
          </span>
        </div>
      </div>
    </aside>
  );
}

export default Sidebar;
