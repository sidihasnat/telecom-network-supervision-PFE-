/**
 * MITRE ATT&CK Mapping + Attack Intelligence
 */

export const MITRE_MAP = {
  synflood:        { id: 'T1498.001', tactic: 'Impact', technique: 'Direct Network Flood' },
  dos:             { id: 'T1498',     tactic: 'Impact', technique: 'Network DoS' },
  ddos:            { id: 'T1498',     tactic: 'Impact', technique: 'Network DoS' },
  http_flood:      { id: 'T1499',     tactic: 'Impact', technique: 'Endpoint DoS' },
  dns_flood:       { id: 'T1498.002', tactic: 'Impact', technique: 'Reflection Amplification' },
  portscan:        { id: 'T1046',     tactic: 'Discovery', technique: 'Network Service Discovery' },
  bruteforce:      { id: 'T1110',     tactic: 'Credential Access', technique: 'Brute Force' },
  fault:           { id: 'T1489',     tactic: 'Impact', technique: 'Service Stop' },
  rst_flood:       { id: 'T1498.001', tactic: 'Impact', technique: 'Direct Network Flood' },
  udp_flood:       { id: 'T1498.001', tactic: 'Impact', technique: 'Direct Network Flood' },
  ping_of_death:   { id: 'T1498.001', tactic: 'Impact', technique: 'Direct Network Flood' },
  router_dos:      { id: 'T1498',     tactic: 'Impact', technique: 'Network DoS' },
  router_synflood: { id: 'T1498.001', tactic: 'Impact', technique: 'Direct Network Flood' },
  device_offline: { id: 'T1499.004', tactic: 'Impact', technique: 'Application Exhaustion' },
};

export const ATTACK_INFO = {
  synflood: {
    severity: 'CRITICAL',
    impact: 'TCP stack exhaustion — Server becomes unreachable',
    recommendation: 'Enable SYN Cookies · Reduce SYN timeout · Rate limit SYN packets',
  },
  dos: {
    severity: 'CRITICAL',
    impact: 'Network bandwidth saturation — Service degradation',
    recommendation: 'Enable rate limiting · Block source IP · Activate upstream filtering',
  },
  ddos: {
    severity: 'CRITICAL',
    impact: 'Distributed bandwidth saturation — Multiple sources',
    recommendation: 'Enable rate limiting · Block source IPs · Contact ISP for upstream filtering',
  },
  http_flood: {
    severity: 'HIGH',
    impact: 'HTTP connection exhaustion — Web server unresponsive',
    recommendation: 'Limit concurrent connections · Enable connection timeout · Deploy WAF',
  },
  dns_flood: {
    severity: 'HIGH',
    impact: 'DNS service disruption — Name resolution failure',
    recommendation: 'Rate limit UDP traffic · Enable DNS response rate limiting',
  },
  portscan: {
    severity: 'MEDIUM',
    impact: 'Reconnaissance — Attacker mapping open ports',
    recommendation: 'Block scanner IP · Close unnecessary ports · Monitor for follow-up attacks',
  },
  bruteforce: {
    severity: 'HIGH',
    impact: 'Credential compromise risk — Repeated login attempts',
    recommendation: 'Block source IP · Enable account lockout · Use key-based auth',
  },
  fault: {
    severity: 'HIGH',
    impact: 'Network degradation — Increased latency and packet loss',
    recommendation: 'Check physical links · Verify routing · Inspect interface errors',
  },
  rst_flood: {
    severity: 'HIGH',
    impact: 'Connection disruption — Established connections forcefully reset',
    recommendation: 'Filter RST packets · Enable TCP sequence validation',
  },
  udp_flood: {
    severity: 'CRITICAL',
    impact: 'Bandwidth saturation — High volume UDP traffic',
    recommendation: 'Rate limit UDP · Block source · Enable upstream filtering',
  },
  ping_of_death: {
    severity: 'HIGH',
    impact: 'System crash risk — Oversized ICMP packets',
    recommendation: 'Block oversized ICMP · Enable ICMP rate limiting',
  },
  router_dos: {
    severity: 'CRITICAL',
    impact: 'Router overwhelmed — All connected networks affected',
    recommendation: 'Enable control plane policing · Rate limit ICMP · Block source',
  },
  router_synflood: {
    severity: 'CRITICAL',
    impact: 'Router management plane attack — BGP/SSH/Telnet ports targeted',
    recommendation: 'Enable SYN Cookies on router · Restrict management access',
  },
  transit: {
    severity: 'INFO',
    impact: 'Router forwarding attack traffic — Not the target',
    recommendation: 'Focus on the actual target device',
  },
  device_offline: {
    severity: 'CRITICAL',
    impact: 'Device unreachable — All services on this device are down',
    recommendation: 'Check container status · Restart device · Verify network',
  },
};
