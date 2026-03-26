# Android Firewall, Network Monitoring & Privacy -- Open Source Research

> Research compiled 2026-03-25 for HostShield feature planning.

---

## 1. Android Firewalls

### 1.1 NetGuard

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/M66B/NetGuard |
| **Stars** | ~3,530 |
| **License** | GPL-3.0 |
| **Language** | Java + C (native VPN) |

**Architecture:**
- Uses `VpnService` to create a local TUN interface -- all device traffic is routed through it.
- Core logic lives in `ServiceSinkhole.java` (extends `VpnService`).
- Native C layer (`netguard.c`) performs actual packet inspection and forwarding in the TUN read/write loop.
- When an app is blocked, packets are "sinkholed" -- dropped silently with no remote server involved.

**Per-App Network Access Control:**
- Each app listed with separate toggles for Wi-Fi and Mobile Data.
- Allowed/blocked state stored per UID; the native layer matches outgoing packets to UIDs via `/proc/net` and the kernel's UID-based routing.

**Foreground vs Background:**
- Android restricts apps from seeing which other apps are in the foreground (post-Android 10 privacy changes).
- NetGuard works around this with a **"screen on/off" condition** rather than true foreground detection. When screen is off, background rules apply; when screen is on, foreground rules apply. This is automatic and avoids the Accessibility Service requirement.

**Wi-Fi vs Mobile Data Differentiation:**
- Distinct allow/block columns for Wi-Fi and mobile data per app.
- Handles **metered Wi-Fi** as a separate category (configurable in Network Settings > "Handle Metered WiFi Networks").

**LAN/Localhost Access:**
- Subnet routing configurable in network settings.
- Tethering support available.
- Localhost traffic generally not intercepted (VPN TUN interface only sees routed traffic).

**Metered Network Rules:**
- Built-in metered Wi-Fi detection; applies mobile-data rules to metered Wi-Fi connections when enabled.

**Key Takeaway for HostShield:** NetGuard's screen-on/off approach for fg/bg detection is pragmatic and avoids Accessibility Service permission. The sinkhole pattern (drop packets in native C) is highly efficient.

---

### 1.2 AFWall+

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/ukanth/afwall |
| **Stars** | ~3,320 |
| **License** | GPL-3.0 |
| **Language** | Java |

**Architecture:**
- **Requires root.** Directly manipulates Linux `iptables` / `ip6tables` rules.
- Rules are applied at the kernel netfilter level -- far lower than VPN-based solutions.
- Generates iptables chain rules per UID with `-m owner --uid-owner <uid>`.

**Per-App Network Access Control:**
- Separate columns: Wi-Fi, Mobile Data (2G/3G/4G/5G), Roaming, VPN, Tethering, LAN.
- Most granular per-app control of any open source Android firewall.

**Foreground vs Background:**
- Not explicitly differentiated at the iptables level; however, rules persist regardless of app state, effectively blocking all background traffic for denied apps.

**Wi-Fi vs Mobile Data:**
- Fully independent rulesets per network type per app.
- Roaming treated as a separate network type.

**LAN/Localhost:**
- Dedicated LAN toggle per app -- can allow local network while blocking internet.
- Localhost traffic controllable via iptables OUTPUT chain.

**Metered Network:**
- No built-in metered detection; relies on Android's own metered network classification.

**Key Takeaway for HostShield:** AFWall+'s per-app LAN toggle is a feature worth replicating. The separate Roaming column is excellent UX. For non-root devices, these rules must be approximated in the VPN layer.

---

### 1.3 RethinkDNS (Rethink: DNS + Firewall + VPN)

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/celzero/rethink-app |
| **Stars** | ~4,700 |
| **License** | Apache-2.0 |
| **Language** | Kotlin + Go (native firestack) |

**Architecture:**
- VPN-based, no root. Network stack (`firestack`) is a hard fork of Jigsaw-Code/outline-go-tun2socks written in Go.
- DNS filtering at the VPN layer: blocked domains get empty responses.
- Split-tunnel architecture: DNS queries trapped at VPN DNS endpoint, relayed to user-chosen DoH / DoT / DNSCrypt / ODoH resolver.

**Per-App Network Access Control:**
- Block/allow entire apps.
- **Domain-per-app rules**: allow or deny specific domains for specific apps (unique feature).
- Category-based blocking: block all "Social" or "Games" apps using Play Store categories.
- User-defined denylists.
- IP-based firewall rules (editable).

**Foreground vs Background:**
- Uses **Accessibility Service** to detect foreground/background app state.
- Rules can trigger on: screen-on/screen-off, app-foreground/app-background.

**Wi-Fi vs Mobile (Metered vs Unmetered):**
- Rules differentiate **metered vs unmetered connections** rather than Wi-Fi vs mobile.
- This is arguably more correct since Wi-Fi can be metered and mobile can be unmetered.

**LAN/Localhost:**
- Not prominently featured; DNS-layer blocking does not affect LAN-only traffic.

**Proxy Support:**
- WireGuard, SOCKS5, HTTP CONNECT proxy tunnels.
- Per-app split tunneling: route different apps over different tunnels.

**Connection Tracker:**
- Built-in per-app connection log: when connections were made, how many, to where.
- Flags suspicious/unknown connections.

**Key Takeaway for HostShield:** RethinkDNS's domain-per-app rules and metered/unmetered distinction are best-in-class. The category-based blocking (Social, Games, etc.) is excellent UX. The Go-based network stack is worth studying for performance.

---

### 1.4 LostNet NoRoot Firewall

| Field | Detail |
|-------|--------|
| **Availability** | Closed source (Play Store / Amazon) |
| **Stars** | N/A (not on GitHub) |

**Notable Features:**
- VPN-based, no root.
- **Country-based blocking**: block/unblock access to specific countries per app -- GeoIP integrated.
- **Per-country data usage monitoring** per app.
- Background activity restriction: block apps from internet access while backgrounded.
- Built-in **packet sniffer** with CloudShark.org export (Wireshark compatible).
- Instant notifications when blocked apps attempt connections or when apps try to reach blocked countries.

**Key Takeaway for HostShield:** LostNet's country-based blocking with GeoIP is a compelling feature. The per-country usage monitoring is unique and valuable for privacy-conscious users.

---

### 1.5 ShizuWall (Shizuku-based)

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/AhmetCanArslan/ShizuWall |
| **Stars** | ~1,290 |

**Notable:** Uses Shizuku framework instead of VPN -- avoids the single-VPN limitation. Lightweight, no VPN conflict. Worth watching as an alternative approach.

---

### 1.6 NoRoot Firewall

| Field | Detail |
|-------|--------|
| **Availability** | Play Store (closed source) |

**Features:**
- VPN-based, per-app Wi-Fi and mobile data toggles.
- Advanced domain/IP/hostname filter rules with import/export.
- Simple UI focused on accessibility.

---

## 2. Network Traffic Monitoring & Stats

### 2.1 Approaches Compared

| Approach | Pros | Cons |
|----------|------|------|
| **NetworkStatsManager** (API 23+) | Official API; per-UID, per-network-type, time-bounded; no VPN needed; battery efficient | Requires `READ_PHONE_STATE` or carrier privileges for mobile stats; data delayed (not real-time); 2-hour bucket minimum |
| **TrafficStats** | Simple API; real-time cumulative bytes | Resets on boot; no per-time-range; unreliable per-UID on Android N+ |
| **VPN Packet Counting** | Real-time; per-connection granularity; works on all apps | Requires active VPN; battery cost; can't run alongside other VPNs |
| **/proc/net Parsing** | No special permissions on older Android; real-time | Restricted on Android 10+ (SELinux); fragile; platform-dependent |

### 2.2 Best Practices for HostShield

- **Primary**: Use `NetworkStatsManager` for historical per-app bandwidth with `queryDetailsForUid()`. Provides WiFi vs mobile breakdown, time-bounded queries, and survives reboots.
- **Real-time overlay**: When VPN is active, count bytes in the TUN read/write loop (like PCAPdroid and NetGuard do). This gives per-connection byte counts with zero additional overhead.
- **Data usage alerts**: `NetworkStatsManager` supports bucket-based queries. Implement a periodic `WorkManager` job that checks cumulative usage against user-defined quotas and fires notifications.
- **Per-app bandwidth widget**: Combine `NetworkStatsManager` historical data with VPN real-time counters for a live + historical dashboard.

---

## 3. PCAP / Packet Capture

### 3.1 PCAPdroid

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/emanuele-f/PCAPdroid |
| **Stars** | ~3,850 |
| **License** | GPL-3.0 |
| **Language** | Java + C |

**Capture Architecture:**
- VPN mode (no root): creates TUN interface, reads raw IP packets.
- Root mode: uses `libpcap` for raw socket capture.
- Packets processed in native C layer for performance.

**Export Formats:**
- PCAP and **PCAP-NG** format support.
- Export options: save to file, download via browser, **stream to remote receiver** (e.g., Wireshark over UDP).
- Real-time PCAP streaming enables live analysis on desktop.

**Protocol Detection (nDPI Integration):**
- Integrates [ntop/nDPI](https://github.com/ntop/nDPI) (4,390 stars) for deep packet inspection.
- Identifies 300+ application protocols from packet patterns.
- Extracts **SNI** from TLS ClientHello.
- Extracts HTTP URLs, DNS queries, remote IP addresses.

**TLS Metadata & Fingerprinting:**
- nDPI natively supports **JA3** (client + server) and **JA4** (including ja4_r raw) fingerprints.
- nDPI has introduced its own "nDPI fingerprint" combining TCP fingerprint + JA4 + TLS SHA1 certificate.
- PCAPdroid gets JA3/JA4 "for free" through the nDPI integration.

**TLS Decryption:**
- Optional decryption via customized mitmproxy.
- Decrypted payloads viewable in-app.
- Decrypted traffic exportable as PCAP-NG.

**Real-Time UI:**
- Connections tab: per-app, per-connection list with protocol, state, destination, bytes.
- Filtering: by IP, host, protocol, app name, UID.
- Long-press context menu for quick filtering and hiding.
- Search bar for real-time connection filtering.

**Firewall & Malware Detection (Paid Features):**
- Firewall rules to block apps, domains, and IPs.
- **Malware detection via third-party blacklists** (updated daily).
- Blocks traffic to/from malicious hosts in VPN mode.
- Shows count of active IP and domain rules.

**Key Takeaway for HostShield:** PCAPdroid's nDPI integration is the gold standard for Android packet analysis. Adopt nDPI for protocol detection + JA3/JA4 fingerprinting. The PCAP-NG streaming to remote Wireshark is an excellent power-user feature. The daily-updated malware blacklist model is simple and effective.

---

## 4. GeoIP and Threat Intelligence

### 4.1 MaxMind GeoLite2 Integration

| Resource | Detail |
|----------|--------|
| **Java Library** | [maxmind/GeoIP2-java](https://github.com/maxmind/GeoIP2-java) -- 856 stars |
| **Maven** | `com.maxmind.geoip2:geoip2:5.0.2` |
| **Database** | GeoLite2-Country.mmdb, GeoLite2-City.mmdb, GeoLite2-ASN.mmdb |
| **Update Frequency** | Weekly (Tuesdays) |
| **License** | CC BY-SA 4.0 (database), Apache-2.0 (library) |

**Android Implementation Approach:**
1. Bundle `GeoLite2-Country.mmdb` (~6MB) and `GeoLite2-ASN.mmdb` (~8MB) in assets or download on first launch.
2. Use `DatabaseReader` from GeoIP2-java -- thread-safe, reusable.
3. Enable `CHMCache` for ~2MB memory overhead with faster repeated lookups.
4. For City-level data, `GeoLite2-City.mmdb` is ~70MB -- consider downloading separately and storing on external storage.
5. Implement weekly background update via WorkManager.

**Mirror for auto-updates:** [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) provides auto-updated database mirrors.

### 4.2 IP Reputation / Threat Intelligence

**AbuseIPDB:**
- REST API: `GET /api/v2/check?ipAddress=X.X.X.X`
- Free tier: 1,000 checks/day.
- Returns: abuse confidence score (0-100), total reports, country, ISP, usage type.
- Best used for on-demand checks when user inspects a connection, not bulk real-time.

**Blocklist-based Approach (like PCAPdroid):**
- Download IP/domain blacklists daily (e.g., abuse.ch URLhaus, Spamhaus DROP, Emerging Threats).
- Store as efficient data structures (radix trie for IPs, hash set for domains).
- Check every connection in the VPN loop -- O(1) per lookup.
- This is the practical approach for real-time on-device threat detection.

**Recommended Feeds:**
| Feed | Type | URL |
|------|------|-----|
| abuse.ch URLhaus | Malware URLs/IPs | https://urlhaus.abuse.ch/api/ |
| Spamhaus DROP | Worst IP ranges | https://www.spamhaus.org/drop/ |
| Emerging Threats | IPs | https://rules.emergingthreats.net/ |
| Disconnect Tracker List | Tracker domains | Used by Firefox, TrackerControl |
| DuckDuckGo Tracker Radar | Tracker domains | Used by TrackerControl |
| Hagezi DNS Blocklists | Ads/trackers/malware domains | Popular in RethinkDNS |

### 4.3 Connection Visualization

**Globe / Map View:**
- [geoip-attack-map](https://github.com/MatthewClarkMay/geoip-attack-map) -- real-time globe visualization parsing syslog.
- [geoip-live-map](https://github.com/ramanenka/geoip-live-map) -- real-time access log visualization on a map.
- For Android: use a lightweight WebView-based globe (e.g., Globe.GL / Cesium.js) or a custom OpenGL view.
- Feed connection data from VPN layer: source (device) -> destination (GeoIP-resolved lat/lng).
- LostNet pioneered per-country connection visualization on Android.

**Key Takeaway for HostShield:** Bundle GeoLite2-Country + ASN databases. Use blocklist-based threat detection for real-time (not API-based). Reserve AbuseIPDB for on-demand "deep check" when user taps a connection. A globe view showing active connections by country would be a strong differentiator.

---

## 5. Privacy Scoring & App Analysis

### 5.1 Exodus Privacy / ETIP

| Field | Detail |
|-------|--------|
| **Platform** | [exodus-privacy.eu.org](https://exodus-privacy.eu.org/) |
| **ETIP GitHub** | https://github.com/Exodus-Privacy/etip (71 stars) |
| **Android App** | https://github.com/Exodus-Privacy/exodus-android-app (956 stars) |
| **Database** | https://etip.exodus-privacy.eu.org/ |

**Tracker Detection Methodology:**
1. **Code signature matching**: Each tracker in ETIP has one or more **Java/Kotlin class name patterns** (e.g., `com.google.android.gms.analytics`, `com.facebook.ads`).
2. The scanner extracts the **DEX class list** from the APK (no execution needed).
3. Pattern matching against the ETIP signature database identifies embedded tracker SDKs.
4. Additionally checks for **domain names** in the APK's string constants.
5. ETIP database fields per tracker: name, code signatures, network signatures (domains), categories, documentation links.

**On-Device Implementation:**
- The Exodus Android app queries the Exodus web API for pre-analyzed reports.
- For on-device analysis, use Android's `PackageManager` to get the APK path, then extract the DEX class list and match against ETIP signatures.

### 5.2 ClassyShark3xodus

| Field | Detail |
|-------|--------|
| **F-Droid** | https://f-droid.org/packages/com.oF2pks.classyshark3xodus/ |

- Based on Google's ClassyShark (bytecode viewer).
- Scans **installed APKs locally** -- no server needed.
- Matches class names against Exodus ETIP tracker signatures.
- Shows warnings for known trackers found in app bytecode.

### 5.3 TrackerControl

| Field | Detail |
|-------|--------|
| **GitHub** | https://github.com/TrackerControl/tracker-control-android |
| **Stars** | ~2,420 |
| **License** | GPL-3.0 |
| **Language** | Java |

**Dual Detection Methodology:**
1. **Static analysis**: Uses Exodus/ETIP code signatures to detect tracker libraries in APK bytecode.
2. **Network analysis**: VPN-based traffic monitoring matches connections against:
   - Disconnect blocklist (used by Firefox)
   - DuckDuckGo Tracker Radar (mobile-specific)
   - Custom in-house blocklist (derived from analyzing ~2M apps)
3. Combining static + network analysis provides evidence of actual data exfiltration, not just SDK presence.

### 5.4 Privacy Scoring Model

**Recommended Scoring Formula for HostShield:**

```
Privacy Score = 100 - (tracker_penalty + permission_penalty + network_penalty)

tracker_penalty:
  - Per tracker SDK detected: -5 points (max -40)
  - Analytics trackers: -3 each
  - Advertising trackers: -5 each
  - Fingerprinting trackers: -7 each

permission_penalty:
  - Each dangerous permission: weighted by severity
  - CAMERA, MICROPHONE, LOCATION: -5 each
  - CONTACTS, CALL_LOG, SMS: -4 each
  - STORAGE, PHONE_STATE: -2 each
  - Total cap: -30

network_penalty:
  - Connections to known tracker domains: -3 per unique domain (max -20)
  - Connections to countries with poor privacy laws: -2 per country
  - Unencrypted HTTP connections: -5 per unique host
  - Total cap: -30
```

**Implementation Steps:**
1. On app install / periodic scan: extract class list from APK, match against ETIP database.
2. Query `PackageManager` for declared permissions.
3. When VPN is active: log destination domains per app, match against tracker blocklists.
4. Compute composite score, cache results, surface in app detail view.

**Key Takeaway for HostShield:** Combine Exodus ETIP static detection with real-time network-based tracker detection (like TrackerControl). The dual approach proves actual data sharing, not just SDK presence. The ETIP database is the canonical source for Android tracker signatures.

---

## 6. Network Security Tools

### 6.1 DNS Leak Testing

**How It Works:**
1. Generate unique random subdomain queries (e.g., `<random>.test.dnsleaktest.com`).
2. Send DNS queries through the device's configured resolver.
3. The authoritative server logs which recursive resolver IP made the query.
4. Compare resolver IP against expected VPN/DoH endpoint.
5. If resolver IP belongs to the ISP instead of the VPN provider, DNS is leaking.

**On-Device Implementation:**
- Create a simple authoritative DNS server endpoint (or use existing services).
- Perform lookups from the app and check if responses come from the expected resolver.
- Alternatively, inspect DNS traffic in the VPN layer to detect queries bypassing the tunnel.

### 6.2 WebRTC Leak Detection

**How It Works:**
- WebRTC uses STUN servers to discover the device's public and local IP addresses.
- A STUN request can bypass VPN tunnels and reveal the real IP.
- Detection: use a WebView to run JavaScript that calls `RTCPeerConnection`, extract ICE candidates, compare IPs against VPN-assigned IP.

**On-Device Implementation:**
- Load a local HTML page in a WebView with JavaScript that creates an `RTCPeerConnection`.
- Parse ICE candidates for IP addresses.
- Compare discovered IPs with the VPN tunnel's assigned IP.
- Flag any IP that doesn't match the VPN as a potential leak.
- Mitigation: Android's WebView can disable WebRTC via `chrome://flags` or by intercepting STUN traffic in the VPN layer.

### 6.3 IPv6 Leak Detection

**How It Works:**
- Many VPNs only tunnel IPv4 traffic, leaving IPv6 unprotected.
- Detection: attempt IPv6 connections to known test servers; if successful while VPN is active, IPv6 is leaking.

**On-Device Implementation:**
- Attempt connections to IPv6-only endpoints (e.g., `ipv6.icanhazip.com`).
- If reachable and the returned IP is not the VPN's IPv6 address, flag as leak.
- Mitigation: block all IPv6 traffic in the VPN's TUN interface configuration (don't add IPv6 routes), or explicitly tunnel IPv6.

### 6.4 Captive Portal Detection & Handling

**Android Native Approach:**
- Android uses `NetworkMonitor` (in `com.android.server.connectivity`) to detect captive portals.
- Sends HTTP probe to `connectivitycheck.gstatic.com/generate_204` and HTTPS probe simultaneously.
- If HTTP returns 200 (not 204) or HTTPS fails, captive portal is detected.
- Android 11+ supports RFC 7710bis captive portal API.

**HostShield Implementation:**
- Hook into `ConnectivityManager.NetworkCallback` to receive `onCapabilitiesChanged` with `NET_CAPABILITY_CAPTIVE_PORTAL`.
- When captive portal detected: temporarily pause VPN/firewall rules, show notification to user, open captive portal login in Custom Tabs.
- After authentication (portal check passes), re-enable VPN/firewall.
- Use `CaptivePortal.reportCaptivePortalDismissed()` on Android 11+.

**Open Source Reference:**
- [Captive Portal Controller](https://f-droid.org/packages/io.github.muntashirakon.captiveportalcontroller/) on F-Droid.
- [IPCheck.ing](https://github.com/jason5ng32/MyIP) (9,990 stars) -- open source IP toolbox with DNS leak, WebRTC leak, and IPv6 leak testing.

---

## 7. Recommended Improvements for HostShield

### 7.1 Firewall Enhancements

| Feature | Priority | Source Inspiration | Implementation Notes |
|---------|----------|-------------------|---------------------|
| **Screen on/off rules** (fg/bg proxy) | HIGH | NetGuard | Register `ACTION_SCREEN_ON/OFF` broadcast; toggle rule sets. Avoids Accessibility Service. |
| **Metered vs unmetered rules** | HIGH | RethinkDNS | Use `ConnectivityManager.isActiveNetworkMetered()`; more correct than Wi-Fi vs mobile. |
| **Domain-per-app rules** | HIGH | RethinkDNS | Intercept DNS queries in VPN layer; match app UID + domain; allow/deny. |
| **LAN toggle per app** | MEDIUM | AFWall+ | Detect RFC1918 destinations in VPN layer; separate allow/deny from internet rules. |
| **Category-based blocking** | MEDIUM | RethinkDNS | Query `PackageManager` for Play Store category; group apps by Social/Games/Productivity. |
| **Country-based blocking** | MEDIUM | LostNet | GeoIP lookup on destination IP; allow/deny per country. |
| **Roaming rules** | LOW | AFWall+ | Detect roaming via `TelephonyManager`; apply stricter rules. |

### 7.2 Network Stats Improvements

| Feature | Priority | Implementation |
|---------|----------|---------------|
| **Historical per-app usage** | HIGH | `NetworkStatsManager.queryDetailsForUid()` with daily/weekly/monthly buckets |
| **Real-time byte counter** | HIGH | Count bytes in VPN TUN read/write loop per UID |
| **Data usage quotas + alerts** | MEDIUM | Periodic WorkManager job comparing cumulative usage to user thresholds |
| **Per-country data breakdown** | LOW | GeoIP resolve destination IPs; aggregate bytes by country per app |

### 7.3 PCAP Export Improvements

| Feature | Priority | Implementation |
|---------|----------|---------------|
| **PCAP-NG format** | HIGH | Use pcapng writer (libpcap-based or custom); include interface metadata and app UID annotations |
| **nDPI integration** | HIGH | Link nDPI C library via JNI; feed packets for protocol detection + JA3/JA4 extraction |
| **Remote streaming to Wireshark** | MEDIUM | UDP sender from VPN layer to configurable IP:port in pcap format |
| **TLS metadata display** | MEDIUM | Extract SNI + JA3/JA4 from nDPI; display in connection detail view |
| **Malware blacklist detection** | HIGH | Daily-updated IP+domain blacklists; check every connection in VPN loop |
| **TLS decryption (opt-in)** | LOW | mitmproxy integration; user must install CA cert; significant complexity |

### 7.4 GeoIP & Threat Intelligence

| Feature | Priority | Implementation |
|---------|----------|---------------|
| **GeoIP country resolution** | HIGH | Bundle GeoLite2-Country.mmdb (~6MB); `com.maxmind.geoip2:geoip2` Java library |
| **ASN lookup** | HIGH | Bundle GeoLite2-ASN.mmdb (~8MB); show ISP/org for each connection |
| **Globe visualization** | MEDIUM | WebView + Globe.GL or lightweight OpenGL; plot active connections by geo-coordinates |
| **Blocklist-based threat detection** | HIGH | abuse.ch URLhaus + Spamhaus DROP; radix trie for IP ranges; hash set for domains |
| **On-demand AbuseIPDB check** | LOW | REST API call when user taps connection detail; show abuse confidence score |
| **Weekly GeoIP database updates** | MEDIUM | WorkManager job; download from MaxMind or P3TERX mirror |

### 7.5 Privacy Scoring

| Feature | Priority | Implementation |
|---------|----------|---------------|
| **ETIP tracker detection** | HIGH | Download ETIP signatures; extract DEX class list from installed APKs; pattern match |
| **Permission risk scoring** | HIGH | Query `PackageManager.getPackageInfo()` with `GET_PERMISSIONS`; weight dangerous permissions |
| **Network-based tracker detection** | HIGH | Match VPN-observed domains against Disconnect + DuckDuckGo Tracker Radar lists |
| **Composite privacy score** | MEDIUM | 0-100 scale combining tracker count, permission severity, network behavior |
| **Privacy report per app** | MEDIUM | Detail view showing: trackers found, dangerous permissions, tracker domains contacted, score |

### 7.6 DNS & Leak Testing Tools

| Feature | Priority | Implementation |
|---------|----------|---------------|
| **DNS leak test** | HIGH | Unique subdomain query method; compare resolver IP to expected endpoint |
| **WebRTC leak test** | MEDIUM | WebView + JS `RTCPeerConnection`; compare ICE candidate IPs to VPN IP |
| **IPv6 leak test** | MEDIUM | Attempt IPv6 connections; verify IP matches VPN assignment |
| **Captive portal handling** | HIGH | `NetworkCallback` for portal detection; pause VPN; Custom Tabs for login; resume VPN |
| **DoH/DoT configuration** | MEDIUM | Similar to RethinkDNS; let users pick encrypted DNS resolver |

---

## Key Source Repositories Summary

| Project | Stars | URL | Key Feature to Study |
|---------|-------|-----|---------------------|
| RethinkDNS | 4,700 | https://github.com/celzero/rethink-app | Domain-per-app, metered rules, Go network stack |
| PCAPdroid | 3,850 | https://github.com/emanuele-f/PCAPdroid | nDPI integration, PCAP-NG, malware blacklists |
| nDPI | 4,390 | https://github.com/ntop/nDPI | JA3/JA4 fingerprinting, protocol detection |
| NetGuard | 3,530 | https://github.com/M66B/NetGuard | VPN sinkhole, native C packet loop, screen on/off |
| AFWall+ | 3,320 | https://github.com/ukanth/afwall | iptables per-app, LAN toggle, roaming rules |
| TrackerControl | 2,420 | https://github.com/TrackerControl/tracker-control-android | Dual static+network tracker detection |
| ShizuWall | 1,290 | https://github.com/AhmetCanArslan/ShizuWall | Shizuku-based (no VPN conflict) |
| IPCheck.ing (MyIP) | 9,990 | https://github.com/jason5ng32/MyIP | DNS/WebRTC/IPv6 leak testing |
| Exodus ETIP | 71 | https://github.com/Exodus-Privacy/etip | Canonical tracker signature database |
| GeoIP2-java | 856 | https://github.com/maxmind/GeoIP2-java | MaxMind MMDB reader for Android/Java |
