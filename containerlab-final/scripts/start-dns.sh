#!/bin/sh
sleep 3
ip addr add 10.0.1.3/24 dev eth1
ip route del default
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0
ip route add default via 10.0.1.1 dev eth1
echo "nameserver 127.0.0.1" > /etc/resolv.conf
syslog-ng
chmod -R 755 /etc/bind
chmod -R 777 /var/cache/bind /var/run/named
named -f -c /etc/bind/named.conf &
sleep 2
python3 /scripts/metrics.py dns-server server 10.0.1.1 &
