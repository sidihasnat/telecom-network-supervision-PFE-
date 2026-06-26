import React, { useState, useEffect, useRef } from 'react';
import { Power, Plus, Trash2, Save, UserPlus, Edit2 } from 'lucide-react';
import { getAiStatus, startLivePrediction, stopLivePrediction, updateAiConfig } from '../services/aiApi';
import { getAlertRules, createAlertRule, deleteAlertRule, getUsers, createUser, deleteUser, getPlaybookRules, createPlaybookRule, updatePlaybookRule, deletePlaybookRule, getAvailableMetrics, getQuickCommands, createQuickCommand, updateQuickCommand, deleteQuickCommand, getEmailConfig, updateEmailConfig, testEmail } from '../services/api';

// Devices available for trigger rule scoping.
// Grouped for cleaner UI presentation.
const DEVICE_GROUPS = [
  { label: 'Routers', devices: ['edge-router', 'core-router'] },
  { label: 'Servers', devices: ['web-server', 'dns-server', 'ssh-server', 'db-server'] },
];
const ALL_DEVICES = DEVICE_GROUPS.flatMap(g => g.devices);

function Settings({ user }) {
  // ── AI Engine (from Flask) ────────────────────────────────
  const [aiConnected, setAiConnected] = useState(false);
  const [livePrediction, setLivePrediction] = useState(false);
  const [modelsInfo, setModelsInfo] = useState(null);
  const [slidingWindow, setSlidingWindow] = useState(3);
  const [confidenceThreshold, setConfidenceThreshold] = useState(0.95);

  // ── Trigger Rules (from Spring Boot) ──────────────────────
  const [triggerRules, setTriggerRules] = useState([]);
  const [newRule, setNewRule] = useState({
    metric: '', conditionOp: '>', value: '', severity: 'WARNING',
    selectedDevices: [],  // empty = all devices
  });
  const [showAddRule, setShowAddRule] = useState(false);
  const [availableMetrics, setAvailableMetrics] = useState({ system: [], tcp: [], security: [] });

  // ── Email Notifications ─────────────────────────────────────
  // Single config object loaded from backend (singleton, id=1).
  // The password field comes back as "***" from GET; we keep that sentinel
  // when saving unless the user actually typed a new password.
  const [emailConfig, setEmailConfig] = useState(null);   // null until loaded
  const [emailLoaded, setEmailLoaded] = useState(false);
  const [emailSaving, setEmailSaving] = useState(false);
  const [emailTesting, setEmailTesting] = useState(false);
  const [emailMessage, setEmailMessage] = useState(null); // { type: 'ok'|'err', text: '...' }
  const [emailShowPassword, setEmailShowPassword] = useState(false);

  // ── System Status ─────────────────────────────────────────
  const [backendConnected, setBackendConnected] = useState(false);

  // ── User Management ─────────────────────────────────────
  const [users, setUsers] = useState([]);
  const [showAddUser, setShowAddUser] = useState(false);
  const [newUser, setNewUser] = useState({ username: '', password: '', fullName: '', role: 'VIEWER' });
  const [userError, setUserError] = useState('');

  // ── Playbook Rules ──────────────────────────────────────
  const [playbooks, setPlaybooks] = useState([]);
  const [showAddPlaybook, setShowAddPlaybook] = useState(false);
  // When non-null, the form below is editing this playbook (not creating a new one).
  const [editingPlaybookId, setEditingPlaybookId] = useState(null);
  const [newPlaybook, setNewPlaybook] = useState({
    name: '',
    triggerType: 'ATTACK',
    triggerValue: 'synflood',
    commands: '',
    targetType: 'VICTIM',
    targetDevice: '',
    priority: 1,
    autoExecute: false,
    enabled: true,
    description: '',
  });

  // ── Quick Commands (Terminal shortcuts) ───────────────────
  const [quickCommands, setQuickCommands] = useState([]);
  const [showAddQuickCmd, setShowAddQuickCmd] = useState(false);
  const [newQuickCmd, setNewQuickCmd] = useState({
    name: '', command: '', deviceType: '', sortOrder: 0, enabled: true,
  });

  // ── Load data on mount ────────────────────────────────────
  useEffect(() => {
    // Flask AI status
    getAiStatus()
      .then((data) => {
        setAiConnected(true);
        setLivePrediction(data.livePredictionActive || false);
        // Models info: modelsLoaded is a dict like {"server": true, "router": true}
        const loaded = data.modelsLoaded || {};
        const stats = data.trainingStats || {};
        if (Object.keys(loaded).length > 0) {
          const parseModel = (s) => {
            if (!s) return null;
            const cls = Array.isArray(s.classes) ? s.classes.length : (s.classes || '?');
            let acc = s.accuracy;
            if (acc != null && typeof acc === 'number' && acc <= 1) {
              acc = (acc * 100).toFixed(1);
            } else if (acc == null) {
              acc = '?';
            }
            return { classes: cls, accuracy: acc };
          };
          setModelsInfo({
            server: loaded.server ? parseModel(stats.server) : null,
            router: loaded.router ? parseModel(stats.router) : null,
          });
        }
        setSlidingWindow(data.slidingWindowSize || 3);
        setConfidenceThreshold(data.highConfidenceThreshold || 0.95);
      })
      .catch(() => setAiConnected(false));

    // Alert rules
    getAlertRules()
      .then((rules) => {
        setTriggerRules(rules);
        setBackendConnected(true);
      })
      .catch(() => setBackendConnected(false));

    // Available metrics for autocomplete
    getAvailableMetrics()
      .then(setAvailableMetrics)
      .catch(() => {});

    // Users
    getUsers().then(setUsers).catch(() => {});

    // Playbook rules
    getPlaybookRules().then(setPlaybooks).catch(() => {});

    // Quick commands (Terminal shortcuts)
    getQuickCommands().then(setQuickCommands).catch(() => {});

    // Email configuration (singleton). The backend creates a default disabled
    // row on first GET if none exists, so this is always safe.
    getEmailConfig()
      .then((cfg) => {
        setEmailConfig(cfg || defaultEmailConfig());
        setEmailLoaded(true);
      })
      .catch(() => {
        // If backend is unreachable, render the form with defaults so the
        // admin can still fill it in once connection comes back.
        setEmailConfig(defaultEmailConfig());
        setEmailLoaded(true);
      });
  }, []);

  // ── Handlers ──────────────────────────────────────────────
  const toggleLivePrediction = async () => {
    try {
      if (livePrediction) {
        await stopLivePrediction();
      } else {
        await startLivePrediction();
      }
      setLivePrediction(!livePrediction);
    } catch (err) {
      console.error('Toggle live prediction failed:', err);
    }
  };

  const saveAiConfig = async () => {
    try {
      await updateAiConfig({
        slidingWindowSize: slidingWindow,
        highConfidenceThreshold: confidenceThreshold,
      });
    } catch (err) {
      console.error('Save AI config failed:', err);
    }
  };

  const handleAddRule = async () => {
    if (!newRule.metric || !newRule.value) return;

    // Validate metric name — must exist in available metrics
    const allMetrics = [
      ...availableMetrics.system,
      ...availableMetrics.tcp,
      ...availableMetrics.security,
    ];
    const isValid = allMetrics.some(m => m.name === newRule.metric);
    if (!isValid) {
      alert(`Unknown metric: "${newRule.metric}". Please select from the suggestions.`);
      return;
    }

    try {
      // selectedDevices (array) → deviceNames (comma-separated string, null if empty = all)
      const deviceNames = newRule.selectedDevices && newRule.selectedDevices.length > 0
        ? newRule.selectedDevices.join(',')
        : null;
      const saved = await createAlertRule({
        metric: newRule.metric,
        conditionOp: newRule.conditionOp,
        value: parseFloat(newRule.value),
        severity: newRule.severity,
        deviceNames,
        enabled: true,
      });
      setTriggerRules(prev => [...prev, saved]);
      setNewRule({
        metric: '', conditionOp: '>', value: '', severity: 'WARNING',
        selectedDevices: [],
      });
      setShowAddRule(false);
    } catch (err) {
      console.error('Add rule failed:', err);
    }
  };

  const handleDeleteRule = async (id) => {
    try {
      await deleteAlertRule(id);
      setTriggerRules(prev => prev.filter(r => r.id !== id));
    } catch (err) {
      console.error('Delete rule failed:', err);
    }
  };

  // ── Playbook handlers ──────────────────────────────────────

  const handleSavePlaybook = async () => {
    if (!newPlaybook.name.trim() || !newPlaybook.triggerValue.trim() || !newPlaybook.commands.trim()) {
      alert('Name, trigger value, and at least one command are required');
      return;
    }
    const payload = {
      ...newPlaybook,
      targetDevice: newPlaybook.targetType === 'CUSTOM' ? newPlaybook.targetDevice : null,
    };
    try {
      let saved;
      if (editingPlaybookId) {
        // UPDATE path
        saved = await updatePlaybookRule(editingPlaybookId, payload);
        setPlaybooks(prev => prev.map(p => (p.id === editingPlaybookId ? saved : p)));
      } else {
        // CREATE path
        saved = await createPlaybookRule(payload);
        setPlaybooks(prev => [...prev, saved].sort((a, b) => b.priority - a.priority));
      }
      // Reset form
      setNewPlaybook({
        name: '', triggerType: 'ATTACK', triggerValue: 'synflood',
        commands: '', targetType: 'VICTIM', targetDevice: '',
        priority: 1, autoExecute: false, enabled: true, description: '',
      });
      setEditingPlaybookId(null);
      setShowAddPlaybook(false);
    } catch (err) {
      console.error('Save playbook failed:', err);
      alert('Save failed: ' + (err.message || 'unknown error'));
    }
  };

  // Load a playbook into the form for editing.
  const handleEditPlaybook = (pb) => {
    setNewPlaybook({
      name: pb.name || '',
      triggerType: pb.triggerType || 'ATTACK',
      triggerValue: pb.triggerValue || '',
      commands: pb.commands || '',
      targetType: pb.targetType || 'VICTIM',
      targetDevice: pb.targetDevice || '',
      priority: pb.priority || 1,
      autoExecute: !!pb.autoExecute,
      enabled: pb.enabled !== false,
      description: pb.description || '',
    });
    setEditingPlaybookId(pb.id);
    setShowAddPlaybook(true);
    // Scroll the form into view so the user notices it.
    setTimeout(() => {
      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' });
    }, 50);
  };

  const handleDeletePlaybook = async (id) => {
    try {
      await deletePlaybookRule(id);
      setPlaybooks(prev => prev.filter(p => p.id !== id));
    } catch (err) {
      console.error('Delete playbook failed:', err);
    }
  };

  const handleTogglePlaybook = async (pb) => {
    try {
      const updated = await updatePlaybookRule(pb.id, { ...pb, enabled: !pb.enabled });
      setPlaybooks(prev => prev.map(p => (p.id === pb.id ? updated : p)));
    } catch (err) {
      console.error('Toggle playbook failed:', err);
    }
  };

  const handleTogglePlaybookMode = async (pb) => {
    // AUTO ↔ MANUAL flip without touching enabled state
    try {
      const updated = await updatePlaybookRule(pb.id, { ...pb, autoExecute: !pb.autoExecute });
      setPlaybooks(prev => prev.map(p => (p.id === pb.id ? updated : p)));
    } catch (err) {
      console.error('Toggle playbook mode failed:', err);
    }
  };

  // Bulk: set every enabled playbook to AUTO or MANUAL at once.
  // Disabled playbooks are left untouched.
  const handleSetAllPlaybooksMode = async (autoExecute) => {
    const targets = playbooks.filter(p => p.enabled && p.autoExecute !== autoExecute);
    if (targets.length === 0) return;

    const label = autoExecute ? 'AUTO' : 'MANUAL';
    if (!window.confirm(`Set ${targets.length} enabled playbook(s) to ${label}?`)) return;

    try {
      const updated = await Promise.all(
        targets.map(pb => updatePlaybookRule(pb.id, { ...pb, autoExecute }))
      );
      // Merge updates back into state
      setPlaybooks(prev => prev.map(p => {
        const u = updated.find(x => x.id === p.id);
        return u || p;
      }));
    } catch (err) {
      console.error('Bulk mode update failed:', err);
      alert('Some updates failed. Refresh to see current state.');
    }
  };

  // ── Quick Commands handlers ───────────────────────────────

  const handleAddQuickCmd = async () => {
    if (!newQuickCmd.name.trim() || !newQuickCmd.command.trim()) {
      alert('Name and command are required');
      return;
    }
    try {
      const saved = await createQuickCommand({
        name: newQuickCmd.name.trim(),
        command: newQuickCmd.command.trim(),
        // Store null (not empty string) for "all devices" so JPA treats it consistently
        deviceType: newQuickCmd.deviceType || null,
        sortOrder: parseInt(newQuickCmd.sortOrder) || 0,
        enabled: true,
      });
      setQuickCommands(prev => [...prev, saved]);
      setNewQuickCmd({ name: '', command: '', deviceType: '', sortOrder: 0, enabled: true });
      setShowAddQuickCmd(false);
    } catch (err) {
      console.error('Add quick command failed:', err);
    }
  };

  const handleDeleteQuickCmd = async (id) => {
    try {
      await deleteQuickCommand(id);
      setQuickCommands(prev => prev.filter(c => c.id !== id));
    } catch (err) {
      console.error('Delete quick command failed:', err);
    }
  };

  const handleToggleQuickCmd = async (cmd) => {
    try {
      const updated = await updateQuickCommand(cmd.id, {
        ...cmd,
        enabled: !cmd.enabled,
      });
      setQuickCommands(prev => prev.map(c => (c.id === cmd.id ? updated : c)));
    } catch (err) {
      console.error('Toggle quick command failed:', err);
    }
  };

  const handleAddUser = async () => {
    if (!newUser.username || !newUser.password || !newUser.fullName) {
      setUserError('All fields are required');
      return;
    }
    setUserError('');
    try {
      const saved = await createUser(newUser);
      setUsers(prev => [...prev, saved]);
      setNewUser({ username: '', password: '', fullName: '', role: 'VIEWER' });
      setShowAddUser(false);
    } catch (err) {
      setUserError('Failed to create user');
    }
  };

  const handleDeleteUser = async (userId, username) => {
    if (username === user?.username) return; // Can't delete yourself
    if (username === 'admin') return; // SEC-1: Super Admin cannot be deleted
    try {
      await deleteUser(userId);
      setUsers(prev => prev.filter(u => u.id !== userId));
    } catch (err) {
      console.error('Delete user failed:', err);
    }
  };

  // ── Email handlers ──────────────────────────────────────────
  // Local state mutator — called on every keystroke / toggle in the email form.
  // We patch a single field at a time and keep the rest intact.
  const patchEmailConfig = (patch) => {
    setEmailConfig((prev) => ({ ...(prev || defaultEmailConfig()), ...patch }));
  };

  const handleSaveEmail = async () => {
    if (!emailConfig) return;
    setEmailSaving(true);
    setEmailMessage(null);
    try {
      const updated = await updateEmailConfig(emailConfig);
      // Re-mask password locally so the user sees "***" after save.
      setEmailConfig(updated);
      setEmailShowPassword(false);
      setEmailMessage({ type: 'ok', text: 'Email settings saved.' });
    } catch (err) {
      setEmailMessage({ type: 'err', text: 'Save failed: ' + (err.message || 'unknown') });
    } finally {
      setEmailSaving(false);
    }
  };

  const handleTestEmail = async () => {
    setEmailTesting(true);
    setEmailMessage(null);
    try {
      // Backend returns { status: 'OK'|'ERROR', message: '...' } even on failure
      const res = await testEmail();
      if (res && res.status === 'OK') {
        setEmailMessage({ type: 'ok', text: res.message || 'Test email sent.' });
      } else {
        setEmailMessage({ type: 'err', text: (res && res.message) || 'Test email failed.' });
      }
    } catch (err) {
      setEmailMessage({ type: 'err', text: 'Test failed: ' + (err.message || 'unknown') });
    } finally {
      setEmailTesting(false);
    }
  };

  return (
    <div className="space-y-6 max-w-4xl">
      <h2 className="text-lg font-semibold text-gray-200">Settings</h2>

      {/* ═══ A. AI Engine Control ═══ */}
      <Section title="AI Engine Control">
        <Row label="Flask AI Engine" desc="Connection status">
          <StatusBadge connected={aiConnected} />
        </Row>
        <Row label="Live Prediction" desc="Start/Stop real-time AI analysis">
          <button
            onClick={toggleLivePrediction}
            disabled={!aiConnected}
            className={`flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-colors
              disabled:opacity-30 ${livePrediction
              ? 'bg-danger/10 text-danger hover:bg-danger/20'
              : 'bg-success/10 text-success hover:bg-success/20'
            }`}
          >
            <Power size={16} />
            {livePrediction ? 'Stop' : 'Start'}
          </button>
        </Row>
        <Row label="Models" desc="Loaded model information">
          <div className="text-right text-sm">
            {modelsInfo ? (
              <>
                {modelsInfo.server && (
                  <p className="text-gray-300">
                    SERVER: {modelsInfo.server.classes} classes, {modelsInfo.server.accuracy}% acc
                  </p>
                )}
                {modelsInfo.router && (
                  <p className="text-gray-300">
                    ROUTER: {modelsInfo.router.classes} classes, {modelsInfo.router.accuracy}% acc
                  </p>
                )}
              </>
            ) : (
              <p className="text-gray-500">Not loaded</p>
            )}
          </div>
        </Row>
        <Row label="Sliding Window" desc="Number of readings for majority vote">
          <input type="number" value={slidingWindow}
            onChange={(e) => setSlidingWindow(Number(e.target.value))}
            min={1} max={10}
            className="w-20 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm
                     text-gray-200 font-mono text-center" />
        </Row>
        <Row label="Confidence Threshold" desc="Minimum confidence for immediate alert">
          <input type="number" value={confidenceThreshold}
            onChange={(e) => setConfidenceThreshold(Number(e.target.value))}
            min={0.5} max={1.0} step={0.05}
            className="w-20 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm
                     text-gray-200 font-mono text-center" />
        </Row>
        <div className="p-4">
          <button onClick={saveAiConfig}
            className="flex items-center gap-2 px-4 py-2 bg-accent/10 text-accent rounded-lg
                     hover:bg-accent/20 transition-colors text-sm">
            <Save size={14} /> Save AI Config
          </button>
        </div>
      </Section>

      {/* ═══ B1. Protection System — overview toggles for existing playbooks ═══ */}
      <Section title="Protection System">
        <div className="p-4 space-y-2">
          <p className="text-xs text-gray-500 mb-2">
            Each row below is one of your playbooks. Use the toggles to enable/disable
            and switch between auto-execute and manual. To create, edit, or delete
            playbooks, scroll to the "Response Playbooks" section below.
          </p>

          {/* Global mode controls — set ALL enabled playbooks to AUTO or MANUAL at once.
              Useful at start of a demo / when going on/off duty. */}
          {playbooks.length > 0 && (
            <div className="flex items-center gap-2 mb-3 pb-3 border-b border-gray-800">
              <span className="text-xs text-gray-500">Global:</span>
              <button onClick={() => handleSetAllPlaybooksMode(true)}
                className="text-[11px] px-3 py-1 rounded bg-warning/10 text-warning hover:bg-warning/20 transition-colors">
                Set all to AUTO
              </button>
              <button onClick={() => handleSetAllPlaybooksMode(false)}
                className="text-[11px] px-3 py-1 rounded bg-bg-primary text-gray-400 hover:bg-bg-hover transition-colors">
                Set all to MANUAL
              </button>
              <span className="text-[10px] text-gray-600 ml-2">
                ({playbooks.filter(p => p.autoExecute && p.enabled).length} on AUTO,{' '}
                {playbooks.filter(p => !p.autoExecute && p.enabled).length} on MANUAL,{' '}
                {playbooks.filter(p => !p.enabled).length} disabled)
              </span>
            </div>
          )}

          {playbooks.length === 0 && (
            <p className="text-gray-600 text-sm text-center py-4">
              No playbooks defined yet. Create one in the Response Playbooks section below.
            </p>
          )}

          {playbooks.map(pb => (
            <div key={pb.id} className="bg-bg-primary rounded-lg p-3 flex items-center gap-3">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 flex-wrap">
                  <span className={`text-sm font-medium ${pb.enabled ? 'text-gray-200' : 'text-gray-500'}`}>
                    {pb.name}
                  </span>
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent/10 text-accent">
                    {pb.triggerType === 'ATTACK' ? 'attack' : 'alert'}:{' '}
                    <span className="font-mono">{pb.triggerValue}</span>
                  </span>
                </div>
                <p className="text-[10px] text-gray-500 mt-0.5">
                  target: {pb.targetType}{pb.targetDevice ? ` (${pb.targetDevice})` : ''} · priority {pb.priority}
                </p>
              </div>

              {/* Mode toggle: AUTO vs MANUAL */}
              <button
                onClick={() => handleTogglePlaybookMode(pb)}
                disabled={!pb.enabled}
                className={`text-[10px] px-2 py-1 rounded transition-colors ${
                  !pb.enabled
                    ? 'bg-gray-800 text-gray-600 cursor-not-allowed'
                    : pb.autoExecute
                      ? 'bg-warning/10 text-warning hover:bg-warning/20'
                      : 'bg-bg-card text-gray-400 hover:bg-bg-hover'
                }`}
                title={pb.enabled
                  ? (pb.autoExecute
                      ? 'Auto — runs automatically. Click to switch to manual.'
                      : 'Manual — waits for operator Mitigate click. Click to switch to auto.')
                  : 'Enable the playbook first to change its mode'}
              >
                {pb.autoExecute ? 'AUTO' : 'MANUAL'}
              </button>

              {/* Enable/disable toggle */}
              <button
                onClick={() => handleTogglePlaybook(pb)}
                className={`text-[10px] px-2 py-1 rounded transition-colors ${
                  pb.enabled
                    ? 'bg-accent/10 text-accent hover:bg-accent/20'
                    : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                }`}
              >
                {pb.enabled ? 'ON' : 'OFF'}
              </button>
            </div>
          ))}
        </div>
      </Section>

      {/* ═══ B. Response Playbooks ═══ */}
      <Section title="Response Playbooks">
        <div className="p-4 space-y-3">
          <p className="text-xs text-gray-500">
            Each playbook defines a mitigation for a specific trigger (attack or alert).
            When the trigger fires and <span className="text-accent">Mitigate</span> is clicked
            in Security, the top-priority enabled playbook runs. Set <span className="text-accent">auto-execute</span>
            to make it run automatically without operator action.
          </p>

          {playbooks.length === 0 && (
            <p className="text-gray-600 text-sm text-center py-4">No playbooks yet. Add one below.</p>
          )}

          {/* Playbook list */}
          {playbooks.map(pb => (
            <div key={pb.id} className="bg-bg-primary rounded-lg p-3 space-y-2">
              <div className="flex items-center gap-2 flex-wrap">
                <span className={`text-sm font-medium ${pb.enabled ? 'text-gray-200' : 'text-gray-500 line-through'}`}>
                  {pb.name}
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-accent/10 text-accent">
                  {pb.triggerType === 'ATTACK' ? '🚨 attack' : '⚠️ alert'}: <span className="font-mono">{pb.triggerValue}</span>
                </span>
                <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-700 text-gray-400">
                  target: {pb.targetType}{pb.targetDevice ? ` (${pb.targetDevice})` : ''}
                </span>
                <span className="text-[10px] text-gray-500">priority: {pb.priority}</span>
                {pb.autoExecute && (
                  <span className="text-[10px] px-1.5 py-0.5 rounded bg-warning/10 text-warning">
                    auto
                  </span>
                )}
                <div className="flex-1" />
                <button
                  onClick={() => handleTogglePlaybook(pb)}
                  className={`text-[10px] px-2 py-0.5 rounded transition-colors ${
                    pb.enabled
                      ? 'bg-accent/10 text-accent hover:bg-accent/20'
                      : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                  }`}
                >
                  {pb.enabled ? 'ON' : 'OFF'}
                </button>
                <button
                  onClick={() => handleEditPlaybook(pb)}
                  className="p-1 text-gray-500 hover:text-accent"
                  title="Edit playbook"
                >
                  <Edit2 size={14} />
                </button>
                <button
                  onClick={() => handleDeletePlaybook(pb.id)}
                  className="p-1 text-gray-500 hover:text-danger"
                  title="Delete playbook"
                >
                  <Trash2 size={14} />
                </button>
              </div>
              <pre className="text-[10px] font-mono text-gray-500 bg-bg-card rounded p-2 overflow-x-auto whitespace-pre-wrap"
                   title="Commands to execute (one per line)">
{pb.commands}
              </pre>
              {pb.description && (
                <p className="text-[10px] text-gray-600 italic">{pb.description}</p>
              )}
            </div>
          ))}

          {/* Add/edit form */}
          {showAddPlaybook && (
            <div className="bg-bg-primary rounded-lg p-3 space-y-2 border border-accent/30">
              {editingPlaybookId && (
                <div className="flex items-center gap-2 text-xs text-accent border-b border-gray-800 pb-2 mb-1">
                  <Edit2 size={12} />
                  <span>Editing playbook (id={editingPlaybookId})</span>
                </div>
              )}
              <div className="grid grid-cols-2 gap-2">
                <input
                  placeholder="Playbook name (e.g. SYN Cookies Protection)"
                  value={newPlaybook.name}
                  onChange={e => setNewPlaybook(p => ({ ...p, name: e.target.value }))}
                  className="col-span-2 bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-200 text-xs"
                />
                <select
                  value={newPlaybook.triggerType}
                  onChange={e => setNewPlaybook(p => ({ ...p, triggerType: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-300 text-xs"
                >
                  <option value="ATTACK">Trigger: Attack type</option>
                  <option value="ALERT">Trigger: Alert (trigger rule)</option>
                </select>
                <input
                  placeholder={newPlaybook.triggerType === 'ATTACK'
                    ? 'Attack type e.g. synflood'
                    : 'Rule format e.g. trigger:cpuUsage>80'}
                  value={newPlaybook.triggerValue}
                  onChange={e => setNewPlaybook(p => ({ ...p, triggerValue: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-200 text-xs font-mono"
                />
                <select
                  value={newPlaybook.targetType}
                  onChange={e => setNewPlaybook(p => ({ ...p, targetType: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-300 text-xs"
                >
                  <option value="VICTIM">VICTIM (attacked device)</option>
                  <option value="ATTACKER">ATTACKER (victim, targets attacker IP)</option>
                  <option value="ROUTER">ROUTER (edge-router)</option>
                  <option value="CUSTOM">CUSTOM device</option>
                </select>
                {newPlaybook.targetType === 'CUSTOM' && (
                  <input
                    placeholder="Custom device name"
                    value={newPlaybook.targetDevice}
                    onChange={e => setNewPlaybook(p => ({ ...p, targetDevice: e.target.value }))}
                    className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-200 text-xs"
                  />
                )}
                <div className="col-span-2">
                  <label className="block text-[10px] text-gray-500 mb-1">
                    Commands — one per line. Placeholders:{' '}
                    <span className="text-accent font-mono">{'{device}'}</span>,{' '}
                    <span className="text-accent font-mono">{'{interface}'}</span>,{' '}
                    <span className="text-accent font-mono">{'{attackerIp}'}</span>,{' '}
                    <span className="text-accent font-mono">{'{attackerIps}'}</span>
                  </label>
                  <p className="text-[9px] text-gray-600 mb-1">
                    {'{attackerIp}'} expands to one command per detected source IP.{' '}
                    {'{attackerIps}'} substitutes all IPs space-separated in a single command.{' '}
                    Commands referencing them are skipped if no IPs are available
                    (e.g. spoofed SYN floods, UDP floods).
                  </p>
                  <textarea
                    placeholder={'sysctl -w net.ipv4.tcp_syncookies=1\niptables -A INPUT -p tcp --syn -m limit --limit 1/s -j ACCEPT'}
                    value={newPlaybook.commands}
                    onChange={e => setNewPlaybook(p => ({ ...p, commands: e.target.value }))}
                    rows={4}
                    className="w-full bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-200 text-xs font-mono"
                  />
                </div>
                <div className="flex items-center gap-2">
                  <label className="text-[10px] text-gray-500">Priority:</label>
                  <input
                    type="number"
                    value={newPlaybook.priority}
                    onChange={e => setNewPlaybook(p => ({ ...p, priority: parseInt(e.target.value) || 1 }))}
                    className="w-16 bg-bg-card border border-gray-700 rounded px-2 py-1 text-gray-200 text-xs"
                  />
                  <span className="text-[10px] text-gray-600">(higher wins ties)</span>
                </div>
                <div className="flex items-center gap-2">
                  <input
                    type="checkbox"
                    id="auto-exec-cb"
                    checked={newPlaybook.autoExecute}
                    onChange={e => setNewPlaybook(p => ({ ...p, autoExecute: e.target.checked }))}
                  />
                  <label htmlFor="auto-exec-cb" className="text-xs text-gray-400">
                    Auto-execute on detection
                  </label>
                </div>
                <input
                  placeholder="Description (optional)"
                  value={newPlaybook.description}
                  onChange={e => setNewPlaybook(p => ({ ...p, description: e.target.value }))}
                  className="col-span-2 bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-gray-200 text-xs"
                />
              </div>
              <div className="flex gap-2">
                <button onClick={handleSavePlaybook}
                  className="px-3 py-1.5 bg-accent/10 text-accent rounded text-xs hover:bg-accent/20">
                  {editingPlaybookId ? 'Update Playbook' : 'Save Playbook'}
                </button>
                <button onClick={() => {
                  setShowAddPlaybook(false);
                  setEditingPlaybookId(null);
                  setNewPlaybook({
                    name: '', triggerType: 'ATTACK', triggerValue: 'synflood',
                    commands: '', targetType: 'VICTIM', targetDevice: '',
                    priority: 1, autoExecute: false, enabled: true, description: '',
                  });
                }}
                  className="px-3 py-1.5 bg-gray-700 text-gray-400 rounded text-xs hover:bg-gray-600">
                  Cancel
                </button>
              </div>
            </div>
          )}

          <button onClick={() => {
              if (showAddPlaybook) {
                // Cancel — also clear edit mode
                setEditingPlaybookId(null);
                setNewPlaybook({
                  name: '', triggerType: 'ATTACK', triggerValue: 'synflood',
                  commands: '', targetType: 'VICTIM', targetDevice: '',
                  priority: 1, autoExecute: false, enabled: true, description: '',
                });
              }
              setShowAddPlaybook(!showAddPlaybook);
            }}
            className="flex items-center gap-2 px-4 py-2 bg-bg-primary border border-gray-700
                     rounded-lg text-sm text-gray-400 hover:text-accent hover:border-accent/30 transition-colors">
            <Plus size={14} /> {
              showAddPlaybook
                ? 'Cancel'
                : (editingPlaybookId ? 'Edit Playbook' : 'Add Playbook')
            }
          </button>
        </div>
      </Section>

      {/* ═══ C. Trigger System ═══ */}
      <Section title="Trigger Rules">
        <div className="p-4 space-y-2">
          {triggerRules.length === 0 && (
            <p className="text-gray-600 text-sm">No rules configured</p>
          )}
          {triggerRules.map(rule => {
            // Parse scope from deviceNames (comma-separated)
            const scope = rule.deviceNames
              ? rule.deviceNames.split(',').map(s => s.trim()).filter(Boolean)
              : [];
            const scopeLabel = scope.length === 0
              ? 'All devices'
              : scope.join(', ');

            return (
              <div key={rule.id} className="bg-bg-primary rounded-lg p-3">
                <div className="flex items-center gap-3">
                  <span className="text-sm text-gray-300 font-mono flex-1">
                    IF <span className="text-accent">{rule.metric}</span>{' '}
                    {rule.conditionOp} <span className="text-warning">{rule.value}</span>
                  </span>
                  <span className={`text-xs px-2 py-1 rounded ${
                    rule.severity === 'CRITICAL' ? 'bg-danger/10 text-danger' : 'bg-warning/10 text-warning'
                  }`}>{rule.severity}</span>
                  <button onClick={() => handleDeleteRule(rule.id)}
                    className="p-1 text-gray-500 hover:text-danger transition-colors">
                    <Trash2 size={14} />
                  </button>
                </div>
                <div className="flex items-center gap-2 mt-2 text-[11px]">
                  <span className="text-gray-500">Scope:</span>
                  <span className={`font-mono ${scope.length === 0 ? 'text-gray-400' : 'text-accent'}`}>
                    {scopeLabel}
                  </span>
                </div>
              </div>
            );
          })}

          {showAddRule && (
            <div className="bg-bg-primary rounded-lg p-3 space-y-3">
              {/* Row 1: condition */}
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-sm text-gray-400">IF</span>
                <MetricAutocomplete
                  value={newRule.metric}
                  onChange={(v) => setNewRule(p => ({ ...p, metric: v }))}
                  availableMetrics={availableMetrics}
                />
                <select value={newRule.conditionOp}
                  onChange={e => setNewRule(p => ({ ...p, conditionOp: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1 text-sm text-gray-300">
                  <option value=">">{'>'}</option>
                  <option value="<">{'<'}</option>
                  <option value=">=">{'>='}</option>
                  <option value="<=">{'<='}</option>
                </select>
                <input placeholder="value" value={newRule.value}
                  onChange={e => setNewRule(p => ({ ...p, value: e.target.value }))}
                  className="w-20 bg-bg-card border border-gray-700 rounded px-2 py-1 text-sm text-gray-200 font-mono" />
                <select value={newRule.severity}
                  onChange={e => setNewRule(p => ({ ...p, severity: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1 text-sm text-gray-300">
                  <option value="WARNING">WARNING</option>
                  <option value="CRITICAL">CRITICAL</option>
                </select>
              </div>

              {/* Row 2: device scope */}
              <div className="pt-2 border-t border-gray-700">
                <p className="text-xs text-gray-500 mb-2">Apply to:</p>
                <div className="space-y-2">
                  {/* All devices toggle */}
                  <label className="flex items-center gap-2 cursor-pointer text-sm">
                    <input
                      type="radio"
                      checked={newRule.selectedDevices.length === 0}
                      onChange={() => setNewRule(p => ({ ...p, selectedDevices: [] }))}
                      className="accent-accent"
                    />
                    <span className="text-gray-300">All devices</span>
                  </label>
                  {/* Selected devices */}
                  <label className="flex items-center gap-2 cursor-pointer text-sm">
                    <input
                      type="radio"
                      checked={newRule.selectedDevices.length > 0}
                      onChange={() => {
                        // When switching to "selected", default to first device
                        if (newRule.selectedDevices.length === 0) {
                          setNewRule(p => ({ ...p, selectedDevices: [ALL_DEVICES[0]] }));
                        }
                      }}
                      className="accent-accent"
                    />
                    <span className="text-gray-300">Specific devices:</span>
                  </label>
                  {newRule.selectedDevices.length > 0 && (
                    <div className="ml-6 grid grid-cols-2 gap-x-6 gap-y-1">
                      {DEVICE_GROUPS.map(group => (
                        <div key={group.label} className="space-y-1">
                          <p className="text-[10px] uppercase text-gray-600 tracking-wider">
                            {group.label}
                          </p>
                          {group.devices.map(device => (
                            <label key={device} className="flex items-center gap-2 cursor-pointer text-xs">
                              <input
                                type="checkbox"
                                checked={newRule.selectedDevices.includes(device)}
                                onChange={() => {
                                  setNewRule(p => {
                                    const sel = p.selectedDevices.includes(device)
                                      ? p.selectedDevices.filter(d => d !== device)
                                      : [...p.selectedDevices, device];
                                    return { ...p, selectedDevices: sel };
                                  });
                                }}
                                className="accent-accent"
                              />
                              <span className="text-gray-300 font-mono">{device}</span>
                            </label>
                          ))}
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>

              <div className="flex justify-end pt-2">
                <button onClick={handleAddRule}
                  className="px-4 py-1.5 bg-accent/10 text-accent rounded text-sm hover:bg-accent/20">
                  Save Rule
                </button>
              </div>
            </div>
          )}

          <button onClick={() => setShowAddRule(!showAddRule)}
            className="flex items-center gap-2 mt-2 px-4 py-2 bg-bg-primary border border-gray-700
                     rounded-lg text-sm text-gray-400 hover:text-accent hover:border-accent/30 transition-colors">
            <Plus size={14} /> {showAddRule ? 'Cancel' : 'Add Rule'}
          </button>
        </div>
      </Section>

      {/* ═══ C.2 Quick Commands (Terminal shortcuts) ═══ */}
      <Section title="Quick Commands">
        <div className="p-4 space-y-2">
          <p className="text-xs text-gray-500 mb-2">
            Shortcut buttons shown on the Terminal page. Each runs a shell command on the selected device.
          </p>

          {quickCommands.length === 0 && (
            <p className="text-gray-600 text-sm">No quick commands configured</p>
          )}

          {quickCommands.map(qc => (
            <div key={qc.id} className="bg-bg-primary rounded-lg p-3">
              <div className="flex items-center gap-3">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`text-sm font-medium ${qc.enabled ? 'text-gray-200' : 'text-gray-500 line-through'}`}>
                      {qc.name}
                    </span>
                    <span className={`text-[10px] px-1.5 py-0.5 rounded ${
                      qc.deviceType === 'router' ? 'bg-blue-500/10 text-blue-400' :
                      qc.deviceType === 'server' ? 'bg-purple-500/10 text-purple-400' :
                      'bg-gray-500/10 text-gray-400'
                    }`}>
                      {qc.deviceType || 'all devices'}
                    </span>
                  </div>
                  <p className="text-xs font-mono text-gray-500 truncate mt-0.5" title={qc.command}>
                    {qc.command}
                  </p>
                </div>
                <button
                  onClick={() => handleToggleQuickCmd(qc)}
                  className={`text-[10px] px-2 py-1 rounded transition-colors ${
                    qc.enabled
                      ? 'bg-accent/10 text-accent hover:bg-accent/20'
                      : 'bg-gray-700 text-gray-400 hover:bg-gray-600'
                  }`}
                  title={qc.enabled ? 'Click to disable' : 'Click to enable'}
                >
                  {qc.enabled ? 'ON' : 'OFF'}
                </button>
                <button
                  onClick={() => handleDeleteQuickCmd(qc.id)}
                  className="p-1 text-gray-500 hover:text-danger transition-colors"
                  title="Delete"
                >
                  <Trash2 size={14} />
                </button>
              </div>
            </div>
          ))}

          {showAddQuickCmd && (
            <div className="bg-bg-primary rounded-lg p-3 space-y-2">
              <div className="grid grid-cols-2 gap-2">
                <input
                  placeholder="Button label (e.g. List connections)"
                  value={newQuickCmd.name}
                  onChange={e => setNewQuickCmd(p => ({ ...p, name: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-3 py-2 text-sm text-gray-200"
                />
                <select
                  value={newQuickCmd.deviceType}
                  onChange={e => setNewQuickCmd(p => ({ ...p, deviceType: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-3 py-2 text-sm text-gray-300"
                >
                  <option value="">All devices</option>
                  <option value="router">Routers only</option>
                  <option value="server">Servers only</option>
                </select>
              </div>
              <input
                placeholder="Shell command (e.g. ss -tunap)"
                value={newQuickCmd.command}
                onChange={e => setNewQuickCmd(p => ({ ...p, command: e.target.value }))}
                className="w-full bg-bg-card border border-gray-700 rounded px-3 py-2 text-sm text-gray-200 font-mono"
              />
              <div className="flex items-center gap-2">
                <label className="text-xs text-gray-500">Sort order:</label>
                <input
                  type="number"
                  value={newQuickCmd.sortOrder}
                  onChange={e => setNewQuickCmd(p => ({ ...p, sortOrder: e.target.value }))}
                  className="w-20 bg-bg-card border border-gray-700 rounded px-2 py-1 text-sm text-gray-200"
                />
                <span className="text-[10px] text-gray-600">(lower = shown first)</span>
                <div className="flex-1" />
                <button onClick={handleAddQuickCmd}
                  className="px-3 py-1.5 bg-accent/10 text-accent rounded text-sm hover:bg-accent/20">
                  Save
                </button>
              </div>
            </div>
          )}

          <button onClick={() => setShowAddQuickCmd(!showAddQuickCmd)}
            className="flex items-center gap-2 mt-2 px-4 py-2 bg-bg-primary border border-gray-700
                     rounded-lg text-sm text-gray-400 hover:text-accent hover:border-accent/30 transition-colors">
            <Plus size={14} /> {showAddQuickCmd ? 'Cancel' : 'Add Quick Command'}
          </button>
        </div>
      </Section>

      {/* ═══ D. Email Notifications ═══ */}
      <Section title="Email Notifications">
        {!emailLoaded ? (
          <div className="p-4 text-sm text-gray-500">Loading email settings…</div>
        ) : (
          <>
            <Row label="Enable email notifications"
                 desc="Master switch — daily digest + instant alerts">
              <Toggle
                enabled={!!emailConfig?.enabled}
                onChange={() => patchEmailConfig({ enabled: !emailConfig?.enabled })}
              />
            </Row>

            {emailConfig?.enabled && (
              <>
                {/* ── Recipient & Sender ─────────────────────────── */}
                <Row label="Recipient" desc="Where digest + alerts are sent">
                  <input type="email"
                    value={emailConfig.recipient || ''}
                    onChange={(e) => patchEmailConfig({ recipient: e.target.value })}
                    placeholder="admin@example.com"
                    className="w-72 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>
                <Row label="Sender email" desc="Address that appears in the From header">
                  <input type="email"
                    value={emailConfig.senderEmail || ''}
                    onChange={(e) => patchEmailConfig({ senderEmail: e.target.value })}
                    placeholder="telecomai.pfe@gmail.com"
                    className="w-72 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>
                <Row label="Sender name" desc="Display name shown to the recipient">
                  <input type="text"
                    value={emailConfig.senderName || ''}
                    onChange={(e) => patchEmailConfig({ senderName: e.target.value })}
                    placeholder="TelecomAI"
                    className="w-72 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>

                {/* ── SMTP Configuration ──────────────────────────── */}
                <div className="px-4 pt-4 pb-1">
                  <p className="text-xs uppercase tracking-wider text-gray-500 font-semibold">
                    SMTP Configuration
                  </p>
                  <p className="text-xs text-gray-500 mt-1">
                    Use a Gmail App Password (16 chars) — not your regular Gmail password.
                  </p>
                </div>
                <Row label="SMTP host" desc="Outgoing mail server">
                  <input type="text"
                    value={emailConfig.smtpHost || ''}
                    onChange={(e) => patchEmailConfig({ smtpHost: e.target.value })}
                    placeholder="smtp.gmail.com"
                    className="w-72 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>
                <Row label="SMTP port" desc="587 (TLS) or 465 (SSL)">
                  <input type="number"
                    value={emailConfig.smtpPort ?? 587}
                    onChange={(e) => patchEmailConfig({ smtpPort: parseInt(e.target.value, 10) || 587 })}
                    className="w-24 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>
                <Row label="SMTP user" desc="Usually the same as Sender email">
                  <input type="text"
                    value={emailConfig.smtpUser || ''}
                    onChange={(e) => patchEmailConfig({ smtpUser: e.target.value })}
                    placeholder="telecomai.pfe@gmail.com"
                    className="w-72 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200" />
                </Row>
                <Row label="SMTP password" desc="Gmail App Password (16 characters, no spaces)">
                  <div className="flex items-center gap-2">
                    <input
                      type={emailShowPassword ? 'text' : 'password'}
                      value={emailConfig.smtpPassword || ''}
                      onChange={(e) => patchEmailConfig({ smtpPassword: e.target.value })}
                      placeholder="●●●●●●●●●●●●●●●●"
                      className="w-60 bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-200 font-mono" />
                    <button type="button"
                      onClick={() => setEmailShowPassword(!emailShowPassword)}
                      className="text-xs text-gray-400 hover:text-gray-200 px-2 py-1 border border-gray-700 rounded">
                      {emailShowPassword ? 'Hide' : 'Show'}
                    </button>
                  </div>
                </Row>

                {/* ── Daily Digest ────────────────────────────────── */}
                <div className="px-4 pt-4 pb-1">
                  <p className="text-xs uppercase tracking-wider text-gray-500 font-semibold">
                    Periodic Digest
                  </p>
                </div>
                <Row label="Frequency" desc="How often the digest is sent">
                  <select
                    value={emailConfig.frequency || 'DAILY'}
                    onChange={(e) => patchEmailConfig({ frequency: e.target.value })}
                    className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300">
                    <option value="DAILY">Every day</option>
                    <option value="EVERY_2_DAYS">Every 2 days</option>
                    <option value="WEEKLY">Once a week</option>
                  </select>
                </Row>
                {emailConfig.frequency === 'WEEKLY' && (
                  <Row label="Day of week" desc="Which day to send the weekly digest">
                    <select
                      value={emailConfig.weeklyDay || 'MONDAY'}
                      onChange={(e) => patchEmailConfig({ weeklyDay: e.target.value })}
                      className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300">
                      <option value="MONDAY">Monday</option>
                      <option value="TUESDAY">Tuesday</option>
                      <option value="WEDNESDAY">Wednesday</option>
                      <option value="THURSDAY">Thursday</option>
                      <option value="FRIDAY">Friday</option>
                      <option value="SATURDAY">Saturday</option>
                      <option value="SUNDAY">Sunday</option>
                    </select>
                  </Row>
                )}
                <Row label="Send time" desc="Local server time (HH:mm)">
                  <input type="time"
                    value={emailConfig.digestTime || '08:00'}
                    onChange={(e) => patchEmailConfig({ digestTime: e.target.value })}
                    className="bg-bg-primary border border-gray-700 rounded-lg px-3 py-2 text-sm text-gray-300" />
                </Row>

                {/* ── Instant Critical Alerts ─────────────────────── */}
                <div className="px-4 pt-4 pb-1">
                  <p className="text-xs uppercase tracking-wider text-gray-500 font-semibold">
                    Instant Critical Alerts
                  </p>
                  <p className="text-xs text-gray-500 mt-1">
                    Emails sent immediately when an event fires. Rate-limited to one every 5 minutes.
                  </p>
                </div>
                <Row label="On AI-detected attack" desc="🔴 Immediate alert when AI flags a new attack">
                  <Toggle
                    enabled={!!emailConfig.instantOnAiAttack}
                    onChange={() => patchEmailConfig({ instantOnAiAttack: !emailConfig.instantOnAiAttack })}
                  />
                </Row>
                <Row label="On device offline" desc="⚫ Immediate alert when a device stops responding">
                  <Toggle
                    enabled={!!emailConfig.instantOnDeviceOffline}
                    onChange={() => patchEmailConfig({ instantOnDeviceOffline: !emailConfig.instantOnDeviceOffline })}
                  />
                </Row>
                <Row label="On trigger rule fired" desc="🟡 Immediate alert when a threshold rule trips">
                  <Toggle
                    enabled={!!emailConfig.instantOnTriggerRule}
                    onChange={() => patchEmailConfig({ instantOnTriggerRule: !emailConfig.instantOnTriggerRule })}
                  />
                </Row>

                {/* ── Digest Sections ─────────────────────────────── */}
                <div className="px-4 pt-4 pb-1">
                  <p className="text-xs uppercase tracking-wider text-gray-500 font-semibold">
                    Digest Sections
                  </p>
                  <p className="text-xs text-gray-500 mt-1">Pick what to include in the periodic digest email.</p>
                </div>
                <Row label="Executive summary" desc="Incident counts + availability headline">
                  <Toggle
                    enabled={!!emailConfig.sectionExecutiveSummary}
                    onChange={() => patchEmailConfig({ sectionExecutiveSummary: !emailConfig.sectionExecutiveSummary })}
                  />
                </Row>
                <Row label="Resource usage" desc="Per-device avg/max CPU and avg memory">
                  <Toggle
                    enabled={!!emailConfig.sectionResourceUsage}
                    onChange={() => patchEmailConfig({ sectionResourceUsage: !emailConfig.sectionResourceUsage })}
                  />
                </Row>
                <Row label="SLA report" desc="Per-device uptime % and downtime duration">
                  <Toggle
                    enabled={!!emailConfig.sectionSlaReport}
                    onChange={() => patchEmailConfig({ sectionSlaReport: !emailConfig.sectionSlaReport })}
                  />
                </Row>
                <Row label="Latest events" desc="Last 10 events table (time / device / source / type)">
                  <Toggle
                    enabled={!!emailConfig.sectionLatestEvents}
                    onChange={() => patchEmailConfig({ sectionLatestEvents: !emailConfig.sectionLatestEvents })}
                  />
                </Row>
                <Row label="Auto-mitigations" desc="Playbooks executed automatically in this period">
                  <Toggle
                    enabled={!!emailConfig.sectionMitigations}
                    onChange={() => patchEmailConfig({ sectionMitigations: !emailConfig.sectionMitigations })}
                  />
                </Row>
              </>
            )}

            {/* ── Action buttons ─────────────────────────────────── */}
            <div className="p-4 flex items-center gap-3">
              <button
                onClick={handleSaveEmail}
                disabled={emailSaving || !emailConfig}
                className="flex items-center gap-2 px-4 py-2 bg-accent/10 text-accent rounded-lg text-sm hover:bg-accent/20 disabled:opacity-40">
                <Save size={14} />
                {emailSaving ? 'Saving…' : 'Save'}
              </button>
              <button
                onClick={handleTestEmail}
                disabled={emailTesting || !emailConfig?.enabled}
                title={!emailConfig?.enabled ? 'Enable email notifications first' : 'Send a real digest covering the last 24h'}
                className="flex items-center gap-2 px-4 py-2 bg-bg-primary text-gray-300 border border-gray-700 rounded-lg text-sm hover:bg-bg-card disabled:opacity-40">
                {emailTesting ? 'Sending…' : 'Send test email'}
              </button>
              {emailMessage && (
                <span className={`text-sm ${emailMessage.type === 'ok' ? 'text-success' : 'text-danger'}`}>
                  {emailMessage.text}
                </span>
              )}
            </div>
          </>
        )}
      </Section>

      {/* ═══ F. User Management ═══ */}
      <Section title="User Management">
        <div className="p-4 space-y-2">
          {users.length === 0 && (
            <p className="text-gray-600 text-sm">No users found</p>
          )}
          {users.map(u => (
            <div key={u.id} className="flex items-center gap-3 bg-bg-primary rounded-lg p-3">
              <div className="flex-1">
                <span className="text-sm text-gray-200">{u.fullName || u.username}</span>
                <span className="text-xs text-gray-500 ml-2">@{u.username}</span>
              </div>
              <span className={`text-xs px-2 py-1 rounded ${
                u.role === 'ADMIN' ? 'bg-accent/10 text-accent' : 'bg-gray-700 text-gray-300'
              }`}>{u.role}{u.username === 'admin' ? ' ★' : ''}</span>
              {u.username !== user?.username && u.username !== 'admin' && (
                <button onClick={() => handleDeleteUser(u.id, u.username)}
                  className="p-1 text-gray-500 hover:text-danger transition-colors">
                  <Trash2 size={14} />
                </button>
              )}
            </div>
          ))}

          {showAddUser && (
            <div className="bg-bg-primary rounded-lg p-3 space-y-2">
              {userError && <p className="text-xs text-danger">{userError}</p>}
              <div className="grid grid-cols-2 gap-2">
                <input placeholder="Username" value={newUser.username}
                  onChange={e => setNewUser(p => ({ ...p, username: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" />
                <input placeholder="Full Name" value={newUser.fullName}
                  onChange={e => setNewUser(p => ({ ...p, fullName: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" />
                <input type="password" placeholder="Password" value={newUser.password}
                  onChange={e => setNewUser(p => ({ ...p, password: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-200" />
                <select value={newUser.role}
                  onChange={e => setNewUser(p => ({ ...p, role: e.target.value }))}
                  className="bg-bg-card border border-gray-700 rounded px-2 py-1.5 text-sm text-gray-300">
                  <option value="VIEWER">VIEWER</option>
                  <option value="ADMIN">ADMIN</option>
                </select>
              </div>
              <button onClick={handleAddUser}
                className="px-3 py-1.5 bg-accent/10 text-accent rounded text-sm hover:bg-accent/20">
                Create User
              </button>
            </div>
          )}

          <button onClick={() => setShowAddUser(!showAddUser)}
            className="flex items-center gap-2 mt-2 px-4 py-2 bg-bg-primary border border-gray-700
                     rounded-lg text-sm text-gray-400 hover:text-accent hover:border-accent/30 transition-colors">
            <UserPlus size={14} /> {showAddUser ? 'Cancel' : 'Add User'}
          </button>
        </div>
      </Section>

      {/* ═══ G. System Status ═══ */}
      <Section title="System Status">
        <Row label="Spring Boot" desc="Backend API"><StatusBadge connected={backendConnected} /></Row>
        <Row label="MySQL" desc="Database"><StatusBadge connected={backendConnected} /></Row>
        <Row label="Flask AI" desc="AI Engine"><StatusBadge connected={aiConnected} /></Row>
      </Section>
    </div>
  );
}

/* ═══ Helper Components ═══ */

function Section({ title, children }) {
  return (
    <div className="bg-bg-card rounded-xl border border-gray-800 overflow-hidden">
      <div className="p-4 border-b border-gray-700">
        <h3 className="text-sm font-semibold text-gray-200">{title}</h3>
      </div>
      <div className="divide-y divide-gray-800">{children}</div>
    </div>
  );
}

function Row({ label, desc, children }) {
  return (
    <div className="flex items-center justify-between p-4">
      <div>
        <p className="text-sm text-gray-200">{label}</p>
        {desc && <p className="text-xs text-gray-500 mt-0.5">{desc}</p>}
      </div>
      {children}
    </div>
  );
}

function Toggle({ enabled, onChange }) {
  return (
    <button onClick={onChange}
      className={`w-11 h-6 rounded-full transition-colors relative ${enabled ? 'bg-accent' : 'bg-gray-600'}`}>
      <span className={`absolute top-1 w-4 h-4 rounded-full bg-white transition-transform ${
        enabled ? 'left-6' : 'left-1'
      }`} />
    </button>
  );
}

function StatusBadge({ connected }) {
  return (
    <span className={`flex items-center gap-2 text-sm ${connected ? 'text-success' : 'text-danger'}`}>
      <span className={`w-2 h-2 rounded-full ${connected ? 'bg-success' : 'bg-danger'}`}></span>
      {connected ? 'Connected' : 'Disconnected'}
    </span>
  );
}

/**
 * defaultEmailConfig — initial shape used when the backend hasn't created a
 * row yet, or when the GET request fails. Mirrors the EmailConfig entity
 * defaults on the server so the form renders cleanly with sane values.
 */
function defaultEmailConfig() {
  return {
    id: 1,
    enabled: false,
    recipient: '',
    senderEmail: '',
    senderName: 'TelecomAI',
    smtpHost: 'smtp.gmail.com',
    smtpPort: 587,
    smtpUser: '',
    smtpPassword: '',
    frequency: 'DAILY',
    weeklyDay: 'MONDAY',
    digestTime: '08:00',
    instantOnAiAttack: true,
    instantOnDeviceOffline: true,
    instantOnTriggerRule: false,
    sectionExecutiveSummary: true,
    sectionResourceUsage: true,
    sectionSlaReport: true,
    sectionLatestEvents: true,
    sectionMitigations: true,
  };
}

/**
 * MetricAutocomplete — combobox for metric name selection
 *
 * Solves the "silent failure" bug: previously user could type "cpu_usage"
 * (snake_case) instead of "cpuUsage" (camelCase). The backend would receive it,
 * extractMetricValue() would silently return null, and the rule never fired.
 *
 * Now user types → suggestions filter live → can only pick valid metric names.
 */
function MetricAutocomplete({ value, onChange, availableMetrics }) {
  const [isOpen, setIsOpen] = useState(false);
  const wrapperRef = useRef(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    const onClickOutside = (e) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', onClickOutside);
    return () => document.removeEventListener('mousedown', onClickOutside);
  }, []);

  // Build flat list with group info for filtering
  const allMetrics = [
    ...(availableMetrics.system || []).map(m => ({ ...m, group: 'System' })),
    ...(availableMetrics.tcp || []).map(m => ({ ...m, group: 'TCP/Network' })),
    ...(availableMetrics.security || []).map(m => ({ ...m, group: 'Security' })),
  ];

  const query = (value || '').toLowerCase();
  const filtered = query
    ? allMetrics.filter(m =>
        m.name.toLowerCase().includes(query) ||
        m.label.toLowerCase().includes(query))
    : allMetrics;

  // Group filtered items by category
  const byGroup = filtered.reduce((acc, m) => {
    (acc[m.group] = acc[m.group] || []).push(m);
    return acc;
  }, {});

  const isValid = !value || allMetrics.some(m => m.name === value);

  return (
    <div ref={wrapperRef} className="relative">
      <input
        type="text"
        value={value}
        onChange={(e) => { onChange(e.target.value); setIsOpen(true); }}
        onFocus={() => setIsOpen(true)}
        placeholder="metric name"
        className={`w-52 bg-bg-card border rounded px-2 py-1 text-sm font-mono ${
          isValid
            ? 'border-gray-700 text-gray-200'
            : 'border-danger/50 text-danger'
        }`}
      />

      {isOpen && filtered.length > 0 && (
        <div className="absolute left-0 top-full mt-1 w-80 max-h-72 overflow-y-auto
                        bg-bg-card border border-gray-700 rounded-lg shadow-lg z-50">
          {Object.entries(byGroup).map(([group, items]) => (
            <div key={group}>
              <div className="px-3 py-1 text-[10px] uppercase tracking-wider text-gray-500
                              bg-bg-primary border-b border-gray-800">
                {group}
              </div>
              {items.map((m) => (
                <button
                  key={m.name}
                  type="button"
                  onMouseDown={(e) => {
                    e.preventDefault();
                    onChange(m.name);
                    setIsOpen(false);
                  }}
                  className={`w-full text-left px-3 py-1.5 hover:bg-accent/10 transition-colors ${
                    m.name === value ? 'bg-accent/10' : ''
                  }`}
                >
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-xs font-mono text-accent">{m.name}</span>
                    {m.unit && (
                      <span className="text-[10px] text-gray-500 font-mono">{m.unit}</span>
                    )}
                  </div>
                  <p className="text-[11px] text-gray-400 truncate">{m.label}</p>
                </button>
              ))}
            </div>
          ))}
        </div>
      )}

      {isOpen && filtered.length === 0 && (
        <div className="absolute left-0 top-full mt-1 w-80 bg-bg-card border border-gray-700
                        rounded-lg shadow-lg z-50 p-3">
          <p className="text-xs text-gray-500">No metrics match "{value}"</p>
        </div>
      )}
    </div>
  );
}

export default Settings;