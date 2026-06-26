/**
 * WebSocket Service — STOMP over SockJS
 *
 * Topics:
 *   /topic/metrics        → new metrics from devices
 *   /topic/predictions    → new AI predictions
 *   /topic/notifications  → attack alerts, escalation, ended
 */

import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const WS_URL = '/ws';

let stompClient = null;
let connected = false;
const pendingSubscriptions = [];
const activeSubscriptions = [];

// ── Connection state listeners ────────────────────────────
let onConnectCallback = null;
let onDisconnectCallback = null;

export function onConnectionChange(onConnect, onDisconnect) {
  onConnectCallback = onConnect;
  onDisconnectCallback = onDisconnect;
}

export function isConnected() {
  return connected;
}

// ── Connect ───────────────────────────────────────────────
export function connectWebSocket() {
  if (stompClient) return;

  stompClient = new Client({
    webSocketFactory: () => new SockJS(WS_URL),
    reconnectDelay: 5000,
    debug: () => {},

    onConnect: () => {
      console.log('✅ WebSocket connected');
      connected = true;
      if (onConnectCallback) onConnectCallback();

      // Subscribe pending
      pendingSubscriptions.forEach(({ topic, callback }) => {
        doSubscribe(topic, callback);
      });
      pendingSubscriptions.length = 0;

      // Re-subscribe active
      activeSubscriptions.forEach(({ topic, callback }) => {
        doSubscribe(topic, callback);
      });
    },

    onDisconnect: () => {
      console.log('⬛ WebSocket disconnected');
      connected = false;
      if (onDisconnectCallback) onDisconnectCallback();
    },

    onStompError: (frame) => {
      console.error('❌ WebSocket error:', frame.headers.message);
      connected = false;
    },
  });

  stompClient.activate();
}

// ── Subscribe ─────────────────────────────────────────────
function doSubscribe(topic, callback) {
  if (!stompClient || !stompClient.connected) return;
  stompClient.subscribe(topic, (message) => {
    try {
      const data = JSON.parse(message.body);
      callback(data);
    } catch (e) {
      console.error('WS parse error:', e);
    }
  });
}

export function subscribe(topic, callback) {
  // Track for reconnect
  const exists = activeSubscriptions.find(
    (s) => s.topic === topic && s.callback === callback
  );
  if (!exists) {
    activeSubscriptions.push({ topic, callback });
  }

  if (stompClient && stompClient.connected) {
    doSubscribe(topic, callback);
  } else {
    pendingSubscriptions.push({ topic, callback });
  }
}

// ── Disconnect ────────────────────────────────────────────
export function disconnectWebSocket() {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
    connected = false;
  }
}
