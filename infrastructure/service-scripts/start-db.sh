#!/bin/sh
sleep 3
ip addr add 10.0.2.3/24 dev eth1
ip route del default
ip route add 172.27.0.0/16 via 172.20.20.1 dev eth0
ip route add default via 10.0.2.1 dev eth1
syslog-ng
mariadbd --user=mysql --bind-address=0.0.0.0 --port=3306 --skip-grant-tables --skip-networking=0 &
sleep 5
python3 /scripts/metrics.py db-server server 10.0.2.1 &
