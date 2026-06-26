#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-db.sh — LAN Database Server (10.0.2.3)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 3

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.2.3/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.2.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd /run/mysqld
/usr/sbin/sshd 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── MariaDB ────────────────────────────────────────────────────────
chown -R mysql:mysql /var/lib/mysql /run/mysqld 2>/dev/null
pkill mysqld 2>/dev/null
sleep 1
mysqld --user=mysql --datadir=/var/lib/mysql &

sleep 4

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py db-server" 2>/dev/null
sleep 1
python3 /scripts/metrics.py db-server server 10.0.2.1 &

echo "✅ db-server initialized"
