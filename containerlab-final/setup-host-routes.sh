#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# setup-host-routes.sh
# يضيف routes على Ubuntu host للوصول لـ containers
# ═══════════════════════════════════════════════════════════════════

set -e

echo "🔍 Discovering router IPs..."

EDGE_IP=$(docker inspect clab-telecom-supervision-edge-router \
    -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>/dev/null)
CORE_IP=$(docker inspect clab-telecom-supervision-core-router \
    -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' 2>/dev/null)

if [ -z "$EDGE_IP" ] || [ -z "$CORE_IP" ]; then
    echo "❌ Routers not found. Did you deploy?"
    exit 1
fi

echo "  edge-router: $EDGE_IP"
echo "  core-router: $CORE_IP"

# ── إضافة routes ─────────────────────────────────────────────────
echo ""
echo "📝 Adding routes..."

add_route() {
    local subnet=$1
    local via=$2
    local label=$3
    sudo ip route del $subnet 2>/dev/null
    if sudo ip route add $subnet via $via 2>/dev/null; then
        echo "  ✅ $subnet → $label ($via)"
    else
        echo "  ⚠️  Failed to add $subnet"
    fi
}

add_route "10.0.0.0/24" "$EDGE_IP" "edge-router"
add_route "10.0.1.0/24" "$CORE_IP" "core-router (DMZ)"
add_route "10.0.2.0/24" "$CORE_IP" "core-router (LAN)"
add_route "10.0.3.0/24" "$EDGE_IP" "edge-router (WAN-INT)"
add_route "10.0.4.0/24" "$CORE_IP" "core-router (Management)"

echo ""
echo "📋 Routes:"
ip route | grep "10.0\."

# ── اختبار الاتصال ──────────────────────────────────────────────
echo ""
echo "🔌 Testing connectivity..."

test_ping() {
    local ip=$1
    local label=$2
    if ping -c 1 -W 2 $ip > /dev/null 2>&1; then
        echo "  ✅ $ip ($label)"
    else
        echo "  ❌ $ip ($label)"
    fi
}

test_ping "10.0.4.10" "supervision-web"
test_ping "10.0.4.11" "supervision-app"
test_ping "10.0.4.12" "supervision-db"
test_ping "10.0.4.13" "supervision-ai"
test_ping "10.0.1.2"  "web-server"
test_ping "10.0.2.2"  "ftp-server"

echo ""
echo "🌐 Dashboard URL: http://10.0.4.10"
echo "   API:           http://10.0.4.10/api"
echo "   AI:            http://10.0.4.10/ai"
