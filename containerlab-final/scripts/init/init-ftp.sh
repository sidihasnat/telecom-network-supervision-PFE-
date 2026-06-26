#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-ftp.sh — LAN FTP Server (10.0.2.2)
# ───────────────────────────────────────────────────────────────────
# يستبدل SSH server القديم. يقدم FTP service للاختبارات.
# - vsftpd على port 21
# - Brute force target (hydra ftp://...)
# - failed_logins يُقرأ من /var/log/vsftpd.log
# ═══════════════════════════════════════════════════════════════════

set +e

sleep 3

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.2.2/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.2.1 dev eth1 2>/dev/null

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── FTP Server (vsftpd) ────────────────────────────────────────────
mkdir -p /var/log
touch /var/log/vsftpd.log
chmod 644 /var/log/vsftpd.log

pkill vsftpd 2>/dev/null
sleep 1
/usr/sbin/vsftpd /etc/vsftpd/vsftpd.conf &

sleep 2

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py ftp-server" 2>/dev/null
sleep 1
python3 /scripts/metrics.py ftp-server server 10.0.2.1 &

echo "✅ ftp-server initialized (replaced ssh-server)"
