#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-web.sh — DMZ Web Server (10.0.1.2)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 2

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.1.2/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.1.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd /run/nginx
/usr/sbin/sshd 2>/dev/null
nginx 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py web-server" 2>/dev/null
sleep 1
python3 /scripts/metrics.py web-server server 10.0.1.1 &

echo "✅ web-server initialized"
