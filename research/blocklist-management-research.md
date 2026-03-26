# Blocklist/Filter-List Management Research for HostShield

> Research date: 2026-03-25

---

## 1. Blocklist Sources and Formats

### Major Projects

| Project | GitHub Stars | Formats | Update Freq | Notes |
|---------|-------------|---------|-------------|-------|
| [StevenBlack/hosts](https://github.com/StevenBlack/hosts) | ~29.9k | hosts (`0.0.0.0`), convertible to dnsmasq/RPZ/Unbound/Privoxy | Automated, frequent | Gold standard for merged hosts files. 86k+ entries. Python-based build. Modular extensions (porn, social, gambling, fakenews). |
| [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists) | ~16k+ | domains, hosts, adblock (ABP), wildcard, dnsmasq, RPZ, unbound | Daily/frequent | Tiered approach: Light, Normal, Pro, Pro++, Ultimate. Seven output formats from same source. Wildcard format consolidates subdomains to root. |
| [oisd.nl](https://oisd.nl/) | N/A (website) | ABP, dnsmasq, domains-wildcard, RPZ, regex | Continuous | Discontinued hosts/domains-only in Jan 2024 in favor of ABP/wildcard (4x smaller, blocks more). Big/Small/NSFW variants. Aggregates 50+ upstream lists. |
| [Energized Protection](https://github.com/EnergizedProtection/block) | ~2.6k | hosts, domains, filter (ABP), DAT ruleset | Daily | Multiple packs (Spark, Blu, BluGo, Ultimate). Removes dead/inactive domains. Magisk module support. |
| [EasyList](https://github.com/easylist/easylist) | ~2k+ | adblock-syntax only | Multiple times daily | Primary source for browser ad blocking. Not DNS-native; requires conversion via justdomains or AdGuard HostlistCompiler. |
| [AdGuard DNS Filter](https://github.com/AdguardTeam/AdGuardSDNSFilter) | ~1.5k+ | adblock-syntax (||domain^) | Frequent | Composed from AdGuard Base, Social, Tracking, Mobile, EasyList, EasyPrivacy. Simplified for DNS compatibility. |

### Format Comparison

| Format | Example | Pros | Cons |
|--------|---------|------|------|
| **hosts** | `0.0.0.0 ads.example.com` | Universal compatibility, simple | No wildcards, no exceptions, large file size |
| **domains-only** | `ads.example.com` | Compact, easy to parse | No wildcards, no exceptions |
| **adblock-syntax** | `\|\|ads.example.com^` | Wildcards, exceptions (`@@`), modifiers (`$important`) | Complex parsing, not all DNS resolvers support it |
| **wildcard** | `*.ads.example.com` | Subdomain coverage from single rule | Limited tooling support |
| **regex** | `/^ads[0-9]+\.example\.com$/` | Maximum flexibility | Performance cost, hard to audit |
| **RPZ** | `ads.example.com CNAME .` | DNS-native, supports wildcards | Requires DNS server RPZ support |
| **dnsmasq** | `local=/ads.example.com/` | Native dnsmasq integration | dnsmasq-specific |

### Key Insight for HostShield
The industry is moving away from plain hosts format toward adblock-syntax and wildcard formats. OISD's 2024 deprecation of hosts format is a signal. HostShield should support adblock-syntax as a first-class citizen alongside hosts format.

---

## 2. Blocklist Parsing and Compilation

### AdGuard's urlfilter Library (Go)

**Repository**: [AdguardTeam/urlfilter](https://github.com/AdguardTeam/urlfilter) — the reference implementation for adblock-syntax DNS filtering.

**Architecture**:
```
Rule Text -> filterlist.RuleStorage -> Engine Initialization -> Index Building -> Fast Matching
```

**Key Components**:

| Component | Purpose |
|-----------|---------|
| `DNSEngine` | Primary DNS filtering — combines host rules + network rules |
| `NetworkEngine` | Fast search over network rules with internal indexes |
| `CosmeticEngine` | CSS/JS injection (browser only) |
| `filterlist.RuleStorage` | Parsed rule storage shared across engines |
| `internal/lookup` | **Index structures for fast matching** (domain-based trie) |

**Matching Pipeline**:
1. `DNSEngine.MatchRequest(req)` receives hostname + DNS type + client info
2. Tries network rules first (via `NetworkEngine` with trie-based index)
3. Falls back to host rules if no network rule match
4. Returns `DNSResult` with matched rules, rewrites, and exception status

**Rule Priority System** (highest to lowest):
1. `$dnsrewrite` rules (DNS-specific rewrites)
2. Exception rules with `$important` (`@@||example.com^$important`)
3. Blocking rules with `$important` (`||example.com^$important`)
4. Standard exception rules (`@@||example.com^`)
5. Standard blocking rules (`||example.com^`)
6. Host file rules (`0.0.0.0 example.com`)

**Supported Modifiers**:
- `$important` — elevates rule priority
- `$badfilter` — disables matching rules
- `$client` — targets specific clients by IP/name
- `$ctag` — targets client device types
- `$dnstype` — filters by DNS record type (A, AAAA, CNAME, etc.)
- `$dnsrewrite` — rewrites DNS responses (supports NOERROR, NXDOMAIN, REFUSED + all record types)
- `$denyallow` — blocks everything except specified domains

### AdGuard HostlistCompiler (Node.js)

**Repository**: [AdguardTeam/HostlistCompiler](https://github.com/AdguardTeam/HostlistCompiler)

**Purpose**: Compiles multiple source lists into a single optimized blocklist.

**Key Transformations**:
- Converts `/etc/hosts` syntax to adblock syntax
- Removes incompatible/dangerous rules
- Deduplicates entries
- Strips domain-specific rules (useless for DNS)
- Removes rules with unsupported modifiers
- Configurable via JSON config file

**Config Example**:
```json
{
  "name": "My DNS Filter",
  "sources": [
    { "source": "https://example.com/list.txt", "type": "adblock" },
    { "source": "https://example.com/hosts.txt", "type": "hosts" }
  ],
  "transformations": ["RemoveComments", "Compress", "Deduplicate", "Validate"],
  "exclusions": ["excluded-domain.com"]
}
```

### RethinkDNS Compressed Radix Trie

**Repository**: [serverless-dns/blocklists](https://github.com/serverless-dns/blocklists) (115 stars)

**Approach**: Compiles 194 blocklists (~13.5M domains) into a compressed, succinct radix-trie.

**Build Pipeline**:
```
1. python3 download.py          # Parse config.json, fetch all lists
2. node ./src/build.js          # Build compressed radix-trie (needs 16GB heap)
3. node ./src/upload.js          # Upload to S3/Cloudflare R2
```

**Input Formats**: domains, hosts, ABP, wildcards

**Config Model**:
```json
{
  "listname": {
    "url": "https://...",
    "format": "domains|hosts|abp|wildcard",
    "severity": 0,  // 0=lite, 1=aggressive, 2=extreme
    "tags": ["spam", "malware", "privacy"]
  }
}
```

**Key Innovation**: The compressed trie is deployed to 300+ Cloudflare Workers locations worldwide, enabling serverless DNS filtering with sub-millisecond lookups across millions of domains.

### Data Structure Recommendations for HostShield

| Structure | Use Case | Tradeoff |
|-----------|----------|----------|
| **Hash Map** | Exact domain lookup | O(1) lookup, no wildcard support, high memory |
| **Trie / Radix Trie** | Domain hierarchy, wildcard matching | O(k) lookup (k=domain length), excellent for `*.example.com` |
| **Compressed Radix Trie** | Large-scale deployment (millions of domains) | Complex to build, minimal memory, fast lookup |
| **Sorted Array + Binary Search** | Simple domains-only lists | Low memory, O(log n), easy to diff |
| **Bloom Filter** | Pre-filter before exact check | Probabilistic, fast negative answers |

**Recommended Approach**: Use a **hash set for exact matches** (covers 90%+ of lookups) combined with a **reversed-label trie for wildcard/subdomain matching**. This is essentially what AdGuard Home does internally.

---

## 3. Blocklist Gallery / Curation

### FilterLists.com

**Repository**: [collinbarrett/FilterLists](https://github.com/collinbarrett/FilterLists) (1.6k stars)

**Architecture**: .NET Aspire stack — React/TypeScript frontend (Ant Design) + ASP.NET Core API + SQL Server

**Data Model**:
- Lists stored as JSON in `services/Directory/data/`
- Each list has: name, description, URL, homepage, maintainer contact
- Categories: ads, trackers, malware, social, annoyances, etc.
- No automated health scoring — purely community-curated
- Contributors submit PRs or open issues
- Automated "Migrate bot" handles EF Core migrations

**Limitation**: No quality metrics, no overlap analysis, no update-frequency tracking.

### AdGuard HostlistsRegistry

**Repository**: [AdguardTeam/HostlistsRegistry](https://github.com/AdguardTeam/HostlistsRegistry) (357 stars, 13.6k commits)

**Metadata Schema** (`metadata.json` per filter):
```json
{
  "filterKey": "unique-string-id",
  "filterId": 123,
  "name": "Filter Name",
  "description": "What it blocks",
  "homepage": "https://...",
  "timeAdded": 1640000000000,
  "expires": "4 days",
  "displayNumber": 1,
  "environment": "prod",
  "disabled": false,
  "trusted": true,
  "tags": [
    { "tagId": 1, "keyword": "purpose:ads" },
    { "tagId": 10, "keyword": "recommended" }
  ]
}
```

**Tag Taxonomy**:
- `purpose:ads` / `purpose:privacy` / `purpose:malware` / `purpose:social` / `purpose:parental`
- `lang:en` / `lang:ru` / `lang:zh` / etc.
- `recommended` — low-risk, vetted lists
- `obsolete` — abandoned, excluded from distribution

**Acceptance Criteria** (very strict):
- GitHub repos need **50+ stars**
- Non-GitHub lists need **10+ monthly issues/discussions**
- Must be actively maintained for **6+ months**
- Minimum **10 updates per month**
- DNS-oriented adblock syntax preferred over hosts format
- Removed after **1 year without support**

**CI/CD**: `yarn compose` validates and compiles filters, auto-publishes to GitHub Pages as `filters.json` and `services.json`.

### Sefinek Blocklist Generator

**Repository**: [sefinek/Sefinek-Blocklist-Collection](https://github.com/sefinek/Sefinek-Blocklist-Collection)

**Unique Feature**: Web-based **Blocklist Generator** at sefinek.net/blocklist-generator where users select categories and get custom URLs. Output formats: `0.0.0.0`, `127.0.0.1`, no-IP, AdGuard, dnsmasq, Unbound, RPZ.

**Categories**: 100+ source lists, 6M+ domains. Updated every 3 hours via GitHub Actions.

### Gallery Recommendations for HostShield

1. **Adopt AdGuard's metadata schema** — `filterKey`, tags with `purpose:*` taxonomy, `recommended` flag, `trusted` flag
2. **Add health metrics** FilterLists lacks:
   - Last-updated timestamp and staleness indicator
   - Update frequency (commits/month or HTTP Last-Modified tracking)
   - Rule count and delta since last check
   - False positive reports / community score
3. **Category-based browsing** with preset bundles (e.g., "Privacy Essential", "Family Safe", "Maximum Protection")
4. **Sefinek-style generator** — let users pick categories and auto-generate a composite subscription URL

---

## 4. Overlap Analysis

### Current State of the Art

**Problem**: Users subscribe to multiple lists without knowing how much each contributes. NextDNS users report wanting to know: "If 800/1000 blocked queries are caught by all my lists, I only need the list that catches the other 200."

### DNS Toolkit

**Repository**: [phani-kb/dns-toolkit](https://github.com/phani-kb/dns-toolkit) (3 stars — early stage but architecturally sound)

**Overlap Metrics Per Source**:
- **C** (Count): Total entries in list
- **U** (Unique): Entries not found in any other subscribed list
- **X** (Conflicts): Entries appearing in both allow and block lists

**Conflict Detection**: Daily-generated report showing entries found in both allowlists and blocklists with source attribution.

**Pipeline**: downloaders -> processors -> consolidation (grouping, categorization, overlap detection) -> output generation

### Techniques for Overlap Analysis

```
Algorithm: Pairwise Overlap Matrix

For each pair of lists (A, B):
  overlap(A,B) = |A ∩ B| / min(|A|, |B|)    // Jaccard-like metric
  unique(A) = |A - (B ∪ C ∪ D ∪ ...)|        // Domains only in A
  coverage(A) = |A ∩ blocked_queries| / |blocked_queries|  // Actual hit rate

Output:
  - Overlap heatmap matrix
  - Unique contribution percentage per list
  - Redundancy score (lists that add <1% unique domains)
  - Suggested removals to minimize subscriptions while maintaining coverage
```

### Practical Implementation Approaches

1. **Static Analysis** (no query data needed):
   - Download all subscribed lists
   - Build domain sets, compute pairwise intersections
   - Generate overlap matrix and unique-contribution percentages
   - Flag lists where >95% of domains are covered by other subscriptions

2. **Query-Log-Based Analysis** (most accurate):
   - Track which list(s) matched each blocked query
   - Compute actual hit-rate contribution per list
   - Show "If you removed List X, these N queries would go unblocked"

3. **Visualization**:
   - Venn-diagram style for 2-3 lists
   - Heatmap matrix for many lists
   - Bar chart showing unique contribution %

### Recommendations for HostShield

- **Static overlap analysis** as a first-class feature: when users add/manage lists, show overlap % with existing subscriptions
- **"Optimize my lists"** button that suggests removing redundant subscriptions
- **Per-list health card**: total rules, unique rules, overlap %, last updated, staleness warning
- **Query-log attribution**: tag each blocked query with the list(s) that matched it

---

## 5. Custom Rule Editors

### AdGuard Home

**Architecture**:
- Custom rules stored in `Config.UserRules` (string array in `config.yaml`)
- **Highest priority** — evaluated before all filter lists
- API: `POST /control/filtering/set_rules` (full replacement, not append)
- Test API: `GET /control/filtering/check_host?name=example.com` — returns whether a domain would be filtered and by which rule

**Rule Types Supported**:
- `||example.com^` — block domain + subdomains
- `@@||example.com^` — allow exception
- `||example.com^$important` — force block even if excepted
- `||example.com^$dnsrewrite=1.2.3.4` — rewrite DNS response
- `/regex/` — regex-based rules
- `0.0.0.0 example.com` — hosts-style rules

**UI Features**:
- Inline rule editor with syntax highlighting
- Real-time validation
- Rule count display
- "Check host" testing tool

### Pi-hole

**Architecture**:
- Separate lists for: blocklist, blocklist-wildcard (regex), whitelist, whitelist-wildcard
- Gravity database (`gravity.db`) stores all rules
- Import: via URL subscription or bulk paste/file upload
- Export: Teleporter backup (tar.gz of entire config)
- Third-party tool: [pihole5-list-tool](https://github.com/jessedp/pihole5-list-tool) for bulk operations

**Limitations**:
- No adblock-syntax support (hosts and regex only)
- No rule priority/`$important` modifier
- Import/export of individual allow/block lists requested but not fully implemented
- No built-in rule testing UI (must check query log after the fact)

### RethinkDNS

**Architecture**:
- Web-based configuration at rethinkdns.com/configure
- Blocklists selectable with severity levels (lite/aggressive/extreme)
- Tags: spam, malware, privacy, etc.
- All config encoded in the DNS endpoint URL itself (stateless)

### Recommendations for HostShield's Rule Editor

1. **Syntax support**: Full adblock-syntax with `||`, `@@`, `$important`, `$dnsrewrite`, regex
2. **Real-time validation**: Parse rules as user types, show warnings for invalid syntax
3. **Rule testing**: "Test domain" input that shows which rules match and in what priority order
4. **Import/export**:
   - Import from: hosts file, adblock-syntax file, Pi-hole Teleporter backup, AdGuard Home config
   - Export to: hosts, adblock-syntax, domains-only
5. **Rule conflict detection**: Warn when a new block rule conflicts with an existing allow rule (or vice versa)
6. **Drag-and-drop priority**: Visual rule ordering with clear priority indicators
7. **Bulk operations**: Multi-select, bulk enable/disable, bulk delete

---

## 6. Hosts File Diffing

### Current Landscape

There is **no widely-adopted dedicated tool** for blocklist diffing. Most projects rely on:

- **Git history**: StevenBlack/hosts and hagezi track changes via git commits
- **Git diff**: Standard `git diff` on the raw list files
- **Manual log inspection**: Users compare query logs before/after updates

### Approaches Worth Implementing

1. **Set-Based Diff**:
   ```
   added   = new_version - old_version
   removed = old_version - new_version
   unchanged = old_version ∩ new_version

   Output:
     + 142 domains added
     - 87 domains removed
     = 85,834 domains unchanged
   ```

2. **Categorized Diff** (enhanced):
   - Group added/removed domains by category (if list metadata available)
   - Flag notable additions (e.g., major services newly blocked)
   - Show source attribution for merged lists (which upstream list caused the change)

3. **Update Impact Preview**:
   - Before applying an update, show:
     - New domains that would be blocked (cross-reference with user's query history)
     - Domains that would be unblocked
     - Rules that changed priority or modifiers

4. **Changelog Generation**:
   - Auto-generate human-readable changelog for each list update
   - Include: date, version, domains added/removed count, notable changes
   - Store history for rollback capability

### Implementation Approach for HostShield

```
When a list update is fetched:
  1. Parse old and new versions into domain sets
  2. Compute added/removed/unchanged
  3. Store diff metadata (timestamp, counts, sample domains)
  4. Show notification: "List X updated: +142 / -87 domains"
  5. Allow user to inspect full diff
  6. Allow rollback to previous version
```

**Storage**: Keep last N versions of each list (or just the diffs) to support rollback and history viewing.

---

## Summary: Top Improvements for HostShield

### High Priority

| Feature | Inspiration | Effort |
|---------|-------------|--------|
| **Adblock-syntax parsing** | AdGuard urlfilter engine | High — but essential for modern lists |
| **Multi-format support** (hosts, domains, ABP, wildcard, regex) | hagezi's 7-format output | Medium |
| **Blocklist gallery with metadata** | AdGuard HostlistsRegistry schema | Medium |
| **Static overlap analysis** | DNS Toolkit's C/U/X metrics | Medium |
| **List update diffing** | Gap in ecosystem — unique opportunity | Low-Medium |
| **Rule testing tool** | AdGuard Home's `check_host` API | Low |

### Medium Priority

| Feature | Inspiration | Effort |
|---------|-------------|--------|
| **Category-based list bundles** | Sefinek generator, RethinkDNS severity tiers | Low |
| **Health scoring per list** | Missing from FilterLists — opportunity | Medium |
| **Query-log attribution** | NextDNS feature request | Medium |
| **Import from Pi-hole/AdGuard** | pihole5-list-tool, Teleporter | Low-Medium |
| **Rule conflict detection** | DNS Toolkit conflict reports | Low |

### Lower Priority (Differentiators)

| Feature | Inspiration | Effort |
|---------|-------------|--------|
| **"Optimize my lists"** button | NextDNS user requests | Medium |
| **Compressed trie for large-scale** | RethinkDNS radix trie | High |
| **List version rollback** | No existing tool does this | Medium |
| **Community quality scores** | Missing everywhere | High |
| **Custom blocklist generator URL** | Sefinek generator | Medium |

---

## Sources

- [StevenBlack/hosts](https://github.com/StevenBlack/hosts)
- [hagezi/dns-blocklists](https://github.com/hagezi/dns-blocklists)
- [hagezi FAQ](https://github.com/hagezi/dns-blocklists/wiki/FAQ)
- [oisd.nl](https://oisd.nl/)
- [oisd included lists](https://oisd.nl/includedlists)
- [Energized Protection](https://github.com/EnergizedProtection/block)
- [AdGuard HostlistCompiler](https://github.com/AdguardTeam/HostlistCompiler)
- [AdGuard urlfilter](https://github.com/AdguardTeam/urlfilter)
- [AdGuard urlfilter Go docs](https://pkg.go.dev/github.com/AdguardTeam/urlfilter)
- [AdGuard DNS Filtering Syntax](https://adguard-dns.io/kb/general/dns-filtering-syntax/)
- [AdGuard Home Filter Lists (DeepWiki)](https://deepwiki.com/AdguardTeam/AdGuardHome/4.2-filter-lists-and-custom-rules)
- [AdGuard HostlistsRegistry](https://github.com/AdguardTeam/HostlistsRegistry)
- [FilterLists.com](https://filterlists.com/)
- [FilterLists GitHub](https://github.com/collinbarrett/FilterLists)
- [serverless-dns/blocklists (RethinkDNS)](https://github.com/serverless-dns/blocklists)
- [phani-kb/dns-toolkit](https://github.com/phani-kb/dns-toolkit)
- [justdomains/blocklists](https://github.com/justdomains/blocklists)
- [Sefinek Blocklist Collection](https://github.com/sefinek/Sefinek-Blocklist-Collection)
- [Sefinek Blocklist Generator](https://sefinek.net/blocklist-generator)
- [Pi-hole](https://github.com/pi-hole/pi-hole)
- [pihole5-list-tool](https://github.com/jessedp/pihole5-list-tool)
- [NextDNS blocklist performance discussion](https://help.nextdns.io/t/p8h16v3/more-detail-about-blocklist-performance)
- [AdGuard Home](https://github.com/AdguardTeam/AdGuardHome)
- [EasyList](https://github.com/easylist/easylist)
- [AdGuard DNS Filter](https://github.com/AdguardTeam/AdGuardSDNSFilter)
