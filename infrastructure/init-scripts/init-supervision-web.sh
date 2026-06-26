#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-supervision-web.sh
# Nginx + React Dashboard (10.0.4.10)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 2

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.4.10/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.4.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
mkdir -p /var/run/sshd /run/nginx /var/log/nginx
/usr/sbin/sshd 2>/dev/null

# ── DNS resolution داخل Management Zone ───────────────────────────
echo "127.0.0.1 localhost" > /etc/hosts
echo "10.0.4.10 supervision-web" >> /etc/hosts
echo "10.0.4.11 supervision-app" >> /etc/hosts
echo "10.0.4.12 supervision-db" >> /etc/hosts
echo "10.0.4.13 supervision-ai" >> /etc/hosts

# ── Nginx ──────────────────────────────────────────────────────────
nginx -t && nginx

echo "✅ supervision-web initialized — Dashboard at http://10.0.4.10"
