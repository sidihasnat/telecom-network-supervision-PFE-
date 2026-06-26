import React from 'react';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend, LabelList
} from 'recharts';

const DEVICES = ['edge-router','core-router','web-server','dns-server','ftp-server','db-server','supervision-app'];
const SERVERS = ['web-server','dns-server','ftp-server','db-server','supervision-app'];

function Monitoring({ onDeviceClick, latestMetrics }) {
  const metrics = latestMetrics || {};

  // Build chart data from latest metrics
  const cpuData = DEVICES.map(d => ({
    name: d.replace('-', '\n'),
    cpu: +(metrics[d]?.cpuUsage ?? 0).toFixed(1),
  }));

  const memData = DEVICES.map(d => ({
    name: d.replace('-', '\n'),
    memory: +(metrics[d]?.memoryUsage ?? 0).toFixed(1),
  }));

  const diskData = DEVICES.map(d => ({
    name: d.replace('-', '\n'),
    disk: +(metrics[d]?.diskUsage ?? 0).toFixed(1),
    used: +(metrics[d]?.diskUsedGb ?? 0).toFixed(1),
    total: +(metrics[d]?.diskTotalGb ?? 0).toFixed(1),
  }));

  const throughputData = DEVICES.map(d => {
    const ifaces = metrics[d]?.interfaces || [];
    let totalIn = 0, totalOut = 0;
    ifaces.forEach(i => {
      totalIn += i.throughputIn || 0;
      totalOut += i.throughputOut || 0;
    });
    return { name: d.replace('-', '\n'), in: +totalIn.toFixed(2), out: +totalOut.toFixed(2) };
  });

  const latencyData = DEVICES.map(d => {
    const ifaces = metrics[d]?.interfaces || [];
    const maxLat = Math.max(0, ...ifaces.map(i => i.latency || 0));
    return { name: d.replace('-', '\n'), latency: +maxLat.toFixed(1) };
  });

  const connData = SERVERS.map(d => ({
    name: d.replace('-', '\n'),
    connections: metrics[d]?.connections ?? 0,
  }));

  const chartStyle = {
    bg: '#141923',
    grid: '#1c2333',
    text: '#6b7280',
    accent: '#10b981',
  };

  const customTooltip = ({ active, payload, label }) => {
    if (!active || !payload) return null;
    return (
      <div className="bg-bg-card border border-gray-700 rounded-lg p-2 text-xs">
        <p className="text-gray-300 mb-1">{label}</p>
        {payload.map((p, i) => (
          <p key={i} style={{ color: p.color }}>
            {p.name}: {p.value}
          </p>
        ))}
      </div>
    );
  };

  const diskTooltip = ({ active, payload, label }) => {
    if (!active || !payload || !payload[0]) return null;
    const d = payload[0].payload;
    return (
      <div className="bg-bg-card border border-gray-700 rounded-lg p-2 text-xs">
        <p className="text-gray-300 mb-1">{label}</p>
        <p className="text-purple-400">Usage: {d.disk}%</p>
        <p className="text-gray-400">Used: {d.used} GB / {d.total} GB</p>
      </div>
    );
  };

  const handleBarClick = (data) => {
    if (!data?.activeLabel) return;
    const deviceName = data.activeLabel.replace('\n', '-');
    onDeviceClick({
      name: deviceName,
      type: deviceName.includes('router') ? 'router' : 'server',
    });
  };

  // UX-3: Custom label renderer for bar values
  const renderBarLabel = (props) => {
    const { x, y, width, value } = props;
    if (value === 0 || value === undefined) return null;
    return (
      <text
        x={x + width / 2}
        y={y - 5}
        fill="#9ca3af"
        textAnchor="middle"
        fontSize={10}
        fontFamily="'JetBrains Mono', monospace"
      >
        {value}
      </text>
    );
  };

  return (
    <div className="space-y-6">
      <h2 className="text-lg font-semibold text-gray-200">Network Monitoring</h2>

      <div className="grid grid-cols-2 gap-4">
        {/* CPU Comparison */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">CPU Usage (%)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={cpuData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, 100]} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="cpu" fill={chartStyle.accent} radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Throughput Comparison */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Throughput (Mbps)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={throughputData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, (max) => Math.max(1, Math.ceil(max * 1.2))]} />
              <Tooltip content={customTooltip} />
              <Legend wrapperStyle={{ fontSize: 11, color: chartStyle.text }} />
              <Bar dataKey="in" fill="#3b82f6" name="In" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
              <Bar dataKey="out" fill={chartStyle.accent} name="Out" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Latency Comparison */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Latency (ms)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={latencyData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, (max) => Math.max(10, Math.ceil(max * 1.2))]} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="latency" fill="#f59e0b" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Connections (Servers only) */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Connections (Servers)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={connData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, (max) => Math.max(10, Math.ceil(max * 1.2))]} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="connections" fill="#8b5cf6" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Memory Comparison */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Memory Usage (%)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={memData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, 100]} />
              <Tooltip content={customTooltip} />
              <Bar dataKey="memory" fill="#3b82f6" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Disk Usage */}
        <div className="bg-bg-card rounded-xl p-4 border border-gray-800">
          <h3 className="text-sm text-gray-400 mb-3">Disk Usage (%)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={diskData} onClick={handleBarClick} margin={{ top: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke={chartStyle.grid} />
              <XAxis dataKey="name" tick={{ fill: chartStyle.text, fontSize: 10 }} />
              <YAxis tick={{ fill: chartStyle.text, fontSize: 10 }} domain={[0, 100]} />
              <Tooltip content={diskTooltip} />
              <Bar dataKey="disk" fill="#a855f7" radius={[4, 4, 0, 0]}>
                <LabelList content={renderBarLabel} />
              </Bar>
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

export default Monitoring;
