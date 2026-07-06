// ──────────────────────────────────────────────────────────────
//  mitre.dart
//  Port من React: src/data/mitre.js
//  (MITRE ATT&CK Mapping + Attack Intelligence)
//
//  يُستخدم في:
//   - widgets/toast_alerts.dart (لعرض MITRE ID + Tactic + Recommendation)
//   - DeviceDetailPage AI Result tab
// ──────────────────────────────────────────────────────────────

class MitreInfo {
  final String id;
  final String tactic;
  final String technique;
  const MitreInfo({
    required this.id,
    required this.tactic,
    required this.technique,
  });
}

class AttackInfo {
  final String severity; // CRITICAL | HIGH | MEDIUM | INFO
  final String impact;
  final String recommendation;
  const AttackInfo({
    required this.severity,
    required this.impact,
    required this.recommendation,
  });
}

const Map<String, MitreInfo> kMitreMap = {
  'synflood': MitreInfo(
      id: 'T1498.001',
      tactic: 'Impact',
      technique: 'Direct Network Flood'),
  'dos': MitreInfo(id: 'T1498', tactic: 'Impact', technique: 'Network DoS'),
  'ddos': MitreInfo(id: 'T1498', tactic: 'Impact', technique: 'Network DoS'),
  'http_flood':
      MitreInfo(id: 'T1499', tactic: 'Impact', technique: 'Endpoint DoS'),
  'dns_flood': MitreInfo(
      id: 'T1498.002',
      tactic: 'Impact',
      technique: 'Reflection Amplification'),
  'portscan': MitreInfo(
      id: 'T1046',
      tactic: 'Discovery',
      technique: 'Network Service Discovery'),
  'bruteforce': MitreInfo(
      id: 'T1110',
      tactic: 'Credential Access',
      technique: 'Brute Force'),
  'fault':
      MitreInfo(id: 'T1489', tactic: 'Impact', technique: 'Service Stop'),
  'rst_flood': MitreInfo(
      id: 'T1498.001',
      tactic: 'Impact',
      technique: 'Direct Network Flood'),
  'udp_flood': MitreInfo(
      id: 'T1498.001',
      tactic: 'Impact',
      technique: 'Direct Network Flood'),
  'ping_of_death': MitreInfo(
      id: 'T1498.001',
      tactic: 'Impact',
      technique: 'Direct Network Flood'),
  'router_dos':
      MitreInfo(id: 'T1498', tactic: 'Impact', technique: 'Network DoS'),
  'router_synflood': MitreInfo(
      id: 'T1498.001',
      tactic: 'Impact',
      technique: 'Direct Network Flood'),
  'device_offline': MitreInfo(
      id: 'T1499.004',
      tactic: 'Impact',
      technique: 'Application Exhaustion'),
};

const Map<String, AttackInfo> kAttackInfo = {
  'synflood': AttackInfo(
    severity: 'CRITICAL',
    impact: 'TCP stack exhaustion — Server becomes unreachable',
    recommendation:
        'Enable SYN Cookies · Reduce SYN timeout · Rate limit SYN packets',
  ),
  'dos': AttackInfo(
    severity: 'CRITICAL',
    impact: 'Network bandwidth saturation — Service degradation',
    recommendation:
        'Enable rate limiting · Block source IP · Activate upstream filtering',
  ),
  'ddos': AttackInfo(
    severity: 'CRITICAL',
    impact: 'Distributed bandwidth saturation — Multiple sources',
    recommendation:
        'Enable rate limiting · Block source IPs · Contact ISP for upstream filtering',
  ),
  'http_flood': AttackInfo(
    severity: 'HIGH',
    impact: 'HTTP connection exhaustion — Web server unresponsive',
    recommendation:
        'Limit concurrent connections · Enable connection timeout · Deploy WAF',
  ),
  'dns_flood': AttackInfo(
    severity: 'HIGH',
    impact: 'DNS service disruption — Name resolution failure',
    recommendation:
        'Rate limit UDP traffic · Enable DNS response rate limiting',
  ),
  'portscan': AttackInfo(
    severity: 'MEDIUM',
    impact: 'Reconnaissance — Attacker mapping open ports',
    recommendation:
        'Block scanner IP · Close unnecessary ports · Monitor for follow-up attacks',
  ),
  'bruteforce': AttackInfo(
    severity: 'HIGH',
    impact: 'Credential compromise risk — Repeated login attempts',
    recommendation:
        'Block source IP · Enable account lockout · Use key-based auth',
  ),
  'fault': AttackInfo(
    severity: 'HIGH',
    impact: 'Network degradation — Increased latency and packet loss',
    recommendation:
        'Check physical links · Verify routing · Inspect interface errors',
  ),
  'rst_flood': AttackInfo(
    severity: 'HIGH',
    impact:
        'Connection disruption — Established connections forcefully reset',
    recommendation: 'Filter RST packets · Enable TCP sequence validation',
  ),
  'udp_flood': AttackInfo(
    severity: 'CRITICAL',
    impact: 'Bandwidth saturation — High volume UDP traffic',
    recommendation: 'Rate limit UDP · Block source · Enable upstream filtering',
  ),
  'ping_of_death': AttackInfo(
    severity: 'HIGH',
    impact: 'System crash risk — Oversized ICMP packets',
    recommendation: 'Block oversized ICMP · Enable ICMP rate limiting',
  ),
  'router_dos': AttackInfo(
    severity: 'CRITICAL',
    impact: 'Router overwhelmed — All connected networks affected',
    recommendation:
        'Enable control plane policing · Rate limit ICMP · Block source',
  ),
  'router_synflood': AttackInfo(
    severity: 'CRITICAL',
    impact:
        'Router management plane attack — BGP/SSH/Telnet ports targeted',
    recommendation:
        'Enable SYN Cookies on router · Restrict management access',
  ),
  'transit': AttackInfo(
    severity: 'INFO',
    impact: 'Router forwarding attack traffic — Not the target',
    recommendation: 'Focus on the actual target device',
  ),
  'device_offline': AttackInfo(
    severity: 'CRITICAL',
    impact: 'Device unreachable — All services on this device are down',
    recommendation:
        'Check container status · Restart device · Verify network',
  ),
};

// ─── Pretty attack name (نفس React formatAttackName) ───────
const Map<String, String> _kAttackNames = {
  'synflood': 'SYN Flood',
  'dos': 'DoS',
  'ddos': 'DDoS',
  'udp_flood': 'UDP Flood',
  'http_flood': 'HTTP Flood',
  'dns_flood': 'DNS Flood',
  'portscan': 'Port Scan',
  'ping_of_death': 'Ping of Death',
  'rst_flood': 'RST Flood',
  'router_dos': 'Router DoS',
  'router_synflood': 'Router SYN Flood',
  'slowloris': 'Slowloris',
  'bruteforce': 'Brute Force',
  'fault': 'Fault',
  'transit': 'Transit',
  'device_offline': 'Device Offline',
};

String formatAttackName(String? type) {
  if (type == null || type.isEmpty) return 'Unknown';
  return _kAttackNames[type] ?? type.toUpperCase();
}
