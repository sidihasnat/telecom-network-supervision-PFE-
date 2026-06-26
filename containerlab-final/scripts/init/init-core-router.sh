#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-core-router.sh — مع دعم Management Zone
# ───────────────────────────────────────────────────────────────────
# Interfaces:
#   eth1: WAN-INT (link to edge-router)
#   eth2-eth3: DMZ bridge (web, dns)
#   eth4-eth7: LAN bridge (ftp, db, pc1, pc2)
#   eth8-eth11: Management bridge (supervision-*)
# ═══════════════════════════════════════════════════════════════════

set +e

# ── Conntrack hardening ────────────────────────────────────────────
sysctl -w net.netfilter.nf_conntrack_max=2000000 2>/dev/null || true

# ── Services ───────────────────────────────────────────────────────
syslog-ng 2>/dev/null
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── WAN-INT ────────────────────────────────────────────────────────
ip addr add 10.0.3.2/24 dev eth1 2>/dev/null

# ── DMZ Bridge ─────────────────────────────────────────────────────
ip link add br-dmz type bridge 2>/dev/null
ip link set eth2 master br-dmz 2>/dev/null
ip link set eth3 master br-dmz 2>/dev/null
ip link set br-dmz up
ip addr add 10.0.1.1/24 dev br-dmz 2>/dev/null

# ── LAN Bridge ─────────────────────────────────────────────────────
ip link add br-lan type bridge 2>/dev/null
ip link set eth4 master br-lan 2>/dev/null
ip link set eth5 master br-lan 2>/dev/null
ip link set eth6 master br-lan 2>/dev/null
ip link set eth7 master br-lan 2>/dev/null
ip link set br-lan up
ip addr add 10.0.2.1/24 dev br-lan 2>/dev/null

# ── Management Bridge 🆕 ──────────────────────────────────────────
ip link add br-mgmt type bridge 2>/dev/null
ip link set eth8 master br-mgmt 2>/dev/null
ip link set eth9 master br-mgmt 2>/dev/null
ip link set eth10 master br-mgmt 2>/dev/null
ip link set eth11 master br-mgmt 2>/dev/null
ip link set br-mgmt up
ip addr add 10.0.4.1/24 dev br-mgmt 2>/dev/null
# ── IP Forwarding ──────────────────────────────────────────────────
sysctl -w net.ipv4.ip_forward=1

# ── Routes ─────────────────────────────────────────────────────────
ip route del default 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null
ip route add default via 10.0.3.1 dev eth1 2>/dev/null
ip route add 10.0.0.0/24 via 10.0.3.1 dev eth1 2>/dev/null

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# ACL: حماية Management Zone
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

iptables -I FORWARD 1 -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT

# 1. منع LAN من الوصول لـ Management (Defense in Depth)
iptables -A FORWARD -s 10.0.2.0/24 -d 10.0.4.0/24 -j DROP 2>/dev/null
# 2. منع DMZ من الوصول لـ Management
iptables -A FORWARD -s 10.0.1.0/24 -d 10.0.4.0/24 -j DROP 2>/dev/null
# 3. منع WAN من الوصول لـ Management
iptables -A FORWARD -s 10.0.0.0/24 -d 10.0.4.0/24 -j DROP 2>/dev/null
# 4. السماح لـ Management بالوصول للجميع (للمراقبة)
iptables -I FORWARD -s 10.0.4.0/24 -j ACCEPT 2>/dev/null
# 5. السماح لـ devices ترسل metrics لـ supervision-app
iptables -I FORWARD -d 10.0.4.11 -p tcp --dport 8080 -j ACCEPT 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── Metrics ────────────────────────────────────────────────────────
pkill -f "metrics.py core-router" 2>/dev/null
sleep 1
python3 /scripts/metrics.py core-router router 10.0.3.1 10.0.1.2 10.0.2.3 &

echo "✅ core-router initialized (with Management Zone)"
