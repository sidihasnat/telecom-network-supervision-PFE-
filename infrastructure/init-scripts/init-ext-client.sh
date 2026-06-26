#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-ext-client.sh — External Client (ext-client1 or ext-client2)
# ───────────────────────────────────────────────────────────────────
# يستخدم متغير $CLIENT_IP
# ═══════════════════════════════════════════════════════════════════

set +e

CLIENT_IP="${CLIENT_IP:-10.0.0.20}"

sleep 2

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── Network ────────────────────────────────────────────────────────
ip addr add ${CLIENT_IP}/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add default via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.1.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.2.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.3.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.4.0/24 via 10.0.0.1 dev eth1 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── Background Traffic ─────────────────────────────────────────────
pkill -f "background_traffic" 2>/dev/null
sleep 1
sh /scripts/background_traffic.sh &

echo "✅ ext-client initialized (IP: ${CLIENT_IP})"
