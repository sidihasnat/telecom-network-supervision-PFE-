#!/bin/sh
# background_traffic.sh v3
# ════════════════════════════════════════════════════════════════
# توليد traffic طبيعي واقعي في الخلفية.
# يُشغّل من: pc1, pc2, ext-client1, ext-client2, attacker
#
# الهدف: Dashboard يظهر:
#   CPU: 2-6%  |  Throughput: 0.5-2 Mbps  |  Connections: 6-15
# ════════════════════════════════════════════════════════════════

WEB_SERVER="10.0.1.2"
DNS_SERVER="10.0.1.3"
SSH_SERVER="10.0.2.2"
DB_SERVER="10.0.2.3"
CORE_ROUTER="10.0.2.1"

# ══════════════════════════════════════════════════════════════
# 1. HTTP — تصفح طبيعي
# ══════════════════════════════════════════════════════════════

# تصفح عادي — request كل 1-3 ثواني
(while true; do
    curl -s -o /dev/null -m 3 http://$WEB_SERVER/ 2>/dev/null
    sleep $(shuf -i 1-3 -n 1)
done) &

# صفحة index
(while true; do
    curl -s -o /dev/null -m 3 http://$WEB_SERVER/index.html 2>/dev/null
    sleep $(shuf -i 2-5 -n 1)
done) &

# صفحة about
(while true; do
    curl -s -o /dev/null -m 3 http://$WEB_SERVER/about.html 2>/dev/null
    sleep $(shuf -i 3-8 -n 1)
done) &

# POST بيانات form طبيعية (تسجيل دخول، إرسال نموذج)
(while true; do
    curl -s -o /dev/null -m 3 \
        -X POST \
        -d "username=user&action=login&page=home" \
        http://$WEB_SERVER/ 2>/dev/null
    sleep $(shuf -i 4-10 -n 1)
done) &

# اتصالان متزامنان مع فاصل (يشبه المتصفح الحقيقي)
(while true; do
    curl -s -o /dev/null -m 2 http://$WEB_SERVER/ 2>/dev/null &
    sleep $(shuf -i 1-2 -n 1)
    curl -s -o /dev/null -m 2 http://$WEB_SERVER/index.html 2>/dev/null &
    wait
    sleep $(shuf -i 3-7 -n 1)
done) &

# ══════════════════════════════════════════════════════════════
# 2. DNS — queries متنوعة (ترفع UDP counters)
# ══════════════════════════════════════════════════════════════

(while true; do
    nslookup web.telecom.local $DNS_SERVER >/dev/null 2>&1
    sleep $(shuf -i 2-5 -n 1)
done) &

(while true; do
    nslookup dns.telecom.local $DNS_SERVER >/dev/null 2>&1
    sleep $(shuf -i 3-7 -n 1)
done) &

(while true; do
    nslookup ssh.telecom.local $DNS_SERVER >/dev/null 2>&1
    sleep $(shuf -i 4-10 -n 1)
done) &

(while true; do
    nslookup db.telecom.local $DNS_SERVER >/dev/null 2>&1
    sleep $(shuf -i 5-12 -n 1)
done) &

# ══════════════════════════════════════════════════════════════
# 3. ICMP Ping — طبيعي (يرفع icmpInRate بشكل واقعي)
# ══════════════════════════════════════════════════════════════

(while true; do
    ping -c 2 -W 1 $WEB_SERVER >/dev/null 2>&1
    sleep $(shuf -i 3-6 -n 1)
done) &

(while true; do
    ping -c 2 -W 1 $DNS_SERVER >/dev/null 2>&1
    sleep $(shuf -i 4-8 -n 1)
done) &

(while true; do
    ping -c 2 -W 1 $CORE_ROUTER >/dev/null 2>&1
    sleep $(shuf -i 3-7 -n 1)
done) &

(while true; do
    ping -c 1 -W 1 $SSH_SERVER >/dev/null 2>&1
    sleep $(shuf -i 5-10 -n 1)
done) &

(while true; do
    ping -c 1 -W 1 $DB_SERVER >/dev/null 2>&1
    sleep $(shuf -i 6-12 -n 1)
done) &

# ══════════════════════════════════════════════════════════════
# 4. SSH — محاولات اتصال طبيعية
# ══════════════════════════════════════════════════════════════

(while true; do
    timeout 2 sh -c "echo '' | nc -w 1 $SSH_SERVER 22" >/dev/null 2>&1
    sleep $(shuf -i 8-15 -n 1)
done) &

# ══════════════════════════════════════════════════════════════
# 5. Database — اتصالات TCP طبيعية
# ══════════════════════════════════════════════════════════════

(while true; do
    timeout 2 sh -c "echo '' | nc -w 1 $DB_SERVER 3306" >/dev/null 2>&1
    sleep $(shuf -i 5-10 -n 1)
done) &

echo "🌐 Background traffic v3 started (16 generators)"
echo "   HTTP  → $WEB_SERVER (GET/POST, every 1-10s)"
echo "   DNS   → $DNS_SERVER (every 2-12s, multiple domains)"
echo "   PING  → all devices (every 3-12s)"
echo "   SSH   → $SSH_SERVER (every 8-15s)"
echo "   DB    → $DB_SERVER (every 5-10s)"

