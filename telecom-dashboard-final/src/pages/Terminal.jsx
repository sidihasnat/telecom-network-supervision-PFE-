import React, { useState, useRef, useEffect } from 'react';
import { Play } from 'lucide-react';
import { executeCommand, getEnabledQuickCommands } from '../services/api';

// ─────────────────────────────────────────────────────────────
// Device Groups (matches SshService.java DEVICE_IP_MAP)
// ─────────────────────────────────────────────────────────────
const DEVICE_GROUPS = {
  Routers: ['edge-router', 'core-router'],
  Servers: ['web-server', 'dns-server', 'ftp-server', 'db-server'],   // 🆕 ftp-server
  Clients: ['pc1', 'pc2'],                                            // 🆕 pc1/pc2
  Management: ['supervision-app'],                                    // 🆕
};

const ALL_DEVICES = Object.values(DEVICE_GROUPS).flat();

// Quick commands now come from the database (managed via Settings → Quick Commands).
// We fetch the enabled ones once on mount, then filter by current device's type.
// Each row: { id, name, command, deviceType, sortOrder, enabled }
//   deviceType = null/""  → applies to ALL devices
//   deviceType = "router" → only routers
//   deviceType = "server" → only servers

function Terminal() {
  const [device, setDevice] = useState('web-server');
  const [command, setCommand] = useState('');
  const [history, setHistory] = useState([]);
  const [loading, setLoading] = useState(false);
  const outputRef = useRef(null);

  // Quick commands fetched from DB (managed in Settings → Quick Commands).
  // Empty array means either still loading, or admin hasn't configured any —
  // in both cases we just hide the Quick Commands section gracefully.
  const [quickCommands, setQuickCommands] = useState([]);

  // Fetch quick commands once on mount. Only enabled ones (the /enabled endpoint
  // already filters by enabled=true on the backend, so we don't need to recheck).
  // Sorted by sortOrder ascending — the order admin set in Settings.
  useEffect(() => {
    getEnabledQuickCommands()
      .then(cmds => setQuickCommands(Array.isArray(cmds) ? cmds : []))
      .catch(err => {
        console.error('Failed to load quick commands:', err);
        setQuickCommands([]);
      });
  }, []);

  // Filter quick commands by the currently selected device's type.
  // Routers in DEVICE_GROUPS.Routers → match deviceType "router"
  // Servers in DEVICE_GROUPS.Servers → match deviceType "server"
  // Anything else (Clients, Management) → only commands with no deviceType (universal)
  // A command with empty/null deviceType is ALWAYS shown (universal).
  const currentDeviceType =
    DEVICE_GROUPS.Routers.includes(device) ? 'router' :
    DEVICE_GROUPS.Servers.includes(device) ? 'server' :
    null;

  const visibleQuickCommands = quickCommands.filter(qc => {
    const scope = (qc.deviceType || '').trim().toLowerCase();
    if (!scope) return true;                          // universal — always show
    if (currentDeviceType === null) return false;     // device has no category, only show universal
    return scope === currentDeviceType;               // match exactly
  });

  // Auto-scroll to bottom
  useEffect(() => {
    if (outputRef.current) {
      outputRef.current.scrollTop = outputRef.current.scrollHeight;
    }
  }, [history, loading]);

  const runCommand = async (cmd) => {
  const cmdToRun = cmd || command;
  if (!cmdToRun.trim()) return;

  // 🧹 HANDLE CLEAR COMMAND LOCALLY
  if (cmdToRun.trim().toLowerCase() === 'clear') {
    setHistory([]);
    setCommand('');
    return;
  }

  setLoading(true);
  setHistory(prev => [...prev, { type: 'cmd', text: `${device}:/ $ ${cmdToRun}` }]);

  try {
    const data = await executeCommand(device, cmdToRun);
    setHistory(prev => [...prev, {
      type: data.exitCode === 0 ? 'output' : 'error',
      text: data.output || '(no output)',
    }]);
  } catch (err) {
    setHistory(prev => [...prev, {
      type: 'error',
      text: `SSH Connection error: ${err.message}\nMake sure the device is running and Spring Boot can reach it.`,
    }]);
  }

  setCommand('');
  setLoading(false);
};

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') runCommand();
  };

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-4">
        <h2 className="text-lg font-semibold text-gray-200">Terminal</h2>
        <select
          value={device}
          onChange={(e) => {
            setDevice(e.target.value);
            setHistory([]);
          }}
          className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300"
        >
          {Object.entries(DEVICE_GROUPS).map(([groupName, devices]) => (
            <optgroup key={groupName} label={groupName}>
              {devices.map(d => (
                <option key={d} value={d}>{d}</option>
              ))}
            </optgroup>
          ))}
        </select>
        <span className="text-xs text-gray-500">
          via SSH (JSch)
        </span>
      </div>

      {/* Terminal window */}
      <div className="bg-black rounded-xl border border-gray-700 overflow-hidden">
        {/* Output */}
        <div ref={outputRef} className="h-96 overflow-y-auto p-4 font-mono text-sm space-y-0.5">
          {history.length === 0 && (
            <p className="text-gray-600">Connected to {device}. Type a command or use Quick Commands below.</p>
          )}
          {history.map((entry, i) => (
            <pre key={i} className={`whitespace-pre-wrap break-all ${
              entry.type === 'cmd' ? 'text-green-400' :
              entry.type === 'error' ? 'text-red-400' :
              'text-gray-300'
            }`}>
              {entry.text}
            </pre>
          ))}
          {loading && <p className="text-yellow-400 animate-pulse">Executing...</p>}
        </div>

        {/* Input */}
        <div className="flex items-center border-t border-gray-800 px-4 py-2 bg-gray-900/50">
          <span className="text-green-400 font-mono text-sm mr-2">{device}:/ $</span>
          <input
            type="text"
            value={command}
            onChange={(e) => setCommand(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Enter command..."
            disabled={loading}
            className="flex-1 bg-transparent text-gray-200 font-mono text-sm outline-none"
          />
          <button
            onClick={() => runCommand()}
            disabled={loading}
            className="ml-2 p-1 text-accent hover:text-accent/80 transition-colors disabled:opacity-30"
          >
            <Play size={16} />
          </button>
        </div>
      </div>

      {/* Quick Commands — fetched from DB, filtered by current device's type.
          Hidden entirely if no commands match (e.g., admin hasn't configured
          any, or none apply to the current device category). */}
      {visibleQuickCommands.length > 0 && (
        <div>
          <p className="text-xs text-gray-500 mb-2">Quick Commands</p>
          <div className="flex flex-wrap gap-2">
            {visibleQuickCommands.map(qc => (
              <button
                key={qc.id}
                onClick={() => runCommand(qc.command)}
                disabled={loading}
                title={qc.command}
                className="px-3 py-1.5 bg-bg-card border border-gray-700 rounded-lg
                         text-xs font-mono text-gray-400 hover:text-accent hover:border-accent/30
                         transition-colors disabled:opacity-30"
              >
                {qc.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

export default Terminal;