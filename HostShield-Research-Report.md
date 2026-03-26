# HostShield Feature & UI/UX Research Report

> Research compiled from open-source Android DNS-blocker/firewall projects: RethinkDNS, Blokada, AdGuard Home, NetGuard, InviZible Pro, personalDNSfilter, and others.
> Date: 2026-03-25

---

## Table of Contents

1. [Dashboard / Home Screen UI Patterns](#1-dashboard--home-screen-ui-patterns)
2. [Widgets and Quick Settings Tiles](#2-widgets-and-quick-settings-tiles)
3. [Onboarding Flows](#3-onboarding-flows)
4. [Automation and Scheduling](#4-automation-and-scheduling)
5. [Backup, Export, and Sync](#5-backup-export-and-sync)
6. [Logging and Diagnostics](#6-logging-and-diagnostics)
7. [Notification Systems](#7-notification-systems)
8. [Potential New Features](#8-potential-new-features)
9. [Recommended Chart Library](#9-recommended-chart-library)
10. [Priority Implementation Roadmap](#10-priority-implementation-roadmap)

---

## 1. Dashboard / Home Screen UI Patterns

### How Competing Apps Display Stats

| App | Dashboard Layout | Key Metrics Shown |
|-----|-----------------|-------------------|
| **Pi-hole** | 4 top-level stat cards + 24hr bar chart + top lists | Total queries, Queries blocked, Percent blocked, Domains on blocklists |
| **AdGuard Home** | Summary cards + time-series chart + upstream performance | DNS queries, Blocked by filters, Blocked malware/phishing, Avg response time, Upstream speed comparison |
| **RethinkDNS** | Connection-centric with per-app drill-down | Active connections, DNS queries, Blocked count, Per-app data usage, Geo IP + ASN info |
| **Blokada 5** | Minimalist shield-centric with tracker counter | Ads blocked (total counter), Activity log, Active lists |
| **NetGuard** | Per-app list with Wi-Fi/mobile toggle icons | Per-app allow/block status, Network speed graph in notification |

### Recommended Dashboard Architecture for HostShield

```
+------------------------------------------------------+
|  [Shield Animation: Protected / Unprotected]         |
|  Status: Active  |  DNS: Cloudflare DoH              |
+------------------------------------------------------+
|  +------------+  +------------+  +------------+      |
|  | Queries    |  | Blocked    |  | Block %    |      |
|  | 12,847     |  | 3,241      |  | 25.2%      |      |
|  +------------+  +------------+  +------------+      |
+------------------------------------------------------+
|  [24-hour query volume bar chart]                    |
|  ████▇▅▃▂▁▂▃▅▇████████▇▅▃▂▁▂▃▅▇████               |
|  Blocked (red) vs Allowed (green/blue)               |
+------------------------------------------------------+
|  Top Blocked Domains        Top Queried Domains      |
|  1. ads.tracking.com        1. api.example.com       |
|  2. telemetry.ms.com        2. cdn.content.com       |
|  3. pixel.facebook.com      3. auth.google.com       |
+------------------------------------------------------+
|  [Recent Activity - Real-time feed]                  |
+------------------------------------------------------+
```

### Shield / Protection Status Indicator

**Best approach**: Use **Lottie animations** for the main shield status indicator.

- **Active/Protected state**: Animated shield with subtle pulse or glow effect (green accent)
- **Disabled state**: Shield with crack animation transitioning to grey
- **Partially active**: Shield with warning icon, amber pulse
- **Processing/Starting**: Shield with rotating loading ring

Resources: LottieFiles has 13,000+ shield protection animations available in JSON format. Use `airbnb/lottie-android` for rendering. For Compose, use `lottie-compose` wrapper.

### Material 3 / Material You Dynamic Theming

Key implementation points:

- Use `dynamicColorScheme()` in Compose to derive colors from user wallpaper (Android 12+)
- Fall back to a custom HostShield brand color scheme (blue/green shield palette) on pre-Android 12
- Create semantic color tokens: `surfaceBlocked` (red tint), `surfaceAllowed` (green tint), `surfaceWarning` (amber)
- Use M3 Expressive motion: "success swells" for block confirmations, hero transitions between screens
- Dark/light theme should be automatic with M3, but provide manual override
- **Do NOT** mix Jetpack Compose `MaterialTheme` with Glance widgets -- use color resource IDs for widgets

### Real-Time Query Log Animations

Inspired by RethinkDNS's near-real-time connection tracker:

- Use `LazyColumn` with `animateItemPlacement()` for smooth item insertion
- New items slide in from top with fade-in animation
- Color-code entries: green for allowed, red for blocked, amber for cached
- Show app icon + domain + response time inline
- Tapping an entry expands to show full DNS response, TTL, upstream resolver used

---

## 2. Widgets and Quick Settings Tiles

### Jetpack Glance Widgets

**Recommended widget set** (3 widgets):

#### Widget 1: Toggle + Stats Combo (2x2)
```
+---------------------------+
|  [Shield Icon]  HostShield |
|  Protected  [ON/OFF toggle]|
|  Blocked: 3,241 today     |
|  Queries: 12,847           |
+---------------------------+
```

#### Widget 2: Stats-Only (4x1)
```
+-----------------------------------------------------+
| Queries: 12.8K | Blocked: 3.2K | 25.2% | Latency: 23ms |
+-----------------------------------------------------+
```

#### Widget 3: Mini Toggle (1x1)
```
+--------+
| [Shield|
|  ON]   |
+--------+
```

**Glance best practices** (critical):

- State management: Use `PreferencesDataStore` -- Glance does NOT redraw automatically on state changes
- Theming: Use color resource IDs for dynamic colors, NOT `MaterialTheme` wrapping
- Sizing: Implement `SizeMode.Responsive` with breakpoints for different widget placements
- User interaction: Use `actionRunCallback<ToggleProtectionAction>()` for toggle actions
- Testing: Extend `GlancePreviewActivity` in debug builds for rapid iteration
- Metrics: On Android 16+, leverage `AppWidgetEvent` API for tap/scroll tracking

### Quick Settings Tile

Implement `TileService` for VPN toggle (modeled on WireGuard's implementation):

- Extend `TileService`, require API 24+
- Set `TOGGLEABLE_TILE` metadata to `true` for accessibility
- Observe VPN tunnel state and update tile icon/label accordingly
- States: Active (green shield), Inactive (grey shield), Unavailable (crossed-out shield)
- Use `isSecure()` check before toggling -- if device locked, use `unlockAndRun()` for safety
- Show brief subtitle: "3.2K blocked" or "Protected" as secondary label
- Handle edge case: if VPN permission not yet granted, launch permission flow from tile tap

---

## 3. Onboarding Flows

### Recommended Progressive Onboarding Sequence

Based on patterns from RethinkDNS, Blokada, and security app best practices:

```
Screen 1: Welcome
  "HostShield protects your device from ads, trackers, and malware"
  [Lottie shield animation]
  [Get Started]

Screen 2: VPN Permission
  "HostShield uses a local VPN to filter DNS requests.
   No data leaves your device."
  [Visual diagram: Device -> Local VPN -> Filtered DNS]
  [Enable Protection] -> triggers VPN permission dialog
  WHY: Address user anxiety about "VPN" terminology upfront

Screen 3: Battery Optimization Exemption
  "For uninterrupted protection, exempt HostShield from battery optimization"
  [Step-by-step visual guide with device-specific screenshots]
  [Exempt Now] -> launches system battery optimization settings
  [Skip for now] -> remind later via notification
  WHY: VPN apps killed by Doze = no protection

Screen 4: Choose DNS Provider (Progressive Disclosure)
  Simple mode: "Recommended (Cloudflare DoH)" [one tap]
  Advanced mode: [expandable] Custom DoH/DoT/DNSCrypt configuration
  WHY: Don't overwhelm newcomers, empower power users

Screen 5: Blocklist Selection
  Preset profiles: "Standard", "Strict", "Family-Safe"
  Each shows what it blocks (ads, trackers, malware, adult content)
  [Customize later in Settings]

Screen 6: Done
  "You're protected! Here's what HostShield is doing:"
  [Live demo of first few blocked queries appearing]
  [Explore Dashboard]
```

### Key UX Principles from Research

1. **Short, direct messaging**: "Click here to do this" -- mobile users are impatient
2. **Progressive feature discovery**: Show tooltips for advanced features only when user first encounters them
3. **Contextual hints**: Explain WireGuard proxy, split tunneling, etc. only when user navigates to those screens
4. **Root access flow** (optional): If root detected, offer "Root mode (no VPN slot needed)" as an alternative during setup with clear tradeoff explanation
5. **Rename confusing terms**: RethinkDNS renamed "whitelisting" to "Bypass universal" for clarity -- HostShield should use plain language like "Allow" / "Block" / "Bypass"

---

## 4. Automation and Scheduling

### Tasker / Intent Integration

Based on patterns from WireGuard, OpenVPN, and Blokada:

**Exposed broadcast intents HostShield should support:**

```kotlin
// Toggle protection
"com.hostshield.action.ENABLE_PROTECTION"
"com.hostshield.action.DISABLE_PROTECTION"
"com.hostshield.action.TOGGLE_PROTECTION"

// Profile switching
"com.hostshield.action.SET_PROFILE"
  extra: "profile_name" -> String

// DNS provider switching
"com.hostshield.action.SET_DNS"
  extra: "dns_url" -> String (DoH/DoT URL or sdns:// stamp)

// Blocklist management
"com.hostshield.action.ENABLE_BLOCKLIST"
"com.hostshield.action.DISABLE_BLOCKLIST"
  extra: "blocklist_id" -> String

// Query intents (return data)
"com.hostshield.action.GET_STATUS"
  returns: "is_active" (bool), "blocked_count" (int), "query_count" (int)
```

**Implementation**: ~50 lines per intent using `BroadcastReceiver` -- WireGuard proves this is lightweight. Require a signature-level permission or user opt-in for security.

### Time-Based Blocking Schedules

Modeled on parental control patterns from AdGuard and NextDNS:

```
Schedule Types:
  1. Focus Mode     - Block social media domains during work hours
  2. Sleep Mode     - Block all except essential services at night
  3. Family Mode    - Enable safe search + adult content blocking (school hours)
  4. Custom         - User-defined time windows with specific blocklists

Implementation:
  - Use AlarmManager for exact scheduling (foreground service already running)
  - Store schedules in Room database
  - UI: Weekly calendar grid with colored time blocks
  - Each schedule links to a "Profile" (DNS + blocklist + firewall rules)
```

### Location / Wi-Fi SSID-Based Profile Switching

Inspired by Blokada 5's Networks feature (with improvements on its SSID detection issues):

```
Profiles:
  "Home"      -> Wi-Fi SSID "MyHomeWifi"  -> Relaxed blocking (Pi-hole handles rest)
  "Office"    -> Wi-Fi SSID "CorpWifi"    -> Strict blocking + no social media
  "Public"    -> Any other Wi-Fi           -> Maximum protection + force DoH
  "Mobile"    -> Cellular                  -> Standard + data-saving mode

Implementation:
  - Register NetworkCallback for connectivity changes
  - Match SSID on Wi-Fi connect events (requires ACCESS_FINE_LOCATION on Android 10+)
  - Debounce switching (300ms) to avoid rapid toggling during handoffs
  - Blokada had bugs with SSID detection -- solve by force-refreshing network info
    on connectivity change rather than caching stale values
```

### Screen On/Off Rules

Borrowed from NetGuard's proven implementation:

- "Block when screen off" per-app toggle (NetGuard's most popular feature)
- "Allow when screen on" per-app toggle
- Register `ACTION_SCREEN_ON` / `ACTION_SCREEN_OFF` broadcast receivers
- Instantly update firewall rules when screen state changes
- Use case: Allow social media only when actively using phone

---

## 5. Backup, Export, and Sync

### Config Backup/Restore

**Recommended format**: Encrypted JSON archive (`.hostshield-backup`)

```json
{
  "version": 2,
  "timestamp": "2026-03-25T10:30:00Z",
  "app_version": "1.5.0",
  "contents": {
    "dns_config": { ... },
    "blocklists": [ ... ],
    "custom_rules": [ ... ],
    "profiles": [ ... ],
    "schedules": [ ... ],
    "app_rules": [ ... ],
    "settings": { ... }
  }
}
```

**Encryption**: Use `EncryptedSharedPreferences` pattern -- AES-256-GCM with a user-provided passphrase via PBKDF2 key derivation. Alternatively, support Android Keystore-backed encryption for zero-passphrase local backups.

### Cloud Sync Options

| Method | Pros | Cons | Priority |
|--------|------|------|----------|
| **WebDAV** (Nextcloud, etc.) | Self-hosted, privacy-friendly, proven pattern (DAVx5) | Requires server setup | High |
| **Google Drive / SAF** | Zero setup for most users | Vendor lock-in, privacy concern | Medium |
| **Custom server** | Full control | Maintenance burden | Low |

**WebDAV implementation**: Use OkHttp with WebDAV extensions. Config: server URL, username, password, folder path. Support self-signed certificates (DAVx5 pattern).

### Automatic Backup with WorkManager

```kotlin
// Schedule periodic backup
val backupWork = PeriodicWorkRequestBuilder<BackupWorker>(
    repeatInterval = 24, // daily
    repeatIntervalTimeUnit = TimeUnit.HOURS
)
    .setConstraints(
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // Wi-Fi only for cloud
            .setRequiresCharging(false)
            .build()
    )
    .addTag("auto_backup")
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "hostshield_auto_backup",
    ExistingPeriodicWorkPolicy.KEEP,
    backupWork
)
```

### Sharing Configs Between Devices

- **QR code**: Encode a shareable config URL/blob as QR code (like DNS stamp `sdns://` format)
- **Share intent**: Export config file via Android share sheet
- **Deep link**: `hostshield://import?config=base64encodeddata`
- **Nearby Share**: For same-household device setup

---

## 6. Logging and Diagnostics

### Query Log UX

Modeled on RethinkDNS (best-in-class for Android DNS logging):

**Features to implement:**
- Real-time streaming feed using `LazyColumn` with `Flow<List<QueryLogEntry>>`
- Color-coded entries: Allowed (green), Blocked (red), Cached (blue), CNAME-cloaked (purple)
- Inline app icon + package name for each query
- Search bar with filters: by app, domain, time range, status (blocked/allowed)
- Tap to expand: full DNS response, TTL, resolver used, latency, DNSSEC status
- Geo IP + ASN info for resolved IPs (as RethinkDNS recently added)
- Export to CSV/JSON with date range filter
- "Block this domain" / "Allow this domain" quick actions on each entry

**Performance considerations:**
- Use Room database with pagination (`PagingSource`) for historical logs
- Keep only last N entries in-memory for real-time view (ring buffer)
- Background thread for log writes to avoid UI jank
- Configurable retention period (7/30/90 days, or unlimited)

### Real-Time Log Streaming UI

```
+------------------------------------------+
| [Search] [Filter: All v] [Pause/Resume]  |
+------------------------------------------+
| 10:42:33  Chrome    ads.google.com  BLOCKED  |  <- red
| 10:42:32  Gmail     smtp.gmail.com  ALLOWED  |  <- green
| 10:42:31  Maps      maps.google.com ALLOWED  |  <- green
| 10:42:30  Facebook  graph.fb.com    BLOCKED  |  <- red
| 10:42:28  System    connectivity..  CACHED   |  <- blue
+------------------------------------------+
```

- New entries animate in from top with slide + fade
- Auto-scroll pauses when user scrolls up (like terminal behavior)
- "Jump to latest" FAB appears when not at bottom
- Pause button freezes the stream for inspection without losing data

### Diagnostic Report Generation

Compile a shareable diagnostic report:

```
HostShield Diagnostic Report
============================
App Version: 1.5.0
Android Version: 14 (API 34)
Device: Pixel 8 Pro
VPN Status: Active
DNS Provider: Cloudflare DoH (1.1.1.1)
Uptime: 4d 12h 30m

Stats (Last 24h):
  Total Queries: 12,847
  Blocked: 3,241 (25.2%)
  Avg Latency: 23ms
  Cache Hit Rate: 42%

Active Blocklists: 5
  - AdAway (45,232 rules)
  - Steven Black Unified (87,124 rules)
  ...

Recent Errors:
  [2026-03-25 09:15] DNS timeout to 1.1.1.1 (recovered)
  ...

Battery Usage: 2.1% over 24h
Memory: 45MB RSS

[Sanitized logs attached: last 100 queries]
```

### Crash Reporting with ACRA

**ACRA** (Application Crash Reports for Android) is the recommended open-source solution:

- No proprietary dependencies, fully self-hosted
- Configurable user interaction: silent, toast, dialog, or notification
- Reports sent even if app doesn't crash (manual error reporting)
- Offline storage: reports queued and sent on next app start
- Backend options: **Acrarium** (official, active development) or custom HTTP endpoint
- Supports annotation-based configuration: `@AcraCore`, `@AcraMailSender`, `@AcraHttpSender`
- Integrates with 1.57% of all Play Store apps (13K+ apps, 5B+ downloads)

---

## 7. Notification Systems

### Block Notification Patterns

**Recommended approach: Summary notifications** (not per-query)

```
Channel: "Blocking Activity" (user-configurable frequency)

Option 1: Periodic Summary (default)
  "HostShield blocked 342 requests in the last hour"
  Expandable: top 5 blocked domains

Option 2: Per-App Alerts
  "Chrome tried to reach ads.doubleclick.net (blocked)"
  Only for user-selected apps

Option 3: Silent (log only)
  No notifications, just dashboard stats
```

### Notification Channel Architecture

```
hostshield_protection_status    (Foreground service - required, persistent)
hostshield_blocking_summary     (Periodic block summaries - default: hourly)
hostshield_new_app_alert        (New app detected accessing network)
hostshield_schedule_change      (Profile auto-switched)
hostshield_update_available     (App/blocklist updates)
hostshield_diagnostic           (Errors, warnings, connectivity issues)
hostshield_backup               (Backup completed/failed)
```

Best practices:
- Group channels using `NotificationChannelGroup` for clean Settings UI
- Default all non-essential channels to LOW importance
- Protection status channel: FOREGROUND_SERVICE importance (required by Android)
- Let users customize summary frequency: real-time / hourly / daily / off

### Persistent VPN Notification Customization

The system VPN notification cannot be fully hidden (Android enforces it), but:

- **Customize the foreground service notification**: Show useful stats (queries blocked today, current DNS latency)
- **Include quick actions**: "Pause 30min", "Switch Profile", "View Log"
- **Use a network speed graph** in the notification (NetGuard Pro pattern)
- **Allow "Silent & Minimized"** guidance: Walk users through `Settings > Apps > HostShield > Notifications > VPN Status > Silent & Minimized`
- **Style with InboxStyle or BigTextStyle** to show expandable stats

---

## 8. Potential New Features

### 8.1 Family / Parental Controls

Inspired by AdGuard DNS Family Protection and NextDNS:

- **Age-based profiles**: "Young Child" (strict), "Teen" (moderate), "Adult" (custom)
- **Safe Search enforcement**: Force safe search on Google, Bing, DuckDuckGo, YouTube, Yandex, Brave, Ecosia (AdGuard's approach -- DNS-level enforcement)
- **20+ content categories**: Adult, Gambling, Social Media, Gaming, Streaming, etc. (AdGuard Home offers 20+ categories)
- **Time-based access**: Allow social media only 4-6 PM on weekdays
- **Activity reports**: Weekly email/notification summary for parents
- **PIN/biometric lock**: Prevent children from modifying settings

### 8.2 Device Groups / Multi-Device Management

Inspired by NextDNS and PowerDNS Protect:

- Create device groups (e.g., "Kids Tablets", "Parents Phones", "IoT Devices")
- Apply different filtering profiles per group
- Central dashboard showing all devices (requires optional cloud component or LAN sync)
- For purely local: Use Android's Nearby Connections API to sync configs between household devices

### 8.3 WireGuard / Proxy Integration

Directly from RethinkDNS's proven architecture:

- **Multiple WireGuard upstreams** in split-tunnel config
- **SOCKS5 and HTTP CONNECT proxy** support
- **Per-app routing**: Route specific apps through WireGuard, others direct
- **Multi-hop**: Chain WireGuard -> Tor for high-security needs
- UI: Visual tunnel diagram showing traffic flow per app

### 8.4 Local DNS Server Mode

- Run HostShield as a DNS server on the local network (not just local VPN)
- Other devices on the same Wi-Fi can point their DNS to the phone's IP
- Useful when HostShield runs on a dedicated old phone as a "portable Pi-hole"
- Implementation: Listen on port 53 (requires root) or high port (5353) with router port forwarding
- personalDNSfilter proves this works -- it can run as DNS proxy without VPN on rooted devices

### 8.5 Split Tunneling

From RethinkDNS (most advanced implementation):

- Per-app VPN bypass (exclude banking apps, work apps)
- Per-app DNS override (use corporate DNS only for work apps)
- Per-app proxy routing (different WireGuard tunnels per app)
- Visual UI: App list with routing assignment dropdown

### 8.6 App-Specific DNS Rules

RethinkDNS's implementation to adopt:

- Allow/deny specific domains per app
- Allow/deny specific domains globally
- Bypass DNS + firewall rules entirely per app
- IP-based rules per app
- Condition-based: block when app is in background, allow in foreground

### 8.7 Encrypted DNS Stamp Support (`sdns://`)

From DNSCrypt ecosystem:

- Parse and generate `sdns://` stamps for one-click DNS server configuration
- Support stamp types: DNSCrypt, DoH, DoT, Anonymized DNSCrypt, ODoH (Oblivious DoH)
- QR code sharing of DNS stamps between devices
- Import from public resolver lists (e.g., dnscrypt.info/public-servers)
- Stamp calculator built into the app for advanced users

### 8.8 Speed Test / DNS Benchmark Integration

Open-source libraries available:

- **JSpeedTest** (`bertrandmartel/speed-test-lib`): Java/Android speed test library using Ookla servers
- **LibreSpeed**: Fully open-source speed test (F-Droid available)
- **DNS benchmark**: Measure latency to multiple resolvers and recommend the fastest
- Show comparative results: "Cloudflare: 23ms, Google: 31ms, Quad9: 45ms"
- Auto-select fastest resolver option
- Periodic background benchmarks to alert if current resolver degrades

### 8.9 Content Filtering Categories

From AdGuard Home's category system:

```
Categories (toggleable per profile):
  Ads & Trackers       [ON]  - Advertising networks, analytics
  Malware & Phishing   [ON]  - Known malicious domains
  Adult Content         [OFF] - Pornography, explicit material
  Gambling              [OFF] - Betting, casino sites
  Social Media          [OFF] - Facebook, Instagram, TikTok, etc.
  Gaming                [OFF] - Online games, game stores
  Streaming             [OFF] - Netflix, YouTube, Twitch
  Dating                [OFF] - Dating apps and sites
  Cryptocurrency        [OFF] - Mining, exchanges
  Piracy                [OFF] - Torrent sites, illegal streaming
  VPN & Proxy           [OFF] - VPN/proxy bypass services
  New Domains           [OFF] - Recently registered (< 30 days)
```

### 8.10 Additional Feature Ideas

- **CNAME cloaking detection**: personalDNSfilter has this -- resolve CNAME chains and block first-party tracking subdomains that CNAME to tracking servers
- **DNS-over-HTTP/3 (DoH3)**: Google added Android support in 2022 -- offer as an option for reduced latency
- **Oblivious DoH (ODoH)**: Route DNS through a relay for extra privacy (RethinkDNS supports this via relays)
- **Connection tracker**: Show real-time active connections per app with destination IP, port, protocol, data transferred (RethinkDNS's standout feature)
- **IP-based geolocation in logs**: Show country flags next to resolved IPs
- **Blocklist auto-update**: Schedule periodic blocklist refreshes via WorkManager

---

## 9. Recommended Chart Library

### Verdict: **Vico** (`patrykandpatrick/vico`)

| Criteria | Vico | MPAndroidChart | YCharts |
|----------|------|----------------|---------|
| Compose native | Yes (also supports Views) | No (AndroidView interop) | Yes |
| Multiplatform | Yes (KMP) | No | No |
| Animation | Built-in, differences animated by default | Manual | Limited |
| Real-time updates | `ChartEntryModelProducer` API | Manual invalidation | Manual |
| M2/M3 theming | Optional Material integrations | None | Partial |
| Dependencies | Very few | Heavy | Moderate |
| Maintenance | Active (2025-2026 releases) | Stale | Moderate |
| Chart types | Line, Column, Combined | Line, Bar, Pie, Scatter, Radar, Bubble | Line, Bar, Pie, Donut |

**Why Vico**: Native Compose support without interop hacks, real-time data update API, built-in animations, Material 3 integration, low dependency count, and active maintenance. Perfect for HostShield's dashboard charts (24-hour query volume, blocked percentage over time, latency graphs).

**Alternative for Pie/Donut charts**: Vico does not support pie charts. Use `ehsannarmani/ComposeCharts` for the blocked-categories donut chart.

---

## 10. Priority Implementation Roadmap

### Phase 1: Core Polish (High Impact, Lower Effort)

1. **Material 3 dynamic theming** -- Immediate visual modernization
2. **Lottie shield status animation** -- Distinctive brand element
3. **Quick Settings tile** -- Most-requested power user feature
4. **Notification channel architecture** -- Foundation for all notification features
5. **ACRA crash reporting** -- Essential for stability tracking

### Phase 2: Dashboard & Logging (High Impact, Medium Effort)

6. **Vico-based dashboard charts** -- 24hr query volume, block %, latency
7. **Real-time query log** with `LazyColumn` animations
8. **Query log search/filter/export**
9. **Diagnostic report generation**

### Phase 3: Automation (Medium Impact, Medium Effort)

10. **Tasker/Intent integration** -- Broadcast receivers for toggle/profile
11. **Time-based schedules** -- Focus mode, sleep mode, family mode
12. **Wi-Fi SSID-based profiles** -- Auto-switch on network change
13. **Screen on/off rules** -- Per-app blocking based on screen state

### Phase 4: Widgets & Backup (Medium Impact, Medium Effort)

14. **Glance widgets** -- Toggle + stats combo widget
15. **Config backup/restore** -- Encrypted JSON archives
16. **WorkManager auto-backup scheduling**
17. **QR code config sharing**

### Phase 5: Advanced Features (High Impact, Higher Effort)

18. **Split tunneling** -- Per-app VPN/DNS routing
19. **App-specific DNS rules** -- Per-app domain allow/deny
20. **WireGuard proxy integration**
21. **Content filtering categories**
22. **Safe Search enforcement**
23. **DNS stamp (`sdns://`) support**

### Phase 6: Ecosystem (Highest Effort)

24. **Parental controls with PIN lock**
25. **DNS benchmark / speed test**
26. **WebDAV cloud sync**
27. **Multi-device management**
28. **Local DNS server mode**
29. **CNAME cloaking detection**

---

## Sources

### Open Source Projects Studied
- [RethinkDNS (celzero/rethink-app)](https://github.com/celzero/rethink-app) -- DNS/Firewall/VPN/WireGuard, best-in-class for Android
- [Blokada](https://github.com/blokadaorg/blokada) -- Ad blocker with network profiles
- [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome) -- Network-wide DNS blocking server
- [AdGuard Home Manager (Flutter)](https://github.com/JGeek00/adguard-home-manager) -- Third-party mobile client
- [NetGuard](https://github.com/M66B/NetGuard) -- Per-app firewall, screen on/off rules
- [InviZible Pro](https://github.com/Gedsh/InviZible) -- DNSCrypt + Tor + I2P + Firewall
- [personalDNSfilter](https://github.com/IngoZenz/personaldnsfilter) -- Lightweight DNS filter, CNAME cloaking detection
- [WireGuard Android (Quick Settings Tile)](https://deepwiki.com/WireGuard/wireguard-android/7.2-quick-settings-tile)
- [ACRA](https://github.com/ACRA/acra) -- Open-source crash reporting
- [Vico](https://github.com/patrykandpatrick/vico) -- Compose chart library
- [Lottie Android](https://github.com/airbnb/lottie-android) -- Animation rendering

### Android Developer Resources
- [Jetpack Glance](https://developer.android.com/develop/ui/compose/glance) -- Widget framework
- [Quick Settings Tiles](https://developer.android.com/develop/ui/views/quicksettings-tiles) -- Tile implementation
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3)
- [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) -- Background task scheduling
- [VPN UX Improvements (AOSP)](https://source.android.com/docs/core/connect/vpn-ux)
- [Backup Security Best Practices](https://developer.android.com/privacy-and-security/risks/backup-best-practices)

### Feature References
- [DNS Stamps Specification](https://dnscrypt.info/stamps-specifications/)
- [AdGuard Parental Controls](https://adguard.com/en/blog/adguard-parental-control.html)
- [NextDNS](https://nextdns.io/) -- Multi-device, family profiles
- [PowerDNS Parental Controls](https://www.powerdns.com/parental-controls) -- Device groups
- [JSpeedTest Library](https://github.com/bertrandmartel/speed-test-lib) -- Speed test integration
- [LibreSpeed (F-Droid)](https://f-droid.org/en/packages/com.dosse.speedtest/) -- Open-source speed test
- [LottieFiles Shield Animations](https://lottiefiles.com/9943-protection-shield)
