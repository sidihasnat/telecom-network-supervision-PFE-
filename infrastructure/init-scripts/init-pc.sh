#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-pc.sh — LAN Client (pc1 OR pc2)
# ───────────────────────────────────────────────────────────────────
# يستخدم متغيرات بيئية:
#   $PC_NAME (e.g., "pc1")
#   $PC_IP   (e.g., "10.0.2.4")
# ═══════════════════════════════════════════════════════════════════

set +e

PC_NAME="${PC_NAME:-pc1}"
PC_IP="${PC_IP:-10.0.2.4}"

sleep 2

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── Network ────────────────────────────────────────────────────────
ip addr add ${PC_IP}/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.2.1 dev eth1 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── Background Traffic ─────────────────────────────────────────────
pkill -f "background_traffic" 2>/dev/null
sleep 1
sh /scripts/background_traffic.sh &

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py ${PC_NAME}" 2>/dev/null
sleep 1
python3 /scripts/metrics.py ${PC_NAME} client 10.0.2.1 &

echo "✅ ${PC_NAME} initialized (IP: ${PC_IP})"
