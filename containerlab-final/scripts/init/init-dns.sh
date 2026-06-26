#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-dns.sh — DMZ DNS Server (10.0.1.3)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 2

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.1.3/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.1.1 dev eth1 2>/dev/null

# ── DNS Local: نفسه (يحل أي شيء عبر BIND) ──────────────────────────
echo "nameserver 127.0.0.1" > /etc/resolv.conf

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd /var/cache/bind /var/run/named
/usr/sbin/sshd 2>/dev/null

# ── Permissions BIND ───────────────────────────────────────────────
chmod -R 755 /etc/bind 2>/dev/null
chmod -R 777 /var/cache/bind /var/run/named 2>/dev/null

# ── BIND9 ──────────────────────────────────────────────────────────
pkill named 2>/dev/null
sleep 1
named -f -c /etc/bind/named.conf &

sleep 2

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py dns-server" 2>/dev/null
sleep 1
python3 /scripts/metrics.py dns-server server 10.0.1.1 &

echo "✅ dns-server initialized"
