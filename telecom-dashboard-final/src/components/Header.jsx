import React, { useState, useEffect } from 'react';
import { Bell, LogOut } from 'lucide-react';
import { useLocation } from 'react-router-dom';
import { getUnacknowledged, acknowledgeSession } from '../services/api';

const PAGE_TITLES = {
  '/': 'Home',
  '/monitoring': 'Monitoring',
  '/security': 'Security / AI',
  '/terminal': 'Terminal',
  '/settings': 'Settings',
};

function Header({ notifications = [], onClearNotification, onClearAll, user, onLogout }) {
  const [showDropdown, setShowDropdown] = useState(false);
  const [unackedSessions, setUnackedSessions] = useState([]);
  const location = useLocation();
  const pageTitle = PAGE_TITLES[location.pathname] || 'Dashboard';

  // Fetch unacknowledged sessions on mount + when notifications change
  useEffect(() => {
    getUnacknowledged()
      .then(setUnackedSessions)
      .catch(() => {});
  }, [notifications]);

  // Total badge count = WebSocket notifications + unacked sessions
  const totalCount = notifications.length + unackedSessions.length;

  const handleAcknowledge = async (sessionId) => {
    try {
      await acknowledgeSession(sessionId);
      setUnackedSessions((prev) => prev.filter((s) => s.id !== sessionId));
    } catch (err) {
      console.error('Acknowledge failed:', err);
    }
  };

  // Clear all: ACK all unacked sessions + clear all WebSocket notifications
  const handleClearAll = async () => {
    // Acknowledge all unacked sessions
    const sessions = [...unackedSessions];
    await Promise.all(
      sessions.map((s) =>
        acknowledgeSession(s.id).catch(() => {})
      )
    );
    setUnackedSessions([]);
    // Clear all WebSocket notifications (handled by parent)
    if (onClearAll) onClearAll();
  };

  const typeIcon = {
    NEW_ATTACK: '🚨',
    ATTACK_ENDED: '✅',
    ESCALATION: '⚠️',
  };

  return (
    <header className="h-14 bg-bg-card border-b border-gray-800 flex items-center justify-between px-6">
      <h2 className="text-gray-200 font-medium">{pageTitle}</h2>

      {/* Right side: bell + user + logout */}
      <div className="flex items-center gap-4">
        {/* Notification bell */}
        <div className="relative">
          <button
            onClick={() => setShowDropdown(!showDropdown)}
            className="relative p-2 text-gray-400 hover:text-gray-200 transition-colors"
          >
            <Bell size={20} />
            {totalCount > 0 && (
              <span className="absolute -top-0.5 -right-0.5 w-4 h-4 bg-danger rounded-full
                             text-[10px] text-white flex items-center justify-center font-bold">
                {totalCount > 9 ? '9+' : totalCount}
              </span>
            )}
          </button>

          {showDropdown && (
            <div className="absolute right-0 top-12 w-96 bg-bg-card border border-gray-700
                          rounded-lg shadow-xl z-50 overflow-hidden">
              <div className="p-3 border-b border-gray-700 flex items-center justify-between">
                <p className="text-sm font-medium text-gray-200">Notifications</p>
                <div className="flex items-center gap-3">
                  {totalCount > 0 && (
                    <>
                      <span className="text-xs text-gray-500">{totalCount} unread</span>
                      <button
                        onClick={handleClearAll}
                        className="text-xs px-2 py-0.5 rounded bg-gray-700/50 text-gray-300
                                 hover:bg-gray-700 hover:text-gray-100 transition-colors"
                        title="Clear all notifications"
                      >
                        Clear All
                      </button>
                    </>
                  )}
                </div>
            </div>
            <div className="max-h-80 overflow-y-auto">
              {/* Unacknowledged attack sessions */}
              {unackedSessions.map((session) => (
                <div key={`s-${session.id}`} className="p-3 border-b border-gray-800 hover:bg-bg-hover">
                  <div className="flex items-center justify-between mb-1">
                    <div className="flex items-center gap-2">
                      <span className="w-2 h-2 rounded-full bg-danger"></span>
                      <span className="text-xs font-mono text-danger">{session.attackType}</span>
                      <span className="text-xs text-gray-500">Lvl {session.escalationLevel}</span>
                    </div>
                    <button
                      onClick={() => handleAcknowledge(session.id)}
                      className="text-xs px-2 py-0.5 rounded bg-accent/10 text-accent
                               hover:bg-accent/20 transition-colors"
                    >
                      ACK
                    </button>
                  </div>
                  <p className="text-sm text-gray-300">
                    {session.deviceName}::{session.interfaceName}
                  </p>
                  <p className="text-xs text-gray-500 mt-0.5">
                    {session.avgConfidence ? `${(session.avgConfidence * 100).toFixed(0)}% confidence` : ''}
                    {session.durationFormatted ? ` · ${session.durationFormatted}` : ''}
                  </p>
                </div>
              ))}

              {/* Live WebSocket notifications */}
              {notifications.map((notif, i) => (
                <div
                  key={`n-${i}`}
                  className="p-3 border-b border-gray-800 hover:bg-bg-hover cursor-pointer"
                  onClick={() => onClearNotification(i)}
                >
                  <div className="flex items-center gap-2 mb-1">
                    <span className="text-sm">{typeIcon[notif.type] || '🔔'}</span>
                    <span className="text-xs text-gray-500">
                      {notif.timestamp ? new Date(notif.timestamp).toLocaleTimeString() : 'now'}
                    </span>
                  </div>
                  <p className="text-sm text-gray-300">
                    {notif.type === 'NEW_ATTACK' &&
                      `${notif.attackType} on ${notif.deviceName}::${notif.interfaceName}`}
                    {notif.type === 'ATTACK_ENDED' &&
                      `${notif.attackType} ended on ${notif.deviceName} (${notif.duration})`}
                    {notif.type === 'ESCALATION' &&
                      `Escalated Lvl ${notif.oldLevel}→${notif.newLevel}: ${notif.attackType} on ${notif.deviceName}`}
                  </p>
                </div>
              ))}

              {totalCount === 0 && (
                <div className="p-6 text-center text-gray-600 text-sm">
                  No notifications
                </div>
              )}
            </div>
          </div>
        )}
      </div>

        {/* User info + Logout */}
        {user && (
          <div className="flex items-center gap-3 border-l border-gray-700 pl-4">
            <div className="text-right">
              <p className="text-sm text-gray-200">{user.fullName}</p>
              <p className="text-xs text-gray-500">{user.role}</p>
            </div>
            <button onClick={onLogout}
              className="p-2 text-gray-400 hover:text-danger transition-colors" title="Logout">
              <LogOut size={18} />
            </button>
          </div>
        )}
      </div>
    </header>
  );
}

export default Header;