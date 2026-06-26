#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-supervision-app.sh
# Spring Boot Backend (10.0.4.11)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 3

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.4.11/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.4.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd /app/logs
/usr/sbin/sshd 2>/dev/null

# ── DNS (للوصول لـ supervision-db, supervision-ai) ────────────────
sh /scripts/set-dns.sh 2>/dev/null

# تأكد من DNS resolution داخل الشبكة
echo "127.0.0.1 localhost" > /etc/hosts
echo "10.0.4.10 supervision-web" >> /etc/hosts
echo "10.0.4.11 supervision-app" >> /etc/hosts
echo "10.0.4.12 supervision-db" >> /etc/hosts
echo "10.0.4.13 supervision-ai" >> /etc/hosts

# ── انتظار supervision-db يكون جاهزاً ──────────────────────────────
echo "Waiting for supervision-db..."
for i in 1 2 3 4 5 6 7 8 9 10; do
    if nc -z supervision-db 3306 2>/dev/null; then
        echo "✅ supervision-db is ready"
        break
    fi
    echo "  Attempt $i/10 — waiting 3s..."
    sleep 3
done

# ── Metrics (يراقب نفسه) ───────────────────────────────────────────
pkill -f "metrics.py supervision-app" 2>/dev/null
sleep 1
python3 /scripts/metrics.py supervision-app server 10.0.4.1 &

# ── Spring Boot ────────────────────────────────────────────────────
cd /app
echo "🚀 Starting Spring Boot..."
java -jar \
    -Dspring.config.location=file:/app/config/application.properties \
    -Xms256m -Xmx1g \
    /app/app.jar > /app/logs/spring-boot.log 2>&1 &

echo "✅ supervision-app initialized (Spring Boot starting...)"
