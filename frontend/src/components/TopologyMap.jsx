import React, { useMemo } from 'react';

/**
 * TopologyMap — Interactive network map (SVG).
 *
 * Final version:
 *   - ssh-server → ftp-server
 *   - Management Zone: supervision-app monitored, others display-only
 */

const NODES = {
  // Core / WAN-INT
  'edge-router': { x: 160, y: 250, label: 'edge-router', icon: '🔀', zone: 'core', monitored: true },
  'core-router': { x: 380, y: 250, label: 'core-router', icon: '🔀', zone: 'core', monitored: true },

  // DMZ
  'web-server':  { x: 580, y: 160, label: 'web-server',  icon: '🌐', zone: 'dmz', monitored: true },
  'dns-server':  { x: 580, y: 250, label: 'dns-server',  icon: '📡', zone: 'dmz', monitored: true },

  // LAN
  'ftp-server':  { x: 580, y: 340, label: 'ftp-server',  icon: '📁', zone: 'lan', monitored: true },
  'db-server':   { x: 580, y: 420, label: 'db-server',   icon: '🗄️', zone: 'lan', monitored: true },
  'pc1':           { x: 750, y: 340, label: 'pc1',         icon: '🖥️', zone: 'lan', monitored: true },
  'pc2':           { x: 750, y: 420, label: 'pc2',         icon: '🖥️', zone: 'lan', monitored: true },

  // Management Zone
  'supervision-app': { x: 380, y: 80,  label: 'Spring API', icon: '⚙️', zone: 'mgmt', monitored: true  }, // monitored
};

const LINKS = [
  ['edge-router', 'core-router'],
  ['core-router', 'web-server'],
  ['core-router', 'dns-server'],
  ['core-router', 'ftp-server'],
  ['core-router', 'db-server'],
  ['core-router', 'pc1'],
  ['core-router', 'pc2'],
  ['core-router', 'supervision-app'],
];

const ZONES = [
  { label: 'WAN',        x: 80,  y: 200, w: 140, h: 110, color: 'rgba(255,68,68,0.04)',   border: 'rgba(255,68,68,0.15)' },
  { label: 'Management', x: 220, y: 50,  w: 320, h: 110, color: 'rgba(99,102,241,0.04)',  border: 'rgba(99,102,241,0.20)' },
  { label: 'DMZ',        x: 510, y: 118, w: 140, h: 185, color: 'rgba(255,170,0,0.04)',   border: 'rgba(255,170,0,0.15)' },
  { label: 'LAN',        x: 510, y: 308, w: 280, h: 155, color: 'rgba(16,185,129,0.04)',  border: 'rgba(16,185,129,0.15)' },
];

const IP_MAP = {
  'edge-router':     '10.0.0.1 / .3.1',
  'core-router':     '10.0.3.2 / .1.1 / .2.1 / .4.1',
  'web-server':      '10.0.1.2',
  'dns-server':      '10.0.1.3',
  'ftp-server':      '10.0.2.2',
  'db-server':       '10.0.2.3',
  'pc1':               '10.0.2.4',
  'pc2':               '10.0.2.5',
  'supervision-app': '10.0.4.11',
};

const formatAttackName = (type) => {
  const names = {
    synflood: 'SYN Flood', dos: 'DoS', ddos: 'DDoS',
    udp_flood: 'UDP Flood', http_flood: 'HTTP Flood', dns_flood: 'DNS Flood',
    portscan: 'Port Scan', ping_of_death: 'Ping of Death', rst_flood: 'RST Flood',
    router_dos: 'Router DoS', router_synflood: 'Router SYN Flood',
    ftp_brute: 'FTP Brute Force',
    device_offline: 'Device Offline',
  };
  return names[type] || type?.toUpperCase() || '';
};

function TopologyMap({ latestPredictions = {}, onDeviceClick, deviceLastSeen }) {

  const deviceStatus = useMemo(() => {
    const status = {};
    Object.entries(latestPredictions).forEach(([key, pred]) => {
      const device = key.split('::')[0];
      const attack = pred.predictedAttack;
      if (attack && attack !== 'normal' && attack !== 'transit') {
        status[device] = 'attack';
      } else if (attack === 'transit' && status[device] !== 'attack') {
        status[device] = 'transit';
      }
    });
    return status;
  }, [latestPredictions]);

  const attackTargets = useMemo(() => {
    const targets = [];
    for (const [key, pred] of Object.entries(latestPredictions)) {
      const device = key.split('::')[0];
      const attack = pred.predictedAttack;
      if (attack && attack !== 'normal' && attack !== 'transit') {
        if (!targets.find(t => t.device === device)) {
          targets.push({ device, attack, confidence: pred.confidence });
        }
      }
    }
    return targets;
  }, [latestPredictions]);

  const getColor = (name) => {
    const node = NODES[name];
    if (!node?.monitored) return '#6366f1'; // display-only nodes = indigo

    const s = deviceStatus[name];
    if (s === 'attack') return '#ff4444';
    if (s === 'transit') return '#ffaa00';
    const lastSeen = deviceLastSeen?.current?.[name];
    const hasAnyData = Object.keys(deviceLastSeen?.current || {}).length > 0;
    if (lastSeen && lastSeen !== 1 && (Date.now() - lastSeen > 45000)) return '#6b7280';
    if (lastSeen === 1) return '#6b7280';
    if (!lastSeen && hasAnyData && node?.monitored) return '#6b7280';
    return '#34d399';
  };

  const handleClick = (name) => {
    if (!NODES[name].monitored) return;
    onDeviceClick({ name, type: name.includes('router') ? 'router' : 'server' });
  };

  return (
    <svg viewBox="0 0 830 480" className="w-full" style={{ minHeight: 280 }}>
      <defs>
        <filter id="glow">
          <feGaussianBlur stdDeviation="3" result="b" />
          <feMerge><feMergeNode in="b" /><feMergeNode in="SourceGraphic" /></feMerge>
        </filter>
      </defs>

      {/* Zone backgrounds */}
      {ZONES.map(z => (
        <g key={z.label}>
          <rect x={z.x} y={z.y} width={z.w} height={z.h} rx="12"
            fill={z.color} stroke={z.border} strokeWidth="1" strokeDasharray="4 4" />
          <text x={z.x + 8} y={z.y + 16} fill={z.border} fontSize="10" fontWeight="600">{z.label}</text>
        </g>
      ))}

      {/* Internet cloud */}
      <g>
        <ellipse cx="40" cy="250" rx="30" ry="20" fill="rgba(255,68,68,0.06)" stroke="rgba(255,68,68,0.2)" strokeWidth="1" strokeDasharray="3 3" />
        <text x="40" y="247" textAnchor="middle" fontSize="14">☁️</text>
        <text x="40" y="262" textAnchor="middle" fill="#6b7280" fontSize="7" fontFamily="monospace">Internet</text>
        <line x1="70" y1="250" x2={NODES['edge-router'].x - 26} y2="250"
          stroke="#1c2333" strokeWidth="2" strokeDasharray="4 4" />
      </g>

      {/* Normal links */}
      {LINKS.map(([from, to], i) => (
        <line key={i} x1={NODES[from].x} y1={NODES[from].y} x2={NODES[to].x} y2={NODES[to].y}
          stroke="#1c2333" strokeWidth="1.5" />
      ))}

      {/* Device nodes */}
      {Object.entries(NODES).map(([name, node]) => {
        const color = getColor(name);
        const isMonitored = node.monitored;
        const isUnderAttack = deviceStatus[name] === 'attack' && isMonitored;

        return (
          <g key={name} onClick={() => handleClick(name)}
            className={isMonitored ? 'cursor-pointer' : ''}
            style={isUnderAttack ? { filter: 'drop-shadow(0 0 8px rgba(255,68,68,0.6))' } : undefined}>

            <circle cx={node.x} cy={node.y} r={isMonitored ? 22 : 18} fill="#141923" stroke={color} strokeWidth={isMonitored ? 2 : 1.5} />

            {isUnderAttack && (
              <circle cx={node.x} cy={node.y} r="22" fill="none" stroke="#ff4444" strokeWidth="1" opacity="0.6">
                <animate attributeName="r" values="22;32;22" dur="1.5s" repeatCount="indefinite" />
                <animate attributeName="opacity" values="0.6;0;0.6" dur="1.5s" repeatCount="indefinite" />
              </circle>
            )}

            {isMonitored && (
              <circle cx={node.x + 16} cy={node.y - 16} r="4" fill={color} stroke="#141923" strokeWidth="1.5" />
            )}

            <text x={node.x} y={node.y + 5} textAnchor="middle" fontSize={isMonitored ? 16 : 13}>{node.icon}</text>

            <text x={node.x} y={node.y + (isMonitored ? 40 : 36)} textAnchor="middle"
              fill={isMonitored ? "#9ca3af" : "#6366f1"} fontSize={isMonitored ? 9 : 8} fontFamily="'JetBrains Mono', monospace">{node.label}</text>

            <text x={node.x} y={node.y + (isMonitored ? 51 : 46)} textAnchor="middle"
              fill="#4b5563" fontSize="7" fontFamily="'JetBrains Mono', monospace">{IP_MAP[name] || ''}</text>
          </g>
        );
      })}

      {/* Attack banners */}
      {attackTargets.map((target, idx) => (
        <g key={target.device}>
          <rect x="240" y={170 + idx * 44} width="270" height="38" rx="8"
            fill="rgba(255,68,68,0.08)" stroke="rgba(255,68,68,0.25)" strokeWidth="1" />
          <text x="255" y={188 + idx * 44} fill="#ff4444" fontSize="11" fontWeight="700">
            🚨 {formatAttackName(target.attack)}
          </text>
          <text x="255" y={202 + idx * 44} fill="#ff8888" fontSize="9">
            → {target.device} ({target.confidence ? `${(target.confidence * 100).toFixed(0)}%` : ''})
          </text>
        </g>
      ))}

      {/* Network labels on links */}
      <text x="260" y="240" fill="#374151" fontSize="8" fontFamily="monospace">WAN-INT</text>
      <text x="470" y="200" fill="#374151" fontSize="8" fontFamily="monospace">br-dmz</text>
      <text x="470" y="380" fill="#374151" fontSize="8" fontFamily="monospace">br-lan</text>
      <text x="380" y="170" fill="#6366f1" fontSize="8" fontFamily="monospace" textAnchor="middle">br-mgmt</text>
    </svg>
  );
}

export default TopologyMap;
