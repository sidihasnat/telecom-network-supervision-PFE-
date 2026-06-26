#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# init-supervision-ai.sh
# Flask AI Engine (10.0.4.13)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 3

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.4.13/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.4.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
mkdir -p /var/run/sshd /var/log
service rsyslog start 2>/dev/null
/usr/sbin/sshd 2>/dev/null

# ── DNS resolution ─────────────────────────────────────────────────
echo "127.0.0.1 localhost" > /etc/hosts
echo "10.0.4.10 supervision-web" >> /etc/hosts
echo "10.0.4.11 supervision-app" >> /etc/hosts
echo "10.0.4.12 supervision-db" >> /etc/hosts
echo "10.0.4.13 supervision-ai" >> /etc/hosts

# ── Flask AI ───────────────────────────────────────────────────────
cd /app

# اكتشف ملف الـ Flask الرئيسي


echo "🧠 Starting Flask AI"
python3 /app/app.py > /var/log/flask-ai.log 2>&1 &

echo "✅ supervision-ai initialized"
