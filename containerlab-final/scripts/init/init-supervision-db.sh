#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# init-supervision-db.sh
# MySQL Database (10.0.4.12)
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 2

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.4.12/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.4.1 dev eth1 2>/dev/null

# ── DNS ─────────────────────────────────────────────────────────────
cat > /etc/hosts <<EOF
127.0.0.1 localhost
10.0.4.10 supervision-web
10.0.4.11 supervision-app
10.0.4.12 supervision-db
10.0.4.13 supervision-ai
EOF

# ── Services ───────────────────────────────────────────────────────
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── MySQL ──────────────────────────────────────────────────────────
# entrypoint الأصلي هو docker-entrypoint.sh، نشغله في الخلفية
echo "🗄️ Starting MySQL..."

# تأكد من permissions
chown -R mysql:mysql /var/lib/mysql 2>/dev/null

# لو MySQL ما كان شغال، شغله
if ! pgrep mysqld > /dev/null; then
    /usr/local/bin/docker-entrypoint.sh mysqld &
fi

# انتظار MySQL يكون جاهز
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
    if mysqladmin ping --silent 2>/dev/null; then
        echo "✅ MySQL is ready"
        break
    fi
    echo "  Waiting for MySQL ($i/12)..."
    sleep 3
done

echo "✅ supervision-db initialized"
