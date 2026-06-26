#!/bin/sh
# ═══════════════════════════════════════════════════════════════════
# init-edge-router.sh
# ───────────────────────────────────────────────────────────────────
# Setup كامل لـ edge-router. يُستدعى:
#   1. عند أول deploy (من topology.yml)
#   2. بعد كل docker start (اختياري — سنفعله يدوياً)
#
# يحاكي Network Edge Device:
#   - eth1 (WAN-EXT bridge): attacker, ext-clients
#   - eth2 (WAN-INT): الاتصال بـ core-router
#   - eth0 (Containerlab mgmt): الاتصال بـ Ubuntu host (للإنترنت)
# ═══════════════════════════════════════════════════════════════════

set +e  # ما نوقف عند أخطاء (لأن بعض الأوامر تفشل بشكل طبيعي عند restart)

# ── 1. Conntrack Hardening ─────────────────────────────────────────
# منع امتلاء جدول conntrack أثناء هجمات DoS
sysctl -w net.netfilter.nf_conntrack_max=2000000 2>/dev/null || true

# ── 2. Syslog ──────────────────────────────────────────────────────
syslog-ng 2>/dev/null

# ── 3. SSH Server ──────────────────────────────────────────────────
mkdir -p /var/run/sshd
/usr/sbin/sshd 2>/dev/null

# ── 4. WAN-EXT Bridge ──────────────────────────────────────────────
# bridge مشترك للمهاجم وعملاء الإنترنت
ip link add br-wan type bridge 2>/dev/null
ip link set eth1 master br-wan 2>/dev/null
ip link set eth3 master br-wan 2>/dev/null
ip link set eth4 master br-wan 2>/dev/null
ip link set br-wan up
ip addr add 10.0.0.1/24 dev br-wan 2>/dev/null

# ── 5. WAN-INT Interface ───────────────────────────────────────────
# الاتصال بـ core-router
ip addr add 10.0.3.1/24 dev eth2 2>/dev/null

# ── 6. IP Forwarding ───────────────────────────────────────────────
sysctl -w net.ipv4.ip_forward=1

# ── 7. Routes ──────────────────────────────────────────────────────
ip route del default 2>/dev/null
ip route add default via 172.20.20.1 dev eth0 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null

# Routes داخلية عبر core-router
ip route add 10.0.1.0/24 via 10.0.3.2 dev eth2 2>/dev/null
ip route add 10.0.2.0/24 via 10.0.3.2 dev eth2 2>/dev/null
ip route add 10.0.4.0/24 via 10.0.3.2 dev eth2 2>/dev/null  # Management (لاحقاً)

# ── 8. NAT للإنترنت ────────────────────────────────────────────────
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Internet Access (افتراضي: مفعل)
# لتعطيل الإنترنت (للاختبارات الأمنية المعزولة):
#   ضع # أمام السطر التالي
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
iptables -t nat -A POSTROUTING -o eth0 -j MASQUERADE 2>/dev/null

# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# Strict Firewall (افتراضي: معطل)
# لمنع LAN من الوصول المباشر للخارج:
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# iptables -A FORWARD -d 10.0.2.0/24 -m state --state NEW -j DROP

# ── 9. DNS ─────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── 10. Metrics ────────────────────────────────────────────────────
# kill أي metrics قديم (في حالة restart)
pkill -f "metrics.py edge-router" 2>/dev/null
sleep 1
python3 /scripts/metrics.py edge-router router 10.0.0.10 10.0.3.2 &

echo "✅ edge-router initialized"
