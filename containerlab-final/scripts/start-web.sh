#!/bin/sh
sleep 3
ip addr add 10.0.1.2/24 dev eth1
ip route del default
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0
ip route add default via 10.0.1.1 dev eth1
syslog-ng
mkdir -p /run/nginx
nginx
sleep 2
python3 /scripts/metrics.py web-server server 10.0.1.1 &
