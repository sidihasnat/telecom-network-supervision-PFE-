import psutil
import requests
import time
import subprocess
import sys
from datetime import datetime
from collections import defaultdict
import json
import os

BACKEND_URL = "http://10.0.4.11:8080/api/metrics"
DEVICE_NAME  = sys.argv[1] if len(sys.argv) > 1 else "unknown"
DEVICE_TYPE  = sys.argv[2] if len(sys.argv) > 2 else "unknown"
NEIGHBORS    = sys.argv[3:] if len(sys.argv) > 3 else []
NEIGHBORS = [n for n in NEIGHBORS if n != '&']
INTERVAL     = 10

IGNORED_PREFIXES = ("lo", "eth0", "docker", "veth", "virbr", "tun", "dummy")

def get_active_interfaces():

"""
[1] INTERFACES — Auto-discover
 
psutil.net_io_couters = {
 'Ethernet': snetio(bytes_sent=123456, bytes_recv=654321, packets_sent=1000, packets_recv=900),
 'Wi-Fi': snetio(bytes_sent=34567, bytes_recv=87654, packets_sent=300, packets_recv=250)
}
psutil.net_if_stats = {
 'Ethernet': snicstats(isup=True, duplex=2, speed=1000, mtu=1500),
 'Wi-Fi': snicstats(isup=True, duplex=2, speed=300, mtu=1500)
}
speed : La vitesse maximale de transmission des données d’une interface réseau(Mbps).
duplex : 1 (Half duplex) , 2 (Full duplex)
mtu : La taille maximale d’un paquet de données pouvant être transmis sur le réseau
"""

    result = []
    try:
        counters = psutil.net_io_counters(pernic=True)
        stats    = psutil.net_if_stats()
        for iface in counters:
            if any(iface.startswith(p) for p in IGNORED_PREFIXES):
                continue
            master_path = f"/sys/class/net/{iface}/master"
            if os.path.exists(master_path):
                continue
            is_up = stats[iface].isup if iface in stats else True
            result.append((iface, is_up))
    except Exception:
        pass
    return sorted(result, key=lambda t: t[0])

def measure_traffic(interface):
    try:
        def snap():
            n = psutil.net_io_counters(pernic=True).get(interface)
            if not n:
                return None
            return (n.bytes_sent, n.bytes_recv,
                    n.packets_sent, n.packets_recv,
                    n.errin + n.errout,
                    n.dropin + n.dropout)

        s1 = snap()
        if not s1:
            return 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0
        t0 = time.time()
        target = t0 + 1.0
        while time.time() < target:
            time.sleep(0.01)
        dt = time.time() - t0
        s2 = snap()
        if not s2:
            return 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0
        dt = max(dt, 0.001)

        bout  = s2[0] - s1[0]
        binp  = s2[1] - s1[1]
        pout  = s2[2] - s1[2]
        pinp  = s2[3] - s1[3]
        errs  = s2[4] - s1[4]
        drops = s2[5] - s1[5]
        tot   = pout + pinp


        bytes_per_pkt_out = (bout / pout)  if pout  > 0 else 0.0
        bytes_per_pkt_in  = (binp / pinp)  if pinp  > 0 else 0.0

        return (
            round(bout * 8 / 1_000_000 / dt, 4),
            round(binp * 8 / 1_000_000 / dt, 4),
            pout,
            pinp,
            round(errs  / tot * 100 if tot > 0 else 0.0, 4),
            round(drops / tot * 100 if tot > 0 else 0.0, 4),
            round(bytes_per_pkt_out, 2),
            round(bytes_per_pkt_in,  2),
        )
    except Exception:
        return 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0

def measure_latency(host):
    try:
        result = subprocess.run(
            ["ping", "-c", "3", "-W", "2", host],
            capture_output=True, text=True, timeout=10
        )
        out = result.stdout
        latency, loss, jitter = 0.0, 0.0, 0.0

        if "avg" in out:
            latency = float(out.split("avg")[1].split("=")[1].split('/')[1])
        if "packet loss" in out:
            loss = float(out.split(',')[2].split("%")[0])
        if "mdev" in out:
            jitter = float(out.split("avg")[1].split("=")[1].split('/')[3].replace(" ms",""))
        return round(latency, 3), round(loss, 2), round(jitter, 3)
    except Exception:
        return 0.0, 0.0, 0.0

_last_tcp_stats = None

def get_tcp_stats():
    global _last_tcp_stats

    stats = {

        "passiveOpens":           0,
        "tcpRetransmissions":     0,
        "icmpInMsgs":             0,
        "activeOpens":            0,
        "attemptFails":           0,
        "estabResets":            0,
        "inSegs":                 0,
        "outSegs":                0,
        "inErrs":                 0,
        "outRsts":                0,
        "icmpInErrors":           0,


        "currEstab":              0,


        "passiveOpensRate":       0.0,
        "retransRate":            0.0,
        "icmpInRate":             0.0,
        "attemptFailsRate":       0.0,
        "estabResetsRate":        0.0,
        "outRstsRate":            0.0,
        "inSegsRate":             0.0,
        "outSegsRate":            0.0,
        "activeOpensRate":        0.0,


        "inOutSegRatio":          0.0,
        "rstPerConnection":       0.0,
    }

    try:
        tcp_keys = tcp_vals = icmp_keys = icmp_vals = None

        with open("/proc/net/snmp", "r") as f:
            for line in f.readlines():
                if line.startswith("Tcp:") and "RtoAlgorithm" in line:
                    tcp_keys = line.split()
                elif line.startswith("Tcp:") and "RtoAlgorithm" not in line:
                    tcp_vals = line.split()
                elif line.startswith("Icmp:") and "InMsgs" in line:
                    icmp_keys = line.split()
                elif line.startswith("Icmp:") and "InMsgs" not in line:
                    icmp_vals = line.split()

        current_time = time.time()


        if _last_tcp_stats is None:
            passive = retrans = icmp_in = 0
            attempt_fails = estab_resets = out_rsts = 0
            in_segs = out_segs = active_opens = 0
            if tcp_keys and tcp_vals:
                tcp = dict(zip(tcp_keys[1:], tcp_vals[1:]))
                passive       = int(tcp.get("PassiveOpens", 0))
                retrans       = int(tcp.get("RetransSegs",  0))
                attempt_fails = int(tcp.get("AttemptFails", 0))
                estab_resets  = int(tcp.get("EstabResets",  0))
                out_rsts      = int(tcp.get("OutRsts",      0))
                in_segs       = int(tcp.get("InSegs",       0))
                out_segs      = int(tcp.get("OutSegs",      0))
                active_opens  = int(tcp.get("ActiveOpens",  0))
            if icmp_keys and icmp_vals:
                icmp = dict(zip(icmp_keys[1:], icmp_vals[1:]))
                icmp_in = int(icmp.get("InMsgs", 0))

            _last_tcp_stats = {
                "passiveOpens": passive,
                "activeOpens" : active_opens,
                "retransSegs":  retrans,
                "attemptFails": attempt_fails,
                "estabResets":  estab_resets,
                "outRsts":      out_rsts,
                "inSegs":       in_segs,
                "outSegs":      out_segs,
                "icmpInMsgs":   icmp_in,
                "timestamp":    current_time
            }
            return stats 


        time_diff = current_time - _last_tcp_stats["timestamp"]


        if tcp_keys and tcp_vals:
            tcp = dict(zip(tcp_keys[1:], tcp_vals[1:]))

            passive       = int(tcp.get("PassiveOpens", 0))
            estab         = int(tcp.get("CurrEstab",    0))
            retrans       = int(tcp.get("RetransSegs",  0))
            attempt_fails = int(tcp.get("AttemptFails", 0))
            estab_resets  = int(tcp.get("EstabResets",  0))
            out_rsts      = int(tcp.get("OutRsts",      0))
            in_segs       = int(tcp.get("InSegs",       0))
            out_segs      = int(tcp.get("OutSegs",      0))
            active_opens  = int(tcp.get("ActiveOpens",  0))



            stats["passiveOpens"]       = passive
            stats["tcpRetransmissions"] = retrans
            stats["activeOpens"]        = int(tcp.get("ActiveOpens", 0))
            stats["attemptFails"]       = attempt_fails
            stats["estabResets"]        = estab_resets
            stats["inSegs"]             = in_segs
            stats["outSegs"]            = out_segs
            stats["inErrs"]             = int(tcp.get("InErrs", 0))
            stats["outRsts"]            = out_rsts


            stats["currEstab"]          = estab


            if time_diff > 0:
                stats["passiveOpensRate"] = round(
                    max(0, passive - _last_tcp_stats["passiveOpens"]) / time_diff, 2)
                stats["activeOpensRate"] = round(
                    max(0, active_opens - _last_tcp_stats["activeOpens"]) / time_diff ,2)
                stats["retransRate"] = round(
                    max(0, retrans - _last_tcp_stats["retransSegs"]) / time_diff, 2)
                stats["attemptFailsRate"] = round(
                    max(0, attempt_fails - _last_tcp_stats["attemptFails"]) / time_diff, 2)
                stats["estabResetsRate"] = round(
                    max(0, estab_resets - _last_tcp_stats["estabResets"]) / time_diff, 2)
                stats["outRstsRate"] = round(
                    max(0, out_rsts - _last_tcp_stats["outRsts"]) / time_diff, 2)

                in_segs_rate = max(0, in_segs - _last_tcp_stats["inSegs"]) / time_diff
                out_segs_rate = max(0, out_segs - _last_tcp_stats["outSegs"]) / time_diff
                stats["inSegsRate"] = round(in_segs_rate, 2)
                stats["outSegsRate"] = round(out_segs_rate, 2)


                stats["inOutSegRatio"] = round(
                    in_segs_rate / max(out_segs_rate, 0.01), 3)
                stats["rstPerConnection"] = round(
                    stats["outRstsRate"] / max(estab, 1), 3)


            _last_tcp_stats["passiveOpens"] = passive
            _last_tcp_stats["activeOpens"]  = active_opens
            _last_tcp_stats["retransSegs"]  = retrans
            _last_tcp_stats["attemptFails"] = attempt_fails
            _last_tcp_stats["estabResets"]  = estab_resets
            _last_tcp_stats["outRsts"]      = out_rsts
            _last_tcp_stats["inSegs"]       = in_segs
            _last_tcp_stats["outSegs"]      = out_segs


        if icmp_keys and icmp_vals:
            icmp = dict(zip(icmp_keys[1:], icmp_vals[1:]))

            icmp_in_msgs = int(icmp.get("InMsgs",   0))
            stats["icmpInMsgs"]    = icmp_in_msgs
            stats["icmpInErrors"]  = int(icmp.get("InErrors", 0))

            if time_diff > 0:
                stats["icmpInRate"] = round(
                    max(0, icmp_in_msgs - _last_tcp_stats["icmpInMsgs"]) / time_diff, 2)

            _last_tcp_stats["icmpInMsgs"] = icmp_in_msgs


        _last_tcp_stats["timestamp"] = current_time

    except Exception as e:
        print(f"Error in TCP stats: {e}")

    return stats


_last_udp_stats = None

def get_udp_stats():
    
    global _last_udp_stats
    
    stats = {

        "udpInDatagrams":   0,
        "udpOutDatagrams":  0,
        "udpInErrors":      0,
        "udpNoPorts":       0,
        

        "udpInRate":        0.0,
        "udpOutRate":       0.0,
        "udpErrorRate":     0.0,
        "udpNoPortRate":    0.0,
        

        "udpInOutRatio":    0.0,
    }
    
    try:
        udp_keys = udp_vals = None
        
        with open("/proc/net/snmp", "r") as f:
            for line in f.readlines():
                if line.startswith("Udp:") and "InDatagrams" in line:
                    udp_keys = line.split()
                elif line.startswith("Udp:") and "InDatagrams" not in line:
                    udp_vals = line.split()
        
        current_time = time.time()
        
        if _last_udp_stats is None:
            in_dgrams = out_dgrams = in_errors = no_ports = 0
            if udp_keys and udp_vals:
                udp = dict(zip(udp_keys[1:], udp_vals[1:]))
                in_dgrams  = int(udp.get("InDatagrams",  0))
                out_dgrams = int(udp.get("OutDatagrams", 0))
                in_errors  = int(udp.get("InErrors",     0))
                no_ports   = int(udp.get("NoPorts",      0))
            
            _last_udp_stats = {
                "InDatagrams":  in_dgrams,
                "OutDatagrams": out_dgrams,
                "InErrors":     in_errors,
                "NoPorts":      no_ports,
                "timestamp":    current_time
            }
            return stats
        
        time_diff = current_time - _last_udp_stats["timestamp"]
        

        if udp_keys and udp_vals:
            udp = dict(zip(udp_keys[1:], udp_vals[1:]))
            
            in_dgrams  = int(udp.get("InDatagrams",  0))
            out_dgrams = int(udp.get("OutDatagrams", 0))
            in_errors  = int(udp.get("InErrors",     0))
            no_ports   = int(udp.get("NoPorts",      0))
            

            stats["udpInDatagrams"]  = in_dgrams
            stats["udpOutDatagrams"] = out_dgrams
            stats["udpInErrors"]     = in_errors
            stats["udpNoPorts"]      = no_ports
            

            if time_diff > 0:

                total_in = in_dgrams + no_ports + in_errors
                total_in_last = (_last_udp_stats["InDatagrams"] + 
                                 _last_udp_stats["NoPorts"] + 
                                 _last_udp_stats["InErrors"])
                
                in_rate_total = max(0, total_in - total_in_last) / time_diff
                out_rate = max(0, out_dgrams - _last_udp_stats["OutDatagrams"]) / time_diff
                
                stats["udpInRate"] = round(in_rate_total, 2)
                stats["udpOutRate"] = round(out_rate, 2)
                stats["udpErrorRate"] = round(
                    max(0, in_errors - _last_udp_stats["InErrors"]) / time_diff, 2)
                stats["udpNoPortRate"] = round(
                    max(0, no_ports - _last_udp_stats["NoPorts"]) / time_diff, 2)
                

                if out_rate > 0:
                    stats["udpInOutRatio"] = round(in_rate_total / out_rate, 3)
                elif in_rate_total > 0:
                    stats["udpInOutRatio"] = 999.0
                else:
                    stats["udpInOutRatio"] = 0.0
            
            _last_udp_stats["InDatagrams"]  = in_dgrams
            _last_udp_stats["OutDatagrams"] = out_dgrams
            _last_udp_stats["InErrors"]     = in_errors
            _last_udp_stats["NoPorts"]      = no_ports
        
        _last_udp_stats["timestamp"] = current_time
    
    except Exception as e:
        print(f"Error in UDP stats: {e}")
    
    return stats


def get_routing_table_size():
    
    try:
        r = subprocess.run(
            ["ip", "route", "show"],
            capture_output=True, text=True, timeout=3
        )
        return len([l for l in r.stdout.strip().splitlines() if l.strip()])
    except Exception:
        return 0


def get_forwarding_stats():
   
    stats = {
        "forwardedPackets": 0,
        "inDiscards":       0,
        "noRoutePackets":   0,
    }
    try:
        ip_keys = ip_vals = None
        with open("/proc/net/snmp", "r") as f:
            for line in f.readlines():
                if line.startswith("Ip:") and "Forwarding" in line:
                    ip_keys = line.split()
                elif line.startswith("Ip:") and "Forwarding" not in line:
                    ip_vals = line.split()

        if ip_keys and ip_vals:
            ip = dict(zip(ip_keys[1:], ip_vals[1:]))
            stats["forwardedPackets"] = int(ip.get("ForwDatagrams",  0))
            stats["inDiscards"]       = int(ip.get("InDiscards",     0))
            stats["noRoutePackets"]   = int(ip.get("OutNoRoutes",    0))
    except Exception:
        pass
    return stats

def get_interface_bandwidth_util(interface, throughput_mbps, link_speed_mbps=1000):
    
    try:
        util = (throughput_mbps / link_speed_mbps) * 100
        return round(min(util, 100.0), 2)
    except Exception:
        return 0.0

SENSITIVE_PORTS = {
    "22":    "SSH",
    "23":    "Telnet",
    "3306":  "MySQL",
    "5432":  "PostgreSQL",
    "6379":  "Redis",
    "27017": "MongoDB",
    "80":    "HTTP",
    "443":   "HTTPS",
    "21":    "FTP",
    "3389":  "RDP",
}

def get_connection_indicators():
    result = {
        "uniqueDestinationPorts": 0,
        "timeWaitConnections":    0,
        "sensitivePortsHit":      0,
        "sshConnectionAttempts":  0,
        "topAttackerRepeat":      0,
        "portAccessCount":        {},
        "topAttackersDetail":     {},
    }
    try:
        r = subprocess.run(["ss", "-tn"], capture_output=True, text=True)

        dst_ports    = set()
        port_count   = {}
        ip_count     = {}
        ip_port_map  = {}
        time_wait    = 0

        for line in r.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 5:
                dst_port = parts[3].rsplit(":", 1)[-1]
                src_ip   = parts[4].rsplit(":", 1)[0].strip("[]")

                if dst_port.isdigit():
                    dst_ports.add(dst_port)
                    port_count[dst_port] = port_count.get(dst_port, 0) + 1

                    if dst_port == "22":
                        result["sshConnectionAttempts"] += 1

                if src_ip:
                    ip_count[src_ip] = ip_count.get(src_ip, 0) + 1

                    if dst_port.isdigit():
                        if src_ip not in ip_port_map:
                            ip_port_map[src_ip] = {}
                        ip_port_map[src_ip][dst_port] = \
                            ip_port_map[src_ip].get(dst_port, 0) + 1

            if parts and "TIME-WAIT" in parts[0]:
                time_wait += 1


        top_10_ports = dict(
            sorted(port_count.items(),
                   key=lambda x: x[1],
                   reverse=True)[:10]
        )

        top_10_ips = dict(
            sorted(ip_port_map.items(),
                   key=lambda x: sum(x[1].values()),
                   reverse=True)[:10]
        )

        result["uniqueDestinationPorts"] = len(dst_ports)
        result["timeWaitConnections"]    = time_wait
        result["sensitivePortsHit"]      = sum(
            1 for p in dst_ports if p in SENSITIVE_PORTS
        )
        result["topAttackerRepeat"]      = max(ip_count.values()) if ip_count else 0
        result["portAccessCount"]        = json.dumps(top_10_ports)
        result["topAttackersDetail"]     = json.dumps(top_10_ips)

    except Exception:
        pass
    return result

def get_half_open_connections():

    try:
        r = subprocess.run(
            ["ss", "-tn", "state", "syn-recv"],
            capture_output=True, text=True
        )
        return max(0, len(r.stdout.strip().splitlines()) - 1)
    except Exception:
        return 0

def get_unique_source_ips():

    try:
        r = subprocess.run(["ss", "-tn"], capture_output=True, text=True)
        ips = set()
        for line in r.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 5:
                ip = parts[4].rsplit(":", 1)[0].strip("[]")
                if ip:
                    ips.add(ip)
        return len(ips)
    except Exception:
        return 0

_last_failed_logins = None
 
def get_failed_logins():
    
    total = 0

    try:
        r = subprocess.run(
            ["grep", "-c", "FAIL LOGIN", "/var/log/vsftpd.log"],
            capture_output=True, text=True, timeout=2
        )
        if r.returncode == 0:
            count = int(r.stdout.strip())
            if count > 0:
                total += count
    except Exception:
        pass
 

    ssh_count = 0
    for logfile in ["/var/log/auth.log", "/var/log/secure", "/var/log/messages"]:
        try:
            r = subprocess.run(
                ["grep", "-c", "Failed", logfile],
                capture_output=True, text=True, timeout=2
            )
            if r.returncode == 0:
                c = int(r.stdout.strip())
                if c > ssh_count:
                    ssh_count = c
        except Exception:
            continue
    total += ssh_count
 
    if total == 0:
        try:
            r = subprocess.run(
                ["journalctl", "-q", "--no-pager",
                "-g", "Failed password|FAIL LOGIN",
                "--since", "today"],
                capture_output=True, text=True, timeout=3
            )
            total = len(r.stdout.strip().splitlines())
        except Exception:
            pass
 
    return total

def get_arp_table():

    entries = []
    try:
        with open("/proc/net/arp", "r") as f:
            for line in f.readlines()[1:]:
                parts = line.split()
                if len(parts) >= 4 and parts[2] != "0x0":
                    entries.append({"ip": parts[0], "mac": parts[3]})
        if entries:
            return entries
    except Exception:
        pass
    try:
        r = subprocess.run(["arp", "-n"], capture_output=True, text=True)
        for line in r.stdout.splitlines()[1:]:
            parts = line.split()
            if len(parts) >= 3 and parts[2] not in ("(incomplete)", ""):
                entries.append({"ip": parts[0], "mac": parts[2]})
    except Exception:
        pass
    return entries

def get_connections():
    try:
        conns = psutil.net_connections(kind="inet")
        total = len(conns)
        estab = sum(1 for c in conns if c.status == "ESTABLISHED")
        return total, estab
    except Exception:
        return 0, 0

_connection_start_times = {}

def get_connection_duration():
    
    global _connection_start_times

    result = {
        "avgConnectionDuration": 0.0,
        "maxConnectionDuration": 0.0,
        "longConnectionRatio":   0.0,
    }

    try:
        now   = time.time()
        conns = psutil.net_connections(kind="inet")
        estab = [c for c in conns if c.status == "ESTABLISHED"
                 and c.laddr and c.raddr]


        current_keys = set()
        for c in estab:
            key = (str(c.laddr), str(c.raddr))
            current_keys.add(key)
            if key not in _connection_start_times:
                _connection_start_times[key] = now 

        for key in list(_connection_start_times):
            if key not in current_keys:
                del _connection_start_times[key]

        if not estab:
            return result

        durations = [now - _connection_start_times[
                         (str(c.laddr), str(c.raddr))]
                     for c in estab
                     if (str(c.laddr), str(c.raddr)) in _connection_start_times]

        if not durations:
            return result

        long_threshold = 10.0

        result["avgConnectionDuration"] = round(sum(durations) / len(durations), 2)
        result["maxConnectionDuration"] = round(max(durations), 2)
        result["longConnectionRatio"]   = round(
            sum(1 for d in durations if d > long_threshold) / len(durations), 3)

    except Exception:
        pass

    return result

def collect_metrics():


    cpu    = psutil.cpu_percent(interval=1)
    memory = psutil.virtual_memory().percent
    
    try:
        du = psutil.disk_usage('/')
        disk_usage    = round(du.percent, 1)
        disk_total_gb = round(du.total / (1024 ** 3), 2)
        disk_used_gb  = round(du.used  / (1024 ** 3), 2)
        disk_free_gb  = round(du.free  / (1024 ** 3), 2)
    except Exception:
        disk_usage, disk_total_gb, disk_used_gb, disk_free_gb = 0.0, 0.0, 0.0, 0.0


    total_conns, established = get_connections()
    half_open     = get_half_open_connections()
    unique_ips    = get_unique_source_ips()
    failed_logins = get_failed_logins()
    arp_table     = get_arp_table()

    global _last_failed_logins
    failed_logins_rate = 0.0
    current_time = time.time()
    if _last_failed_logins is None:
        _last_failed_logins = {"count": failed_logins, "timestamp": current_time}
    else:
        dt = current_time - _last_failed_logins["timestamp"]
        if dt > 0:
            failed_logins_rate = round(
                max(0, failed_logins - _last_failed_logins["count"]) / dt, 2)
        _last_failed_logins = {"count": failed_logins, "timestamp": current_time}

    tcp_stats = get_tcp_stats()
    
    udp_stats = get_udp_stats()

    conn         = get_connection_indicators()
    conn_dur     = get_connection_duration()

    routing_size  = get_routing_table_size()
    fwd_stats     = get_forwarding_stats()

    active_ifaces = get_active_interfaces()
    interfaces    = []

    for i, (iface, is_up) in enumerate(active_ifaces):

        (out, inp, pkt_s, pkt_r,
         err, drop,
         bpp_out, bpp_in) = measure_traffic(iface)

        latency, loss, jitter = 0.0, 0.0, 0.0
        if i < len(NEIGHBORS) and is_up:
            latency, loss, jitter = measure_latency(NEIGHBORS[i])

        bw_util_out = get_interface_bandwidth_util(iface, out)
        bw_util_in  = get_interface_bandwidth_util(iface, inp)

        interfaces.append({
            "interfaceName":      iface,
            "isUp":               is_up,
            "throughputOut":      out,
            "throughputIn":       inp,
            "packetsPerSecSent":  pkt_s,
            "packetsPerSecRecv":  pkt_r,
            "errorRate":          err,
            "dropRate":           drop,
            "latency":            latency,
            "packetLoss":         loss,
            "jitter":             jitter,
            "bytesPerPacketOut":  bpp_out,
            "bytesPerPacketIn":   bpp_in,
            "bandwidthUtilOut":   bw_util_out,
            "bandwidthUtilIn":    bw_util_in,
        })


    all_lat  = [f["latency"]    for f in interfaces if f["latency"] > 0]
    all_loss = [f["packetLoss"] for f in interfaces]
    avg_lat  = sum(all_lat)  / len(all_lat)  if all_lat  else 0
    max_loss = max(all_loss) if all_loss else 0

    if (cpu > 80 or avg_lat > 100 or half_open > 10 or max_loss > 5
            or disk_usage > 90
            or tcp_stats["icmpInRate"] > 1000
            or udp_stats["udpInOutRatio"] > 10):
        status = "critical"
    elif (cpu > 60 or avg_lat > 50 or half_open > 3 or max_loss > 1
            or disk_usage > 80
            or tcp_stats["passiveOpensRate"] > 50
            or routing_size > 500
            or udp_stats["udpInOutRatio"] > 5
            or udp_stats["udpNoPortRate"] > 10):
        status = "warning"
    else:
        status = "normal"

    return {

        "deviceName":  DEVICE_NAME,
        "deviceType":  DEVICE_TYPE,

        "cpuUsage":    cpu,
        "memoryUsage": memory,
        "diskUsage":   disk_usage,
        "diskTotalGb": disk_total_gb,
        "diskUsedGb":  disk_used_gb,
        "diskFreeGb":  disk_free_gb,
        "connections": established,
        "status":      status,

        "interfaces":  interfaces,

        "tcpStats": {

            "passiveOpens":          tcp_stats["passiveOpens"],
            "tcpRetransmissions":    tcp_stats["tcpRetransmissions"],
            "icmpInMsgs":            tcp_stats["icmpInMsgs"],
            "activeOpens":           tcp_stats["activeOpens"],
            "attemptFails":          tcp_stats["attemptFails"],
            "estabResets":           tcp_stats["estabResets"],
            "inSegs":                tcp_stats["inSegs"],
            "outSegs":               tcp_stats["outSegs"],
            "inErrs":                tcp_stats["inErrs"],
            "outRsts":               tcp_stats["outRsts"],
            "icmpInErrors":          tcp_stats["icmpInErrors"],


            "currEstab":             tcp_stats["currEstab"],


            "passiveOpensRate":      tcp_stats["passiveOpensRate"],
            "activeOpensRate":       tcp_stats["activeOpensRate"],
            "retransRate":           tcp_stats["retransRate"],
            "icmpInRate":            tcp_stats["icmpInRate"],
            "attemptFailsRate":      tcp_stats["attemptFailsRate"],
            "estabResetsRate":       tcp_stats["estabResetsRate"],
            "outRstsRate":           tcp_stats["outRstsRate"],
            "inSegsRate":            tcp_stats["inSegsRate"],
            "outSegsRate":           tcp_stats["outSegsRate"],


            "inOutSegRatio":         tcp_stats["inOutSegRatio"],
            "rstPerConnection":      tcp_stats["rstPerConnection"],


            "udpInDatagrams":        udp_stats["udpInDatagrams"],
            "udpOutDatagrams":       udp_stats["udpOutDatagrams"],
            "udpInErrors":           udp_stats["udpInErrors"],
            "udpNoPorts":            udp_stats["udpNoPorts"],

            "udpInRate":             udp_stats["udpInRate"],
            "udpOutRate":            udp_stats["udpOutRate"],
            "udpErrorRate":          udp_stats["udpErrorRate"],
            "udpNoPortRate":         udp_stats["udpNoPortRate"],

            "udpInOutRatio":         udp_stats["udpInOutRatio"],


            "routingTableSize":      routing_size,
            "forwardedPackets":      fwd_stats["forwardedPackets"],
            "inDiscards":            fwd_stats["inDiscards"],
            "noRoutePackets":        fwd_stats["noRoutePackets"],
        },
    

        "securityMetric": {
            "halfOpenConnections":    half_open,
            "uniqueSourceIPs":        unique_ips,
            "failedLogins":           failed_logins,
            "failedLoginsRate":       failed_logins_rate,
            "sshConnectionAttempts":  conn["sshConnectionAttempts"],
            "topAttackerRepeat":      conn["topAttackerRepeat"],
            "uniqueDestinationPorts": conn["uniqueDestinationPorts"],
            "timeWaitConnections":    conn["timeWaitConnections"],
            "sensitivePortsHit":      conn["sensitivePortsHit"],
            "portAccessCount":        conn["portAccessCount"],
            "topAttackersDetail":     conn["topAttackersDetail"],

            "avgConnectionDuration":  conn_dur["avgConnectionDuration"],
            "maxConnectionDuration":  conn_dur["maxConnectionDuration"],
            "longConnectionRatio":    conn_dur["longConnectionRatio"],
        },


        "arpTable": arp_table,


        "neighbors":    NEIGHBORS,
        "timestamp":    datetime.now().isoformat(),
    }

def send_metrics(metrics):
    try:
        requests.post(BACKEND_URL, json=metrics, timeout=5)
        ifaces_str = " | ".join(
            f"{f['interfaceName']}{'' if f.get('isUp', True) else '[DOWN]'} "
            f"↑{f['throughputOut']:.2f}Mbps({f['bandwidthUtilOut']}%) "
            f"lat={f['latency']}ms "
            f"loss={f['packetLoss']}% "
            f"bpp={f['bytesPerPacketIn']:.0f}B"
            for f in metrics["interfaces"]
        )
        tcp = metrics.get("tcpStats", {})
        sec = metrics.get("securityMetric", {})
        print(
            f"✅ {DEVICE_NAME} | "
            f"CPU:{metrics['cpuUsage']}% | "
            f"RAM:{metrics['memoryUsage']}% | "
            f"Disk:{metrics['diskUsage']}% | "
            f"SYN_rate:{tcp.get('passiveOpensRate', 0)}/s | "
            f"ICMP_rate:{tcp.get('icmpInRate', 0)}/s | "
            f"Estab:{tcp.get('currEstab', 0)} | "
            f"FailRate:{tcp.get('attemptFailsRate', 0)}/s | "
            f"RSTrate:{tcp.get('outRstsRate', 0)}/s | "
            f"In/Out:{tcp.get('inOutSegRatio', 0)} | "
            f"RST/Conn:{tcp.get('rstPerConnection', 0)} | "
            f"LoginRate:{sec.get('failedLoginsRate', 0)}/s | "
            f"AvgConnDur:{sec.get('avgConnectionDuration', 0)}s | "
            f"LongRatio:{sec.get('longConnectionRatio', 0)} | "
            f"Routes:{tcp.get('routingTableSize', 0)} | "
            f"{metrics['status'].upper()} | "
            f"{ifaces_str}"
        )
    except Exception as e:
        print(f" Send error: {e}")

if __name__ == "__main__":
    print(f" Starting : {DEVICE_NAME} ({DEVICE_TYPE})")
    print(f" Backend  : {BACKEND_URL}")
    print(f" Neighbors: {NEIGHBORS}")
    print()
    print(" Features per reading:")
    print("   System    : cpuUsage, memoryUsage, diskUsage, diskTotalGb, diskUsedGb, diskFreeGb")
    print("   Conn      : connections, halfOpenConnections, uniqueSourceIPs, failedLogins")
    print("   TCP (cum) : passiveOpens, activeOpens, attemptFails, estabResets, currEstab")
    print("   TCP (cum) : inSegs, outSegs, inErrs, outRsts, tcpRetransmissions")
    print("   TCP (rate): passiveOpensRate, retransRate, attemptFailsRate, estabResetsRate")
    print("   TCP (rate): outRstsRate, inSegsRate, outSegsRate")
    print("   TCP (ratio): inOutSegRatio (from rates), rstPerConnection (from rates)")
    print("   ICMP      : icmpInMsgs, icmpInErrors, icmpInRate")
    print("   Security  : failedLoginsRate, sshConnectionAttempts, topAttackerRepeat")
    print("   PortScan  : uniqueDestinationPorts, timeWaitConnections, sensitivePortsHit")
    print("   Slowloris : avgConnectionDuration, maxConnectionDuration, longConnectionRatio")
    print("   Interface : throughput, packets, errors, latency, jitter, bytesPerPacket, bandwidthUtil")
    print("   Router+   : routingTableSize, forwardedPackets, inDiscards, noRoutePackets")
    print()

    while True:
        metrics = collect_metrics()
        send_metrics(metrics)
        time.sleep(INTERVAL)
