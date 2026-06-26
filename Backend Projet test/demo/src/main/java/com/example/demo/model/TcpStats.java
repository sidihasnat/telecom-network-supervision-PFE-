package com.example.demo.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@Entity
@Table(name = "tcp_stats")
public class TcpStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ══════════════════════════════════════════════════════════════
    // القيم القديمة (للتوافق مع الكود الحالي)
    // ══════════════════════════════════════════════════════════════

    // passiveOpens: عدد طلبات الاتصال الواردة (SYN) — تراكمي
    //   Normal:    منخفض
    //   SYN Flood: يرتفع بشدة
    private Integer passiveOpens;

    // tcpRetransmissions: عدد مرات إعادة الإرسال — تراكمي
    //   Normal:    قريب من 0
    //   Fault/SYN Flood: يرتفع
    private Integer tcpRetransmissions;

    // icmpInMsgs: عدد حزم ICMP الواردة — تراكمي (تم تصحيح الاسم)
    //   Normal:    قريب من 0
    //   ICMP Flood: آلاف
    private Integer icmpInMsgs;

    // ══════════════════════════════════════════════════════════════
    // قيم TCP الجديدة من /proc/net/snmp (8 قيم)
    // ══════════════════════════════════════════════════════════════

    // activeOpens: اتصالات بدأها الجهاز (خارجة) — يكشف إذا كان مصدر هجوم
    private Integer activeOpens;

    // attemptFails: محاولات فشل في إنشاء اتصال — يرتفع بشدة تحت SYN Flood
    private Integer attemptFails;

    // estabResets: اتصالات تم قطعها فجأة (RST) — يكشف RST Flood
    private Integer estabResets;

    // currEstab: عدد الاتصالات المفتوحة حالياً — ينهار تحت الهجوم
    private Integer currEstab;

    // inSegs: حزم TCP الواردة — تراكمي
    private Long inSegs;

    // outSegs: حزم TCP الصادرة — تراكمي
    private Long outSegs;

    // inErrs: أخطاء في الحزم الواردة — مشاكل كابلات أو هجوم بحزم تالفة
    private Integer inErrs;

    // outRsts: حزم RST المرسلة — يكشف رفض الاتصالات
    private Integer outRsts;

    // ══════════════════════════════════════════════════════════════
    // قيم ICMP الجديدة (1 قيمة)
    // ══════════════════════════════════════════════════════════════

    // icmpInErrors: أخطاء ICMP — يكشف Ping of Death
    private Integer icmpInErrors;

    // ══════════════════════════════════════════════════════════════
    // 🆕 UDP Statistics (NEW - for DNS, SNMP, etc.)
    // ══════════════════════════════════════════════════════════════

    // udpInDatagrams: Total UDP packets received — cumulative
    //   Normal DNS:    moderate
    //   DNS Flood:     thousands
    private Long udpInDatagrams;

    // udpOutDatagrams: Total UDP packets sent — cumulative
    //   Normal DNS:    matches inbound (queries → responses)
    //   DNS Flood:     much lower than inbound
    private Long udpOutDatagrams;

    // udpInErrors: UDP packets with errors — cumulative
    //   Normal:        near 0
    //   Malformed:     rises
    private Integer udpInErrors;

    // udpNoPorts: Packets sent to closed UDP ports — cumulative
    //   Normal:        0
    //   Port Scan:     rises significantly
    private Integer udpNoPorts;

    // ══════════════════════════════════════════════════════════════
    // قيم محسوبة — معدلات زمنية + نسب
    // الفلسفة: بدون أحكام مسبقة — الـ Model يتعلم بنفسه
    // ══════════════════════════════════════════════════════════════

    // ── TCP Rates ─────────────────────────────────────────────────

    // passiveOpensRate: معدل وصول SYNs في الثانية (delta / time)
    private Double passiveOpensRate;

    // retransRate: معدل إعادة الإرسال في الثانية (delta / time)
    private Double retransRate;

    // icmpInRate: معدل حزم ICMP في الثانية (delta / time)
    private Double icmpInRate;

    // attemptFailsRate: معدل فشل الاتصالات في الثانية
    private Double attemptFailsRate;

    // estabResetsRate: معدل قطع الاتصالات في الثانية
    private Double estabResetsRate;

    // outRstsRate: معدل إرسال RST في الثانية
    private Double outRstsRate;

    // inSegsRate: معدل حزم TCP الواردة في الثانية
    private Double inSegsRate;

    // outSegsRate: معدل حزم TCP الصادرة في الثانية
    private Double outSegsRate;

    // activeOpensRate: معدل الاتصالات الصادرة في الثانية
    private Double activeOpensRate;

    // inOutSegRatio: نسبة الحزم الواردة / الصادرة
    //   Normal:    قريب من 1
    //   SYN Flood: يرتفع (حزم واردة كثيرة بلا ردود)
    private Double inOutSegRatio;

    // rstPerConnection: نسبة RST لكل اتصال مفتوح
    //   Normal:    قريب من 0
    //   RST Flood: يرتفع بشدة
    private Double rstPerConnection;

    // ── UDP Rates (NEW) ───────────────────────────────────────────

    // udpInRate: UDP packets received per second
    //   Normal DNS:      10-100 queries/sec
    //   DNS Flood:       1000+ queries/sec
    @Column(name = "udp_in_rate")
    private Double udpInRate;

    // udpOutRate: UDP packets sent per second
    //   Normal DNS:      matches inRate (queries ≈ responses)
    //   DNS Flood:       much lower (can't respond fast enough)
    @Column(name = "udp_out_rate")
    private Double udpOutRate;

    // udpErrorRate: UDP error packets per second
    //   Normal:          0
    //   Malformed:       rises
    @Column(name = "udp_error_rate")
    private Double udpErrorRate;

    // udpNoPortRate: Packets to closed ports per second
    //   Normal:          0
    //   Port Scan:       10+ per second
    @Column(name = "udp_no_port_rate")
    private Double udpNoPortRate;

    // udpInOutRatio: Ratio of UDP in/out packets
    //   Normal DNS:      ~1.0 (balanced queries/responses)
    //   DNS Flood:       >5.0 (many queries, few responses)
    @Column(name = "udp_in_out_ratio")
    private Double udpInOutRatio;

    // ══════════════════════════════════════════════════════════════
    // Router Metrics (من /proc/net/snmp + ip route)
    // ══════════════════════════════════════════════════════════════

    private Integer routingTableSize;
    private Integer forwardedPackets;
    private Integer inDiscards;
    private Integer noRoutePackets;

    // ── Relation ──────────────────────────────────────────────────
    @OneToOne
    @JoinColumn(name = "metric_id")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private MetricData metricData;
}