/**
 * API Service — Spring Boot (port 8080)
 * All REST calls to the backend.
 */

const BASE_URL = '/api';

// ── Helper ──────────────────────────────────────────────────
function getAuthHeaders() {
  const token = localStorage.getItem('token');
  return token ? { Authorization: `Bearer ${token}` } : {};
}

async function request(url, options = {}) {
  try {
    options.headers = { ...getAuthHeaders(), ...options.headers };
    const res = await fetch(url, options);
    // Token expired or invalid → redirect to login
    if (res.status === 401 || res.status === 403) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.reload();
      return;
    }
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    if (res.status === 204) return null;
    return res.json();
  } catch (err) {
    console.error(`API Error [${url}]:`, err.message);
    throw err;
  }
}

function post(url, body) {
  return request(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function put(url, body) {
  return request(url, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
}

function del(url) {
  return request(url, { method: 'DELETE' });
}

// ══════════════════════════════════════
//  Metrics
// ══════════════════════════════════════

export function getLatestMetrics() {
  return request(`${BASE_URL}/metrics/latest`);
}

export function getDeviceMetrics(deviceName) {
  return request(`${BASE_URL}/metrics/device/${deviceName}`);
}

// ══════════════════════════════════════
//  AI Predictions
// ══════════════════════════════════════

export function getLatestPredictions() {
  return request(`${BASE_URL}/metrics/predictions`);
}

// ══════════════════════════════════════
//  Attack Sessions
// ══════════════════════════════════════

export function getActiveSessions() {
  return request(`${BASE_URL}/attack-sessions/active`);
}

export function getAttackHistory(device, period) {
  const params = new URLSearchParams();
  if (device) params.set('device', device);
  if (period) params.set('period', period);
  const qs = params.toString();
  return request(`${BASE_URL}/attack-sessions/history${qs ? '?' + qs : ''}`);
}

// Home "Problem Timeline" needs active + ended attacks, overlap-aware.
// Returns sessions that overlap the given time window, regardless of status.
export function getAttackTimeline(period) {
  const qs = period ? `?period=${period}` : '';
  return request(`${BASE_URL}/attack-sessions/timeline${qs}`);
}

export function getRecentSessions() {
  return request(`${BASE_URL}/attack-sessions/recent`);
}

export function getUnacknowledged() {
  return request(`${BASE_URL}/attack-sessions/unacknowledged`);
}

export function acknowledgeSession(sessionId) {
  return post(`${BASE_URL}/attack-sessions/${sessionId}/acknowledge`);
}

export function getSessionStats() {
  return request(`${BASE_URL}/attack-sessions/stats`);
}

export function getDowntime(period = '30d') {
  return request(`${BASE_URL}/attack-sessions/downtime?period=${period}`);
}

export function getAttackImpact(sessionId) {
  return request(`${BASE_URL}/attack-sessions/${sessionId}/impact`);
}

// ══════════════════════════════════════
//  Audit Log
// ══════════════════════════════════════

export function getAuditLog(type, period) {
  const params = new URLSearchParams();
  if (type) params.set('type', type);
  if (period) params.set('period', period);
  const qs = params.toString();
  return request(`${BASE_URL}/audit${qs ? '?' + qs : ''}`);
}

// ══════════════════════════════════════
//  Alert Rules (Trigger System)
// ══════════════════════════════════════

export function getAlertRules() {
  return request(`${BASE_URL}/alert-rules`);
}

export function getAvailableMetrics() {
  return request(`${BASE_URL}/alert-rules/available-metrics`);
}

export function createAlertRule(rule) {
  return post(`${BASE_URL}/alert-rules`, rule);
}

export function updateAlertRule(id, rule) {
  return put(`${BASE_URL}/alert-rules/${id}`, rule);
}

export function deleteAlertRule(id) {
  return del(`${BASE_URL}/alert-rules/${id}`);
}

// ══════════════════════════════════════
//  Terminal (SSH via Spring Boot JSch)
// ══════════════════════════════════════

export function executeCommand(device, command) {
  return post(`${BASE_URL}/terminal/exec`, { device, command });
}

export function getTerminalDevices() {
  return request(`${BASE_URL}/terminal/devices`);
}

export function pingTerminalDevice(device) {
  return request(`${BASE_URL}/terminal/ping/${device}`);
}

// ══════════════════════════════════════
//  Mitigation (Playbook execution)
//
//  The old /api/protection/* endpoints are GONE — ProtectionController +
//  ProtectionService were deleted in the Batch 4 refactor. Playbooks are
//  now the single mitigation pathway.
//
//  When the operator clicks "Mitigate" in Security, this hits the playbook
//  controller which auto-picks the top-priority enabled playbook for the
//  session's trigger and runs it.
// ══════════════════════════════════════

export function executeProtection(sessionId) {
  return post(`${BASE_URL}/playbooks/run/${sessionId}`, {});
}

// ══════════════════════════════════════
//  Auth / User Management
// ══════════════════════════════════════

export function getUsers() {
  return request(`${BASE_URL}/auth/users`);
}

export function createUser(userData) {
  return post(`${BASE_URL}/auth/register`, userData);
}

export function deleteUser(userId) {
  return del(`${BASE_URL}/auth/users/${userId}`);
}

// ══════════════════════════════════════
//  Device Status (Online/Offline tracking)
// ══════════════════════════════════════

export function getDeviceTimeline(period = '6h') {
  return request(`${BASE_URL}/device-status/timeline?period=${period}`);
}

export function getOfflineDowntime(period = '24h') {
  return request(`${BASE_URL}/device-status/downtime?period=${period}`);
}

export function getOfflineDevices() {
  return request(`${BASE_URL}/device-status/offline`);
}

// ══════════════════════════════════════
//  Interface Status (router interfaces UP/DOWN history)
// ══════════════════════════════════════

/**
 * Get UP/DOWN timeline for a router's interfaces.
 * Used by SidePanel "Interfaces" tab.
 *
 * @param device The device name (e.g. "edge-router")
 * @param period One of: 1h | 6h | 24h | 7d | 30d (default 24h)
 * @returns Array of events: { interfaceName, status, startedAt, endedAt, durationFormatted }
 */
export function getInterfaceTimeline(device, period = '24h') {
  return request(`${BASE_URL}/interface-status/timeline/${device}?period=${period}`);
}

// ══════════════════════════════════════
//  History (for SidePanel "History" tab)
// ══════════════════════════════════════

/**
 * Historical series of a single metric for a device over a period.
 * Server downsamples to ≤200 points using Max-per-bucket.
 *
 * @param device e.g. "web-server"
 * @param metric e.g. "cpuUsage", "memoryUsage", "icmpInRate"
 * @param period One of: 1h | 6h | 24h | 7d | 30d (default 24h)
 * @returns { metric, period, points: [{timestamp, value}], stats: {min, max, avg, count} }
 */
/**
 * Historical series of a single metric for a device.
 * Two modes:
 *   1) Preset: getDeviceMetricHistory(device, metric, '24h')
 *   2) Custom range: getDeviceMetricHistory(device, metric, null, { from, to })
 *      where from/to are ISO datetime strings like "2026-04-20T14:00:00"
 */
export function getDeviceMetricHistory(device, metric, period = '24h', customRange = null) {
  const params = new URLSearchParams();
  params.set('metric', metric);
  if (customRange && customRange.from && customRange.to) {
    params.set('from', customRange.from);
    params.set('to', customRange.to);
  } else {
    params.set('period', period);
  }
  return request(`${BASE_URL}/metrics/device/${device}/history?${params.toString()}`);
}

// 🆕 ARP table — latest Layer-2 neighbors snapshot for Properties tab
export function getDeviceArpTable(device) {
  return request(`${BASE_URL}/metrics/device/${device}/arp`);
}

// ══════════════════════════════════════
//  Quick Commands (Terminal shortcuts)
// ══════════════════════════════════════

export function getQuickCommands() {
  // Admin view: all commands including disabled
  return request(`${BASE_URL}/quick-commands`);
}

export function getEnabledQuickCommands() {
  // Terminal page: only enabled commands
  return request(`${BASE_URL}/quick-commands/enabled`);
}

export function createQuickCommand(cmd) {
  return request(`${BASE_URL}/quick-commands`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd),
  });
}

export function updateQuickCommand(id, cmd) {
  return request(`${BASE_URL}/quick-commands/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(cmd),
  });
}

export function deleteQuickCommand(id) {
  return request(`${BASE_URL}/quick-commands/${id}`, { method: 'DELETE' });
}

// ══════════════════════════════════════
//  Playbook Rules (SOAR)
// ══════════════════════════════════════

export function getPlaybookRules() {
  return request(`${BASE_URL}/playbooks`);
}

export function createPlaybookRule(rule) {
  return post(`${BASE_URL}/playbooks`, rule);
}

export function updatePlaybookRule(id, rule) {
  return put(`${BASE_URL}/playbooks/${id}`, rule);
}

export function deletePlaybookRule(id) {
  return del(`${BASE_URL}/playbooks/${id}`);
}

// ══════════════════════════════════════
//  Device Control (Power Off / On / Restart)
//  ────────────────────────────────────
//  يستبدل pause/unpause القديم
//  3 أزرار في SidePanel: Power Off / Power On / Restart
// ══════════════════════════════════════

export function powerOffDevice(deviceName) {
  return post(`${BASE_URL}/device/${deviceName}/poweroff`);
}

export function powerOnDevice(deviceName) {
  return post(`${BASE_URL}/device/${deviceName}/poweron`);
}

export function restartDevice(deviceName) {
  return post(`${BASE_URL}/device/${deviceName}/restart`);
}

export function getDeviceStatus(deviceName) {
  return request(`${BASE_URL}/device/${deviceName}/status`);
}

export function getAllDevicesStatus() {
  return request(`${BASE_URL}/device/all/status`);
}

// Legacy aliases (إذا كود قديم لا يزال يستدعي pauseDevice/unpauseDevice)
export function pauseDevice(deviceName) {
  return powerOffDevice(deviceName);
}

export function unpauseDevice(deviceName) {
  return powerOnDevice(deviceName);
}

export function getDeviceContainerStatus() {
  return getAllDevicesStatus();
}

// ════════════════════════════════════════════════════════════════════════
//  EMAIL CONFIGURATION
// ════════════════════════════════════════════════════════════════════════

/**
 * Load the singleton EmailConfig (id=1).
 * Backend creates a default disabled row on first call, so this never 404s.
 * The smtpPassword field comes back as "***" — the sentinel meaning "unchanged".
 */
export function getEmailConfig() {
  return request(`${BASE_URL}/email-config`);
}

/**
 * Save the email configuration (admin only).
 * If smtpPassword is "***" the backend leaves the stored password alone.
 * If smtpPassword is anything else, the new value is saved.
 */
export function updateEmailConfig(config) {
  return put(`${BASE_URL}/email-config`, config);
}

/**
 * Send a real digest covering the last 24h to verify SMTP credentials.
 * Returns { status: 'OK'|'ERROR', message: '...' } in BOTH success and
 * failure cases — the controller wraps SMTP errors so the caller sees
 * the underlying message instead of just an HTTP 500.
 */
export function testEmail() {
  return post(`${BASE_URL}/email-config/test`, {});
}