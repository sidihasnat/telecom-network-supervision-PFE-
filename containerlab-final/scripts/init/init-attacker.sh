#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# init-attacker.sh — WAN Attacker (10.0.0.10)
# ───────────────────────────────────────────────────────────────────
# لا يحتوي على SSH server (هو خصم خارجي).
# لا يوجد metrics.py (المهاجم لا يُراقب — هو من يهاجم).
# ═══════════════════════════════════════════════════════════════════

set +e

# ── Network ────────────────────────────────────────────────────────
ip addr add 10.0.0.10/24 dev eth1 2>/dev/null
ip route del default 2>/dev/null
ip route add default via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.1.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.2.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.3.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 10.0.4.0/24 via 10.0.0.1 dev eth1 2>/dev/null
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0 2>/dev/null

# ── DNS ────────────────────────────────────────────────────────────
sh /scripts/set-dns.sh

# ── Background Traffic ─────────────────────────────────────────────
pkill -f "background_traffic" 2>/dev/null
sleep 1
sh /scripts/background_traffic.sh &

echo "✅ attacker initialized (IP: 10.0.0.10)"
