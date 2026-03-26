# HostShield DNS Blocking Deep-Dive Research
## Open Source Android DNS/Hosts-Based Ad-Blocking Technical Analysis

> Research compiled 2026-03-26 for HostShield core engine improvements.

---

## 1. VPN-Based DNS Blocking Apps

### 1.1 AdGuard for Android
- **GitHub:** [AdguardTeam/AdguardForAndroid](https://github.com/AdguardTeam/AdguardForAndroid) (~1.7k stars, open bug tracker only)
- **Key Libraries:** [AdguardTeam/DnsLibs](https://github.com/AdguardTeam/DnsLibs) (C++ DNS filtering/encryption library), [urlfilter](https://github.com/AdguardTeam/urlfilter) (Go filtering engine)
- **Technical Approach:** Uses Android VpnService to intercept all traffic. The core filtering engine (CoreLibs) is cross-platform C++. DnsLibs handles DNS packet processing, filtering, and encrypted DNS (DoH, DoT, DoQ per RFC 9250). Dynamic VPN protocol selection auto-picks HTTP/2 TLS or HTTP/3 QUIC.
- **Notable Features for HostShield:**
  - **CNAME tracker database:** AdGuard maintains [cname-trackers](https://github.com/AdguardTeam/cname-trackers) -- a continuously auto-updated list of CNAME-cloaked trackers discovered via their DNS infrastructure. HostShield should consume this list.
  - **DNS filtering rule syntax:** AdGuard's rule syntax supports wildcards, regex, client-based rules, and denyallowed modifiers. Consider adopting a compatible rule format.
  - **DoQ support (RFC 9250):** DnsLibs implements production-grade DNS-over-QUIC. HostShield should add DoQ alongside DoH.

### 1.2 DNS66
- **GitHub:** [julian-klode/dns66](https://github.com/julian-klode/dns66) (~2.2k stars)
- **Technical Approach:** Pure Java. Establishes a local-only VPN and redirects all DNS server routes into it. The VPN filters DNS queries against a host blocklist; non-blocked queries are forwarded to upstream DNS. No proxy layer -- just raw packet filtering.
- **Notable Features for HostShield:**
  - **Simplicity of DNS-only VPN routing:** DNS66 only routes DNS traffic (port 53) through the VPN rather than all traffic, reducing overhead. If HostShield routes all traffic, consider a DNS-only mode for battery optimization.
  - **Host list priority model:** Later entries override earlier ones, allowing user overrides to take precedence cleanly.

### 1.3 Blokada 5/6
- **GitHub:** [blokadaorg/blokada](https://github.com/blokadaorg/blokada) (~3.2k stars), [blokadaorg/five-android](https://github.com/blokadaorg/five-android)
- **Technical Approach:** Blokada 5 uses on-device VPN-based DNS interception (similar to DNS66). Blokada 6 moved to cloud-based DNS filtering (subscription model). Installs as an "on-device only" VPN inside the Android networking stack.
- **Notable Features for HostShield:**
  - **Battery-optimized VPN:** Blokada's claim of minimal battery drain suggests careful use of `VpnService.Builder.addRoute()` to only capture DNS packets (port 53 UDP/TCP) rather than all traffic.
  - **Cloud/local hybrid model:** Blokada 6's cloud approach could inspire an optional remote blocklist sync mode where a HostShield server pre-compiles trie updates.

### 1.4 NetGuard
- **GitHub:** [M66B/NetGuard](https://github.com/M66B/NetGuard) (~3.5k stars)
- **Technical Approach:** VPN-based per-app firewall with DNS blocking. The critical differentiator: **native C code (`dns.c`) for packet parsing** on the TUN interface. This provides high-performance packet capture at the kernel/userspace boundary.
- **Notable Features for HostShield:**
  - **Native DNS packet parser in C:** NetGuard's `app/src/main/jni/netguard/dns.c` handles raw DNS packet parsing in native code via JNI for speed. HostShield could benefit from a similar native layer for its packet builder/parser instead of pure Kotlin.
  - **Per-app DNS blocking:** Blocks on real domain names with per-app granularity by tracking which app owns each connection via `ConnectivityManager`.
  - **Custom TTL override:** NetGuard allows users to override DNS TTL values, compensating for Android's non-standard DNS caching behavior.
- **Limitation:** Cannot intercept DoH/DoT traffic since it is encrypted end-to-end.

### 1.5 InviZible Pro
- **GitHub:** [Gedsh/InviZible](https://github.com/Gedsh/InviZible) (~2.5k stars)
- **Technical Approach:** Bundles three standalone binaries -- DNSCrypt, Tor, and Purple I2P -- and manages them as Android services. Supports **three operation modes**: VPN mode (local VPN), Root mode (iptables redirection), and Proxy mode (no VPN, no iptables).
- **Notable Features for HostShield:**
  - **Tri-mode architecture:** The cleanest reference for dual/tri-mode (VPN + root + proxy) DNS routing. HostShield's VPN + root model could add a proxy mode for compatibility with other VPNs.
  - **iptables rule refresh on connectivity change:** InviZible refreshes iptables rules on every connectivity change event -- critical for root mode reliability when switching between WiFi/cellular.
  - **DNSCrypt integration:** Pre-built DNSCrypt binary management. HostShield could integrate DNSCrypt protocol support natively in Kotlin rather than shelling out to a binary.
  - **Firewall with module awareness:** The firewall only activates when DNSCrypt/Tor are running, demonstrating clean lifecycle coupling.

### 1.6 personalDNSfilter
- **GitHub:** [IngoZenz/personaldnsfilter](https://github.com/IngoZenz/personaldnsfilter) (~857 stars)
- **Technical Approach:** Pure Java DNS filter proxy. Hooks into DNS resolution and returns loopback (127.0.0.1) for filtered hosts. Can run as a local DNS proxy on the device OR as a network-wide DNS server.
- **Notable Features for HostShield:**
  - **LRU cache for filter results:** Caches both allowed and blocked domain lookups to avoid repeated trie/list traversals. HostShield should ensure its cache covers both positive and negative filter decisions, not just DNS responses.
  - **Network server mode:** Can serve as a DNS server for an entire LAN. HostShield could add a "share protection" mode where one device filters DNS for the whole network.
  - **DoH + DoT upstream support:** Supports encrypted upstream resolution without root.

### 1.7 Nebulo
- **GitHub:** [Ch4t4r/Nebulo](https://github.com/Ch4t4r/Nebulo) (~236 stars)
- **Technical Approach:** Creates a local-only VPN that intercepts **only DNS requests** and encrypts them. Zero dependencies for core DNS capabilities -- entirely original DNS protocol implementation.
- **Notable Features for HostShield:**
  - **DNS-over-QUIC (DoQ) support:** One of the few Android apps implementing DoQ alongside DoH and DoT. HostShield should prioritize DoQ given the RFC 9250 standardization.
  - **Configurable in-memory DNS cache with custom expiry:** Rather than blindly following TTL, allows user-configurable cache lifetimes.
  - **Multiple blocklist format support:** Supports 4 different blocklist formats (hosts files, domains-only, adblock-style, dnsmasq-style). HostShield should support all common formats.
  - **Zero-dependency DNS implementation:** Proves that a purpose-built DNS stack (no dnsjava/minidns dependency) can be lighter and more tailored for mobile.

### 1.8 RethinkDNS
- **GitHub:** [celzero/rethink-app](https://github.com/celzero/rethink-app) (~4.7k stars, Kotlin 99.7%)
- **Technical Approach:** Kotlin-native Android app combining DNS-over-HTTPS, DNS-over-TLS, DNSCrypt, DNS-over-Tor, WireGuard proxifier, and per-app firewall. Uses Go (`firestack`) for UDP/TCP connection handling via `gomobile`. Per-app connection mapping via `ConnectivityService`.
- **Notable Features for HostShield:**
  - **Succinct Radix Trie for blocklist compression:** RethinkDNS compresses ~17M entries from 200+ blocklists into a succinct radix trie (based on Steve Hanov's implementation, modified for faster lookup). This is the most advanced domain lookup structure in the Android ad-blocking space. **HostShield's trie-based domain lookup should evaluate adopting a succinct/compressed radix trie.**
  - **CNAME + HTTPS/SVCB cloaking detection:** Follows CNAME chains and HTTPS/SVCB redirects, matching each hop against blocklists. This is the gold standard for CNAME cloak detection on Android.
  - **Time-based blocking rules:** Allows temporary website blocks with time-based rules -- a useful UX feature.
  - **DNS-over-Tor:** Routes DNS through Tor for maximum anonymity. Consider as an optional mode.
  - **WireGuard split-tunnel integration:** Allows using a real VPN alongside the DNS firewall. Solves the "only one VPN at a time" Android limitation.

---

## 2. Root-Based DNS Blocking

### 2.1 AdAway
- **GitHub:** [AdAway/AdAway](https://github.com/AdAway/AdAway) (~8.9k stars -- highest in this category)
- **Dual-mode:** Root method modifies `/system/etc/hosts`; non-root method uses VPN-based filtering.
- **Root hosts file approach:**
  - Writes blocked domains to the system hosts file mapping them to `127.0.0.1` or `0.0.0.0`
  - Requires remounting `/system` as read-write (increasingly difficult on modern Android with system-as-root, SAR, and dynamic partitions)
  - Optional local web server responds to blocked host requests (prevents connection timeout delays)
- **Actionable for HostShield:**
  - **Hosts file as fallback:** Even with iptables-based blocking, writing a hosts file provides a belt-and-suspenders approach that survives service crashes.
  - **Local web server for blocked responses:** Returning a proper HTTP response (empty page/pixel) for blocked domains prevents UI hangs in apps waiting for timeouts. HostShield should return immediate NXDOMAIN or A-record pointing to a local responder.

### 2.2 iptables/nftables DNS Redirection Patterns

**Current iptables approach (used by InviZible Pro, AFWall+, HostShield):**
```bash
# Redirect all DNS to local proxy
iptables -t nat -A OUTPUT -p udp --dport 53 -j REDIRECT --to-port 5353
iptables -t nat -A OUTPUT -p tcp --dport 53 -j REDIRECT --to-port 5353

# Block direct DNS bypass (prevent apps using hardcoded DNS)
iptables -A OUTPUT -p udp --dport 53 ! -d 127.0.0.1 -j DROP
iptables -A OUTPUT -p tcp --dport 53 ! -d 127.0.0.1 -j DROP

# Also intercept DoT (port 853)
iptables -t nat -A OUTPUT -p tcp --dport 853 -j REDIRECT --to-port 5353
```

**NFLOG integration pattern:**
```bash
# Log all DNS queries for analytics before filtering
iptables -A OUTPUT -p udp --dport 53 -j NFLOG --nflog-group 1 --nflog-prefix "DNS"
# Userspace reads from NFLOG group via libnetfilter_log or nflog socket
```

**nftables migration considerations:**
- AFWall+ (the leading Android iptables firewall, [ukanth/afwall](https://github.com/ukanth/afwall)) has **not yet migrated to nftables**
- Android 12+ ships with nftables support in the kernel, but the `iptables` binary is still present as a compatibility shim (`iptables-nft`)
- **Recommendation for HostShield:** Detect whether the device uses legacy iptables or nftables backend (`iptables -V` shows `nf_tables` or `legacy`). Use the iptables command-line compatibility layer for now, but architect the rule generation to be backend-agnostic for future nftables native support.

### 2.3 Root Detection and Binary Management
- **InviZible Pro pattern:** Bundles pre-compiled arm/arm64/x86 binaries for DNSCrypt, Tor, I2P. Extracts to app private directory, sets executable permissions, manages lifecycle via `ProcessBuilder`.
- **AdAway pattern:** Uses `su` binary for root commands. Detects root via Magisk/SuperSU/KernelSU APIs.
- **Recommendation for HostShield:** Support KernelSU detection alongside Magisk and legacy SuperSU. Use `libsu` (topjohnwu's library) for modern root shell management.

---

## 3. DNS Packet Parsing/Building

### 3.1 Library Comparison

| Library | Stars | Language | EDNS | DNSSEC | Android Support | Size |
|---------|-------|----------|------|--------|-----------------|------|
| [dnsjava](https://github.com/dnsjava/dnsjava) | ~1.1k | Java | EDNS0 | Full (validating stub resolver) | Yes (via ConnectivityManager init) | ~500KB |
| [MiniDNS](https://github.com/MiniDNS/minidns) | ~243 | Java | EDNS0 | DNSSEC + DANE (not audited) | Yes (minidns-android23 module) | Modular, ~100KB core |
| NetGuard `dns.c` | N/A | C (JNI) | No | No | Native | Minimal |
| Nebulo (custom) | N/A | Kotlin | Partial | No | Yes | Zero-dep |

### 3.2 Recommendations for HostShield

**Option A: Hybrid approach (recommended)**
- Use a **Kotlin-native DNS packet builder/parser** for the hot path (query construction, response parsing, cache lookup). This avoids JNI overhead for simple operations.
- Use **dnsjava** as a dependency for complex operations: DNSSEC validation, TSIG, zone transfers, and full record type coverage.
- Use **JNI/native C** only for the VPN TUN interface read/write loop (like NetGuard's approach) where zero-copy buffer handling matters.

**Option B: Full native DNS stack**
- Port the packet builder to C/Rust via JNI for maximum performance.
- Suitable if HostShield processes >10K queries/sec (unlikely on mobile but relevant for network-server mode).

**EDNS support specifics:**
- Implement EDNS0 (RFC 6891) with OPT pseudo-record in all queries
- Support EDNS Client Subnet (ECS, RFC 7871) -- important for CDN optimization but privacy-sensitive; make it opt-in
- Set EDNS buffer size to 1232 bytes (per DNS Flag Day 2020 recommendation) to avoid fragmentation
- Include EDNS padding (RFC 7830) for DoH/DoT queries to prevent traffic analysis

---

## 4. DNS Caching Strategies

### 4.1 TTL Handling
- **Standard:** Cache responses for the minimum TTL across all RRs in the answer section.
- **Android quirk:** Android's built-in resolver ignores DNS TTL and applies its own caching. HostShield must operate its own cache independent of the system resolver (which it already does via VPN interception).
- **Cap maximum TTL:** Implement a configurable max TTL cap (e.g., 86400 seconds / 1 day) per RFC 8767's suggestion of 7-day maximum. RethinkDNS and Nebulo both allow user-configurable cache expiry.

### 4.2 Negative Caching (RFC 2308, RFC 9520)
- Cache NXDOMAIN and NODATA responses using the SOA record's minimum TTL.
- **RFC 9520 (2024):** Also cache resolution failures (SERVFAIL, timeouts) with a short TTL (e.g., 1-5 seconds) to prevent retry storms against failing upstreams.
- HostShield should differentiate between:
  - NXDOMAIN (domain doesn't exist) -- cache for SOA minimum TTL
  - NODATA (domain exists, no records of requested type) -- cache for SOA minimum TTL
  - SERVFAIL from upstream -- cache for 1-5 seconds (configurable)
  - Network timeout -- cache for 1 second, then retry with exponential backoff

### 4.3 Prefetching (Proactive Cache Refresh)
- **Unbound's algorithm:** When a cached entry's remaining TTL drops below 10% of original TTL, serve the stale entry to the client and dispatch a background refresh.
- **Implementation for HostShield:**
  ```
  if (entry.remainingTTL < entry.originalTTL * 0.10 && entry.queryCount > threshold) {
      serveFromCache(entry)  // respond immediately
      backgroundRefresh(entry)  // async upstream query
  }
  ```
- Only prefetch for **popular domains** (track query frequency). A simple counter with a threshold (e.g., >3 queries during the TTL period) prevents wasting bandwidth on rarely-queried domains.
- **Trade-off:** ~10% increase in upstream queries, but dramatically improves perceived latency for frequently-accessed domains.

### 4.4 Serve-Stale (RFC 8767)
- When an upstream resolver is unreachable, serve expired cache entries rather than returning SERVFAIL.
- Set a **stale TTL cap** (e.g., 1-3 days beyond original expiry).
- Mark stale responses with a low TTL (e.g., 30 seconds) so clients re-query soon.
- **Implementation priority:** This is critical for mobile where connectivity is intermittent. When the device switches between WiFi/cellular, there's a brief period where DNS resolution may fail. Serve-stale bridges this gap seamlessly.
- Akamai has used this in production since 2011; BIND 9.12+ and Unbound both implement it.

### 4.5 Cache Architecture Recommendation
```
HostShield DNS Cache
+-- L1: In-memory LRU (hot entries, ~10K domains)
|   +-- Positive cache (A, AAAA, CNAME, etc.)
|   +-- Negative cache (NXDOMAIN, NODATA)
|   +-- Filter decision cache (blocked/allowed per personalDNSfilter pattern)
+-- L2: On-disk persistent cache (survives app restart, ~100K domains)
|   +-- SQLite or memory-mapped file
+-- Prefetch queue (background refresh for entries at <10% TTL)
```

---

## 5. Encrypted DNS

### 5.1 Protocol Support Matrix Across Projects

| Project | DoH | DoT | DoQ | DNSCrypt | DNS-over-Tor |
|---------|-----|-----|-----|----------|--------------|
| AdGuard | Yes | Yes | Yes (RFC 9250) | Yes | No |
| DNS66 | No | No | No | No | No |
| Blokada 5 | No | No | No | No | No |
| NetGuard | No | No | No | No | No |
| InviZible Pro | Via DNSCrypt binary | No | No | Yes | Yes (via Tor) |
| personalDNSfilter | Yes | Yes | No | No | No |
| Nebulo | Yes | Yes | Yes | No | No |
| RethinkDNS | Yes | Yes | No | Yes | Yes |
| **HostShield (current)** | **Yes** | **No** | **No** | **No** | **No** |

**Priority additions for HostShield:**
1. **DoT (DNS-over-TLS, RFC 7858):** Simpler than DoH, lower overhead, widely supported. Use Android's built-in TLS stack.
2. **DoQ (DNS-over-QUIC, RFC 9250):** Growing adoption, supported by AdGuard DNS, Cloudflare (experimental), NextDNS. Use a QUIC library like `cronet` (Chromium's QUIC stack available as an Android library) or `kwik` (pure Java QUIC).
3. **DNSCrypt:** Still relevant for privacy-focused users. Can wrap dnscrypt-proxy binary or implement the protocol natively.

### 5.2 CNAME Cloaking Detection

**Current state of the art:**
- **RethinkDNS:** Follows CNAME chains AND HTTPS/SVCB record redirects, matching each hop against blocklists. This is the most comprehensive approach.
- **AdGuard:** Maintains a continuously-updated [CNAME tracker list](https://github.com/AdguardTeam/cname-trackers) discovered by scanning the web at scale.
- **NextDNS:** Maintains [cname-cloaking-blocklist](https://github.com/nextdns/cname-cloaking-blocklist) of known CNAME cloaking destinations.
- **uBlock Origin (Firefox):** Uses browser DNS API to resolve CNAME chains and match against filter lists. Blocks ~70% of CNAME-cloaked trackers.

**Recommended implementation for HostShield:**
```
1. Intercept DNS response
2. If response contains CNAME records:
   a. Resolve the full CNAME chain (follow up to N=8 hops)
   b. Check EACH domain in the chain against blocklists
   c. If ANY domain in the chain matches a blocklist, block the original query
3. If response contains HTTPS/SVCB records:
   a. Extract TargetName from SVCB/HTTPS records
   b. Check TargetName against blocklists
4. Consume both AdGuard cname-trackers and NextDNS cname-cloaking-blocklist
5. Cache CNAME chains to avoid repeated resolution overhead
```

### 5.3 ECH (Encrypted Client Hello) Considerations

- **RFC 9849** (published 2025) standardizes ECH for TLS 1.3.
- ECH encrypts the SNI field using keys published in DNS SVCB/HTTPS records.
- **Impact on HostShield:** ECH makes it impossible to determine the destination server from TLS handshake alone. DNS-level blocking becomes the **only** viable blocking layer when ECH is deployed.
- **Actionable steps:**
  - Parse HTTPS/SVCB records (TYPE 64/65) in DNS responses to extract ECHConfig
  - Log ECH-enabled domains for user visibility
  - Consider blocking HTTPS/SVCB records that contain ECHConfig for blocked domains (prevents clients from establishing ECH connections to tracker domains)
  - Support SVCB-aware DNS resolution to properly handle AliasMode (SVCB with TargetName) and ServiceMode records

### 5.4 DNSSEC Validation

**Library options:**
- **dnsjava:** Full DNSSEC validating stub resolver, based on Unbound Java prototype. Production-tested. Supports Extended DNS Errors (EDE, RFC 8914) for validation failure reasons.
- **MiniDNS:** DNSSEC + DANE support via `minidns-dnssec` module, but has NOT undergone security audit. More lightweight.
- **dnssecjava** ([ibauersachs/dnssecjava](https://github.com/ibauersachs/dnssecjava)): Standalone DNSSEC validating stub resolver for Java, can complement dnsjava.

**Recommendation for HostShield:**
- Implement DNSSEC validation as opt-in (it adds latency and many domains are not signed).
- Use dnsjava's DNSSEC module for validation.
- Display DNSSEC status (secure/insecure/bogus) in the query log UI.
- For bogus responses (failed validation), return SERVFAIL with EDE code.

### 5.5 DNS Stamps (`sdns://`)

DNS stamps encode all parameters to connect to a DNS resolver in a single compact string:
- Format: `sdns://` prefix + base64url-encoded configuration
- Supports: DNSCrypt, DoH, DoT, DoQ, Oblivious DoH, plain DNS, DNS relay
- Used by: dnscrypt-proxy, AdGuard, NextDNS, Simple DNSCrypt, RethinkDNS

**Recommendation for HostShield:**
- Implement DNS stamp parsing/generation per the [specification](https://dnscrypt.info/stamps-specifications/)
- Allow users to add servers by pasting `sdns://` stamps (much easier than manual IP/hostname/path configuration)
- Pre-populate with stamps for popular resolvers (Cloudflare, Google, Quad9, NextDNS, AdGuard DNS, Mullvad)
- Support stamp QR code scanning for easy mobile configuration sharing

---

## 6. Summary: Prioritized Improvements for HostShield

### Tier 1 -- High Impact, Moderate Effort
| Improvement | Reference Project | Impact |
|-------------|-------------------|--------|
| Succinct radix trie for blocklists | RethinkDNS | 10-50x memory reduction for large blocklists |
| CNAME chain + HTTPS/SVCB cloaking detection | RethinkDNS, AdGuard | Blocks trackers that evade simple domain matching |
| Serve-stale (RFC 8767) | Unbound, Akamai | Eliminates DNS failures during connectivity transitions |
| Negative caching (NXDOMAIN/NODATA/SERVFAIL) | RFC 2308, RFC 9520 | Reduces upstream query volume by 15-30% |
| DNS stamps (`sdns://`) support | DNSCrypt, AdGuard | Dramatically simplifies server configuration UX |
| Consume AdGuard + NextDNS CNAME blocklists | AdGuard, NextDNS | Immediate coverage of known CNAME-cloaked trackers |

### Tier 2 -- High Impact, Higher Effort
| Improvement | Reference Project | Impact |
|-------------|-------------------|--------|
| DoT (DNS-over-TLS) support | Nebulo, personalDNSfilter | Second most common encrypted DNS protocol |
| DoQ (DNS-over-QUIC, RFC 9250) support | Nebulo, AdGuard DnsLibs | Faster than DoH, lower latency, multiplexed |
| Prefetching for popular domains | Unbound algorithm | Near-zero latency for frequently accessed domains |
| Native C/Rust packet parser for TUN loop | NetGuard `dns.c` | Reduces GC pressure and latency in hot path |
| iptables/nftables backend detection | AFWall+ (pending) | Future-proofs root mode for Android 14+ |

### Tier 3 -- Moderate Impact, Valuable
| Improvement | Reference Project | Impact |
|-------------|-------------------|--------|
| L2 persistent disk cache | personalDNSfilter LRU | Instant DNS after app/device restart |
| DNSSEC validation (opt-in) | dnsjava, MiniDNS | Security for users on untrusted networks |
| Filter decision LRU cache | personalDNSfilter | Avoids repeated trie lookups for hot domains |
| Multiple blocklist format support | Nebulo (4 formats) | Compatibility with all major blocklist sources |
| EDNS padding (RFC 7830) | AdGuard DnsLibs | Prevents DNS query traffic analysis |
| ECH-aware SVCB/HTTPS record handling | RFC 9849 | Prepares for ECH deployment wave |
| Proxy mode (no VPN, no root) | InviZible Pro | Works alongside other VPN apps |
| Network DNS server mode | personalDNSfilter | Share protection with LAN devices |
| KernelSU root detection | Modern root tools | Support for latest root solutions |

---

## Sources

- [AdGuard for Android - GitHub](https://github.com/AdguardTeam/AdguardForAndroid)
- [AdGuard DnsLibs - GitHub](https://github.com/AdguardTeam/DnsLibs)
- [AdGuard CNAME Trackers - GitHub](https://github.com/AdguardTeam/cname-trackers)
- [AdGuard DNS Content Blocking at Scale](https://adguard-dns.io/en/blog/dns-content-blocking-at-scale.html)
- [DNS66 - GitHub](https://github.com/julian-klode/dns66)
- [Blokada - GitHub](https://github.com/blokadaorg/blokada)
- [Blokada 5 Android - GitHub](https://github.com/blokadaorg/five-android)
- [NetGuard - GitHub](https://github.com/M66B/NetGuard)
- [NetGuard dns.c - GitHub](https://github.com/M66B/NetGuard/blob/master/app/src/main/jni/netguard/dns.c)
- [InviZible Pro - GitHub](https://github.com/Gedsh/InviZible)
- [InviZible Operation Modes Wiki](https://github.com/Gedsh/InviZible/wiki/Operation-Modes)
- [personalDNSfilter - GitHub](https://github.com/IngoZenz/personaldnsfilter)
- [Nebulo - GitHub](https://github.com/Ch4t4r/Nebulo)
- [RethinkDNS App - GitHub](https://github.com/celzero/rethink-app)
- [RethinkDNS Serverless - GitHub](https://github.com/serverless-dns/serverless-dns)
- [RethinkDNS Blocklists - GitHub](https://github.com/serverless-dns/blocklists)
- [RethinkDNS FAQ](https://rethinkdns.com/faq)
- [AdAway - GitHub](https://github.com/AdAway/AdAway)
- [AFWall+ - GitHub](https://github.com/ukanth/afwall)
- [dnsjava - GitHub](https://github.com/dnsjava/dnsjava)
- [MiniDNS - GitHub](https://github.com/MiniDNS/minidns)
- [dnssecjava - GitHub](https://github.com/ibauersachs/dnssecjava)
- [NextDNS CNAME Cloaking Blocklist - GitHub](https://github.com/nextdns/cname-cloaking-blocklist)
- [DNS Stamps Specification](https://dnscrypt.info/stamps-specifications/)
- [DNSCrypt Project](https://www.dnscrypt.org/)
- [RFC 8767 - Serving Stale Data](https://www.rfc-editor.org/rfc/rfc8767.html)
- [RFC 2308 - Negative Caching](https://datatracker.ietf.org/doc/html/rfc2308)
- [RFC 9520 - Negative Caching of Resolution Failures](https://datatracker.ietf.org/doc/rfc9520/)
- [RFC 9849 - TLS Encrypted Client Hello](https://www.rfc-editor.org/rfc/rfc9849.html)
- [RFC 9250 - DNS over QUIC](https://www.rfc-editor.org/rfc/rfc9250)
- [RFC 9460 - SVCB and HTTPS Records](https://www.rfc-editor.org/rfc/rfc9460)
- [CNAME Cloaking - Palo Alto Unit42](https://unit42.paloaltonetworks.com/cname-cloaking/)
- [CNAME Cloaking Detection - IEEE](https://ieeexplore.ieee.org/document/9403411/)
- [Unbound Serve-Stale Documentation](https://unbound.docs.nlnetlabs.nl/en/latest/topics/core/serve-stale.html)
- [Android VPN Blueprint Discussion](https://github.com/orgs/community/discussions/171226)
