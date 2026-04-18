# NovaCut — Cross-Project Feature Port Roadmap

Features identified for port **from sibling projects in `Z:\repos\`** into NovaCut. Separate from the main [ROADMAP.md](ROADMAP.md) (which covers ML dependency integration tiers). This doc tracks the cross-pollination initiative: taking proven patterns from the 190+ sibling projects and adapting them for an Android video editor.

**Current version:** v3.49.0 (April 2026)

**Executive summary:** Four waves of exploration across ~30 sibling projects have yielded **10 shipped features** (spread across v3.46/47/48), **~35 backlog candidates** spanning Tier 1 quick wins to Tier 3 strategic bets, and a running list of un-surveyed targets for Wave 5. Each roadmap entry names its source project so the team can go back and inspect the original implementation pattern. Active waves of work:

- **Waves 1–2 (shipped & planned):** export controls (target-size, ETA, filename tokens), text templates, scratchpad notes, recovery UI, preset grouping.
- **Wave 3:** frame-preview grid, export pre-flight report, usage analytics, EXIF/GPS ingest, branching undo tree, markdown reports, geo-tag map, preset marketplace, AI edit coach.
- **Wave 4:** contact-sheet export, clip filter chips, multi-pane preview grid, encrypted project archive, output profile pipelines.

**Conventions:**
- Effort tiers: **S** = 1–3 hours, **M** = half/full day, **L** = multi-session feature
- Integration point = file/class where the feature lands
- Source project links back to `Z:\repos\<name>`

---

## 0. Shipped

### v3.49.0 (current)
| # | Feature | Source | Integration Point |
|---|---------|--------|-------------------|
| ✅ | Contact-sheet export — single PNG with one thumbnail per clip, configurable 2–6 column grid, midpoint-frame capture, `M:SS` duration labels | FrameSnap | `ContactSheetExporter.kt`, `ExportConfig.exportAsContactSheet`, `ExportDelegate` contact-sheet branch, `ExportSheet` Flamingo-accent toggle |

### v3.48.0
| # | Feature | Source | Integration Point |
|---|---------|--------|-------------------|
| ✅ | Speed curve presets grouped (Ramps / Constants) with section labels | ClipForge | `SpeedCurveEditor.kt` — two labeled `FlowRow`s |
| ✅ | Keyframe presets grouped (Cinematic / Fades / Emphasis) in dropdown with `HorizontalDivider` + `labelSmall` subheaders | ClipForge | `KeyframeCurveEditor.kt` — `applyPreset` lambda + three divided groups |
| ✅ | Scratchpad notes sidecar export — `<basename>.notes.txt` written next to rendered video on export success | KeepSyncNotes | `ExportDelegate.startExport` — `onComplete` IO block, non-fatal on failure |

### v3.47.0
| # | Feature | Source | Integration Point |
|---|---------|--------|-------------------|
| ✅ | Per-project scratchpad notes — 750ms-debounced auto-save, overflow menu entry | KeepSyncNotes | `Project.notes`, `ScratchpadSheet.kt`, `PanelId.SCRATCHPAD`, Room `MIGRATION_5_6` |
| ✅ | Visible crash-recovery dialog — AlertDialog on editor open with Keep / Discard buttons | FaceSlim / GitForge | `PanelId.RECOVERY_DIALOG`, `EditorViewModel.dismissRecoveryDialog()`, `EditorScreen` recovery AlertDialog |

### v3.46.0
| # | Feature | Source | Integration Point |
|---|---------|--------|-------------------|
| ✅ | Target file size export presets (Discord 8/25/100 MB, Gmail 25 MB, Telegram 50 MB, WhatsApp 16 MB, Twitter 512 MB) — auto-derive bitrate from duration, resolved at dispatch | VideoCrush | `ExportConfig.resolveTargetSize()`, `TargetSizePreset` enum, `ExportSheet` Target File Size card |
| ✅ | Filename templates with tokens (`{name}` `{date}` `{time}` `{res}` `{codec}` `{fps}` `{preset}`) + 5 preset patterns | AlphaCut / FrameSnap / EXTRACTORX | `ExportDelegate.applyFilenameTemplate()`, `ExportSheet` Filename Template card |
| ✅ | Pre-flight ETA estimation ("Est. time: 2m 34s") scaled by resolution pixels × codec factor × fps | VideoCrush / AlphaCut | `ExportSheet.estimateExportEtaSeconds()`, `formatEtaSeconds()` |
| ✅ | 6 meme/viral text templates — Impact Meme, TikTok Caption, Reels Hook, POV Meme, Neon Glow, Word Burst | GifText | `builtInTextTemplates` in `TextTemplateGallery.kt` (SOCIAL category) |
| ✅ | Frame screenshot export (pre-existing, noted during audit) | FrameSnap | `ExportConfig.captureFrameOnly`, `EditorViewModel` capture branch |

---

## 1. Tier 1 — Quick Wins (S effort)

Short, self-contained, no new dependencies. Targets for **v3.47.0 – v3.48.0**.

### 1.1 Output path macros (extended)
**Source:** EXTRACTORX
**Why:** Current filename template uses token replacement. Extend tokens to include `{projectFolder}`, `{sourceName}` (first clip's source filename), `{duration}`, `{sizeMB}` for post-render macro-substitution.
**Integration:** Extend `ExportDelegate.applyFilenameTemplate()` — add post-export rename step for tokens that need the final file size.
**Effort:** S

### 1.2 Auto-categorization tags on clips
**Source:** FileOrganizer
**Why:** Tiered classifier (extension → keyword → fuzzy-match) inspires auto-tagging clips by filename keywords ("tutorial", "gaming", "interview"). Adds a chip row on timeline clips + filter on ProjectListScreen.
**Integration:** `Clip.autoTags: List<String>` field + keyword rule table in new `ClipAutoTagger.kt`. Runs on clip-add in `ClipEditingDelegate`.
**Effort:** S

### 1.3 Interpolation preset discoverability pass — ✅ shipped in v3.48.0
**Source:** ClipForge
**Shipped:** SpeedCurveEditor split into Ramps (Ramp Up/Down, Pulse) + Constants (Slow Mo, 2×, 4×). KeyframeCurveEditor dropdown divided into Cinematic / Fades / Emphasis with subheaders and dividers. No behavior change — pure discoverability polish.

### 1.4 Multi-volume media sequence detection
**Source:** EXTRACTORX (split-archive grouping)
**Why:** Users with GoPro/drone footage get `.MP4` chunks named `GH010100.MP4`, `GH020100.MP4`. Auto-detect sequence → offer one-click merged-import (concatenates into a single timeline clip).
**Integration:** `MediaPicker` post-selection pass — regex-match common camera patterns → prompt "Merge 8 related clips?" → add as concatenated sequence via existing merge logic.
**Effort:** S

### 1.5 Timeline activity anomaly watcher
**Source:** HostShield (rolling-baseline anomaly detection)
**Why:** Detect bulk-deletes (>10 clips in <5 min), mass-effect-removal, accidental project wipes → show "Undo bulk change?" snackbar with one-tap revert. Uses existing undo stack.
**Integration:** New `EditorActivityMonitor.kt` — track operation counts in rolling window inside `EditorViewModel`. Snackbar on threshold exceeded.
**Effort:** S

### 1.6 Crash-log visible recovery UI — ✅ shipped in v3.47.0
**Source:** FaceSlim / GitForge
**Shipped:** Added `PanelId.RECOVERY_DIALOG` + AlertDialog in `EditorScreen`. Opens on project load when `autoSave.loadRecoveryData()` returned non-empty content. Buttons: Keep recovered / Discard.
**Integration:** `EditorViewModel.dismissRecoveryDialog(recover)` — discard path calls `autoSave.clearRecoveryData(projectId)`.

---

## 2. Tier 2 — Medium Wins (M effort)

New engines, new panels, or non-trivial state-model changes. Targets for **v3.49 – v3.52**.

### 2.1 Unified preset library (effect chains + color grades + export configs)
**Source:** Claude-Ultimate-Enhancer / FaceSlim / ImageForge
**Why:** Currently NovaCut has scattered preset systems — text templates, speed presets, keyframe presets, LUT presets, platform export presets. Unify under `PresetLibrarySheet` with categories, search, favorites, and user-saved presets (JSON in `filesDir/presets/`).
**Integration:** New `PresetManager` (singleton) + `PresetLibrarySheet` panel. Extends existing `TemplateManager` to cover `EffectChain`, `ColorGrade`, `ExportConfig`, `AudioMixerProfile`.
**Effort:** M

### 2.2 Watermark / branding overlay with presets
**Source:** ImageForge + WallBrand
**Why:** Content creators need branded watermarks (logo + handle + position presets — corner, animated, tiled). Burn-in at export. Saves watermark presets per-project or globally.
**Integration:** New `WatermarkOverlay` model (image + text + opacity + position + animation) → `WatermarkEffect` mapped in `EffectBuilder.buildVideoEffect()` as OverlayEffect during export. New `WatermarkPanel` Composable.
**Effort:** M

### 2.3 Duplicate-clip detector with perceptual hashing
**Source:** DuplicateFF / FileOrganizer
**Why:** 5-stage pipeline (size → prefix SHA256 → suffix → full hash → pHash diff) finds identical and near-duplicate clips across a project. Show "3 similar clips" badge in gallery; let user batch-remove or merge-aware edit.
**Integration:** New `ClipDedupeEngine.kt` — runs in background `WorkManager` task. pHash stored in Room as `Clip.pHash: Long?`. Surfaced via `DedupePanel` or warning banner.
**Effort:** M

### 2.4 Live in-canvas text editing (double-tap to edit)
**Source:** PDFedit
**Why:** Current flow requires opening `TextEditorSheet`. Double-tap on a text overlay in the preview → inline-editable text box with keyboard + drag-corner resize for stickers. Matches PowerDirector and CapCut UX.
**Integration:** `PreviewPanel` gains a gesture layer for overlays; new `InlineTextEditOverlay` Composable reuses `TextOverlay` state. Undo-aware via existing `saveUndoState("Edit text")`.
**Effort:** M

### 2.5 Watermark / subtitle removal via region inpainting
**Source:** GeminiWatermarkRemover / VideoSubtitleRemover
**Why:** NovaCut has `InpaintingEngine` — extend with a "draw mask → inpaint across frames" AI tool. Three-stage NCC detection (GWR) + reverse alpha blending `(I - α·255)/(1-α)` for static watermarks; per-frame inpaint for moving subtitles.
**Integration:** New `aiRemoveWatermark(maskRect)` in `AiToolsDelegate` → reuses existing `InpaintingEngine.inpaintFrame()`. UI: new AI tool card + mask-draw overlay.
**Effort:** M

### 2.6 Lossless stream-copy trim path
**Source:** ClipForge
**Why:** When a clip has **no** effects, transforms, keyframes, or color grading and is only trim+concat, use `MediaMuxer` stream-copy instead of full Media3 re-encode. Result: 50–100× faster exports for trim-only edits.
**Integration:** New `StreamCopyExporter.kt` selected in `VideoEngine.export()` when `tracks.all { it.clips.all { clip -> clip.isStreamCopyEligible() } }`.
**Effort:** M (tricky edge cases: timebase, keyframe boundary snapping)

### 2.7 Auto-sequence detection enhanced with drag-drop merge
**Source:** Multistreamer (grid/synced layouts) + MediaDL (codec detection)
**Why:** On media-picker drag of multiple clips, detect if they share source session (same camera, close timestamps, consecutive filenames) → one-tap merge as single timeline clip.
**Integration:** `MediaPickerSheet` post-pick analyzer using `MediaMetadataRetriever` for creation-time + matching codec/resolution. Dialog: "Merge these 5 clips?"
**Effort:** M

### 2.8 Frame-skip mask reuse for AI tools
**Source:** AlphaCut
**Why:** Segmentation, motion-tracking, stabilization run per-frame; processing every frame is 80% of their cost. Reuse mask across N frames with temporal smoothing (deque + gaussian blending) — 3–5× speedup.
**Integration:** `SegmentationEngine` + `StabilizationEngine` + motion tracker in `AiFeatures.kt` gain `frameSkip: Int` + `temporalSmoothFrames: Int` params. `SegmentationGlEffect` buffers last-N masks for interpolation.
**Effort:** M

### 2.9 Theme variant presets
**Source:** DarkReaderLocal / stylebot
**Why:** Add a few Catppuccin variants (Latte light, Frappe, Macchiato, Mocha — current) + optional "AMOLED black" and a per-project theme override. Stored in `SettingsRepository`.
**Integration:** Extend `ui/theme/Theme.kt` with `ThemeVariant` enum + `LocalThemeVariant` CompositionLocal. New Settings toggle.
**Effort:** M (every Mocha color reference becomes a token lookup)

### 2.10 VFX particle overlays (fireflies, sparkles, embers)
**Source:** Aura (`VfxParticleRenderer`)
**Why:** Decorative 30fps Canvas particle effects with phase animation. Export-only (not real-time preview). Compliments existing effect system.
**Integration:** New `ParticleEffect` entries in `EffectType` + `ParticleGlEffect` for GL path. UI: new section in `EffectLibraryPanel`.
**Effort:** M

### 2.11 Social-media platform codec fallbacks
**Source:** MediaForge / VideoCrush
**Why:** Probe device encoder capability at startup; gracefully degrade HEVC → H.264 on devices without hardware HEVC encoders. Log choice to user ("Exporting in H.264 — HEVC not supported on this device").
**Integration:** `VideoEngine.probeEncoders()` at init; `ExportConfig.getAvailableCodecs()` already does the first half — extend with auto-degrade fallback in `startExport`.
**Effort:** M

---

## 3. Tier 3 — Strategic Bets (L effort)

Multi-session features or new core systems. Targets for **v4.x**.

### 3.1 Scheduled / deferred batch export
**Source:** AlarmClockXtreme
**Why:** Queue batch exports for off-peak hours ("Export at 2 AM"). Exact AlarmManager for deadlines + WorkManager for flexible renders. Important for long 4K/AV1 renders that drain battery.
**Integration:** New `ScheduledExportScheduler` + `ScheduledExportWorker` (extends existing `ProxyGenerationWorker` pattern). Permission handling (`SCHEDULE_EXACT_ALARM` API 31+). UI: schedule picker in `BatchExportPanel`.
**Effort:** L

### 3.2 Real-time multi-device collaborative editing
**Source:** Multistreamer (sync state across viewers)
**Why:** Sync timeline zoom, playhead, active clip across phone + tablet for review sessions. Not full collab-edit — review-mode sync first.
**Integration:** New `CollaborativeSession` module with WebSocket/Gun.js; `EditorViewModel` publishes state deltas; read-only peer view.
**Effort:** L

### 3.3 Hierarchical tag + search for clip library
**Source:** Bookmark-Organizer-Pro
**Why:** At 100+ clips per project, flat gallery breaks down. Nested tags (Events > Wedding > Ceremony), boolean search (wedding AND ceremony NOT rehearsal), saved smart filters.
**Integration:** New `ClipTag` model + Room table. `TagPickerSheet` + `SmartFilterEditor`. Extend `ProjectListViewModel` search to boolean-grammar.
**Effort:** L

### 3.4 Unified format ingest (HEIC/HEIF/AVIF/WebP/JXL)
**Source:** HEICShift
**Why:** Modern cameras and iPhones export HEIC/HEIF; web assets are often AVIF/WebP. Auto-convert on import to a NovaCut-friendly format, preserving EXIF/ICC/XMP.
**Integration:** `MediaPicker` post-selection convert via new `UniversalFormatBridge`. Uses existing `FFmpegEngine` for unsupported formats.
**Effort:** L (requires FFmpeg decoder fleshout)

### 3.5 Advanced anomaly & health heuristics
**Source:** HostShield (baseline + rolling)
**Why:** Go beyond bulk-delete detection — detect render-blocking effect stacks (20+ effects on one clip), OOM-risky project sizes (500+ clips, 10GB+ media), fragmented timelines — and surface "Project health" indicator in gallery.
**Integration:** New `ProjectHealthAnalyzer` that runs on auto-save. Emits `HealthReport` (surfaced as gallery card badge).
**Effort:** L

### 3.6 Per-frame GIF timing control with keyframe animator
**Source:** GifStudio / GifText
**Why:** Current GIF export is uniform 15/20fps. Enable per-frame delays (frames 1–5 at 100ms, 6–10 at 50ms) for dramatic-pause meme timing. Built on existing keyframe engine.
**Integration:** Extend GIF export path in `ExportDelegate` to consume `SpeedCurve` / per-frame delay list; new `GifTimingEditor` panel.
**Effort:** L

---

## 4. Deferred / Evaluated but Skipped

| Feature | Source | Reason |
|---------|--------|--------|
| Icon pack discovery | Lawnchair-Lite | Not applicable unless NovaCut adds extensible effect/font packs. Re-evaluate in v5. |
| OS-level context menu ("Edit in NovaCut") | EXTRACTORX | Android intent system already covers this via `ACTION_EDIT`; no registry hook needed. |
| Shadow DOM isolation | DarkReaderLocal / stylebot | Web-only concept. N/A for Android native. |
| Timezone-aware timecode utilities | TimeZoneShift | Timezone ≠ timecode. NovaCut timebase is project-local ms. |
| Live wallpaper patterns | Aura | Non-applicable to in-editor UI; video editor is always foreground. |
| Lip reading / visual speech recognition | LipSight | Too niche; Whisper STT already covers the 99% use case. |
| Video compression CRF tuning | VideoCrush | Media3 Transformer doesn't expose CRF directly; bitrate-based targeting (1.1 `resolveTargetSize`) covers the same user need. |
| Gun.js / WebRTC collab | Multistreamer | Subsumed by 3.2. |

---

## 5. Sourcing Summary

Full cross-project source map across all four waves. Entries with ✅ indicate at least one feature has shipped.

| Source project | Features identified | Status |
|----------------|---------------------|--------|
| VideoCrush ✅ | Target-size encoding, ETA estimation, codec fallbacks | 2 shipped (3.46), 1 pending (2.11) |
| AlphaCut ✅ | Filename templates, frame-skip mask reuse, ETA | 1 shipped (3.46), 1 pending (2.8) |
| FrameSnap ✅ | Filename templates, frame export, contact-sheet export | 2 shipped (3.46), 1 pending (8.1) |
| GifText ✅ | Meme text templates, per-frame GIF timing | 1 shipped (3.46), 1 pending (3.6) |
| GifStudio | Per-frame GIF timing | 1 pending (3.6) |
| ClipForge ✅ | Lossless trim, interpolation preset grouping | 1 shipped (3.48), 1 pending (2.6) |
| EXTRACTORX ✅ | Filename templates, split-volume detection | 1 shipped (3.46), 1 pending (1.4) |
| ImageForge / WallBrand | Watermark overlay | 1 pending (2.2) |
| Claude-Ultimate-Enhancer | Unified preset library | 1 pending (2.1) |
| FaceSlim ✅ | Preset system, crash-log recovery UI | 1 shipped (3.47), 1 pending (2.1) |
| HostShield | Anomaly detection | 2 pending (1.5, 3.5) |
| Aura | VFX particles, lossless audio trim | 1 pending (2.10) |
| AlarmClockXtreme | Scheduled export | 1 pending (3.1) |
| Multistreamer | Synced multi-view, grid layouts, multi-pane preview | 2 pending (3.2, 8.3) |
| MediaDL | Codec detection on ingest | merged into 2.7 |
| DuplicateFF / FileOrganizer | Perceptual hash dedupe, auto-categorize | 2 pending (1.2, 2.3) |
| HEICShift | Universal format ingest | 1 pending (3.4) |
| GeminiWatermarkRemover / VideoSubtitleRemover | Watermark / subtitle removal | 1 pending (2.5) |
| PDFedit | Live in-canvas text editing | 1 pending (2.4) |
| Bookmark-Organizer-Pro | Hierarchical tag + smart search | 1 pending (3.3) |
| DarkReaderLocal / stylebot | Theme variants | 1 pending (2.9) |
| KeepSyncNotes ✅ | Scratchpad notes, sidecar export, markdown reports | 2 shipped (3.47, 3.48), 1 pending (7.6) |
| NeonNote | Undo branching tree, encoding detect, preview opacity | 3 pending (6.2, 6.3, 6.6 / 7.5) |
| qBittorrent-Vanced / qB-Enhanced-Edition | Render speed throttle, batch export queue | 2 pending (6.1, 6.5) |
| UniFile | Export snapshot diff | 1 pending (6.7) |
| LogLens | Project health dashboard, markdown formatting | 2 pending (6.8, 7.6) |
| MavenForge | Project template scaffolder | 1 pending (6.10) |
| Kindred / VIPTrack | Usage activity dashboard, AI edit coach | 2 pending (7.3, 7.9) |
| XRayAcquisition / mnamer | Clip EXIF/GPS/camera metadata ingest | 1 pending (7.4) |
| SkyTrack | Frame-preview grid, geo-tagged map view | 2 pending (7.1, 7.7) |
| Openshop | Frame-preview grid, preset marketplace scaffold | 2 pending (7.1, 7.8) |
| Doordash-Enhanced | Export health pre-flight | 1 pending (7.2) |
| Job-Search | Clip filter chips | 1 pending (8.2) |
| DefenderControl / DefenderShield | (informs 6.1 throttle + 2.1 preset UI) | reinforcement |
| AdapterLock | Encrypted project archive | 1 pending (8.4) |
| NexRay | Output profile pipelines | 1 pending (8.5) |

---

## 6. Wave 2 — Additional Research (April 2026)

Second-pass exploration of qBittorrent-Vanced/Enhanced-Edition, KeepSyncNotes, NeonNote, LogLens, Vigil, PathForge, UniFile.

### 6.1 Adaptive render speed limit (Tier 1)
**Source:** qBittorrent-Vanced (speed-limit presets)
**Why:** Export rendering can thermal-throttle the device. Add Low/Medium/High/Max presets that throttle encoder by injecting brief sleeps between frames. Real-time indicator above progress bar: "Speed: 45% (Limit: 75%)".
**Integration:** `ExportSheet` dropdown above progress bar; `VideoEngine.export()` respects a throttle param (`Thread.sleep(throttleMs)` in progress callback).
**Effort:** S

### 6.2 File encoding auto-detect on subtitle import (Tier 1)
**Source:** NeonNote (8-level encoding pipeline)
**Why:** Imported `.srt` files with GB2312, Shift-JIS, or Windows-1252 show as garbled text. BOM check → UTF-8 attempt → fallback chain prevents the garble.
**Integration:** New `FileEncodingDetector.kt` called from `SubtitleExporter`/importer path. Store `Subtitle.encoding: String`.
**Effort:** S

### 6.3 Preview panel variable opacity (Tier 1)
**Source:** NeonNote (window opacity slider)
**Why:** Useful for checking overlay alignment against UI — pinch-zoom + Alt-modifier to blend video with underlying editor chrome.
**Integration:** `PreviewPanel` pointerInput + `Project.previewOpacity: Float` persisted in Room.
**Effort:** S

### 6.4 Scratchpad notes per project — ✅ shipped in v3.47.0
**Source:** KeepSyncNotes
**Shipped:** `Project.notes` Room-migrated field (MIGRATION_5_6), `ScratchpadSheet` Composable with 750ms-debounced auto-save, overflow menu entry.
**Remaining:** Optional sidecar `.txt` bundle on export is still pending — deferred to a later wave.

### 6.5 Export batch queue with priority reorder & pause/resume (Tier 2)
**Source:** qBittorrent-Enhanced-Edition (queue management + bandwidth limits)
**Why:** Existing `BatchExportPanel` runs jobs sequentially — no reorder, no pause/resume, no persistent queue across app restarts. Add Room-backed queue with drag-reorder, pause/resume per job, priority levels.
**Integration:** Promote `batchExportQueue` to a Room table with stable IDs. New `QueueManagerService` (foreground service) consumes queue via WorkManager. `BatchExportPanel` → `BatchExportQueueSheet` with drag handles + priority chips.
**Effort:** M

### 6.6 Multi-level undo timeline with visual rollback (Tier 2)
**Source:** NeonNote (undo timeline visualization)
**Why:** Linear undo stack forces sequential step-back. Visual card shows "Trim (2m ago) → Add Music (1m ago) → Color Grade (now)" — tap any point to instant-rollback.
**Integration:** Extend `EditorViewModel` undo stack to capture label + timestamp. New `UndoTimelineSheet` Composable; horizontal scroll list with highlighted current position.
**Effort:** M

### 6.7 Export config snapshot & before/after diff (Tier 2)
**Source:** UniFile (undo timeline with preview) + NeonNote (session restore)
**Why:** "Last export was 1080p H.264. This one is 4K HEVC — 5× longer, 4× file size." Warn on drastic config changes so users don't accidentally overwrite a good preset.
**Integration:** New `ExportSnapshot` Room table (ExportConfig + first-frame thumbnail bytes + timestamp). `ExportSheet` shows diff card on repeat-open; highlights changed fields.
**Effort:** M

### 6.8 Project health dashboard (Tier 2)
**Source:** LogLens (stats + anomaly detection) + HostShield baseline patterns
**Why:** ProjectListScreen shows health badge: "8 clips, 2.3 GB, 127 effects. Est. render 45m. Warning: clip #5 has 22 effects." Prevents OOM surprises before user hits export.
**Integration:** New `ProjectHealthAnalyzer.kt` runs on auto-save → `HealthReport` Room row → badge in `ProjectListScreen` card. Thresholds: >50 clips, >10GB media, >15 effects per clip, >3 audio tracks.
**Effort:** M

### 6.9 Subtitle-aware auto-segmentation / scene-cut suggest (Tier 2)
**Source:** LogLens (format detection) + UniFile (metadata + heuristics)
**Why:** Imported SRT — detect sentence boundaries + long pauses via duration heuristic, offer one-tap "Cut clip at each subtitle boundary" for music-video sync workflows.
**Integration:** New `SubtitleSegmentEngine.kt`; `SubtitleSegmentationSheet` showing candidate cuts with preview. Ties into existing `splitClipAtPlayhead` in `ClipEditingDelegate`.
**Effort:** M

### 6.10 Project template scaffolder (Tier 3)
**Source:** MavenForge (rapid scaffolding)
**Why:** "Start from template" flow at project creation — not just blank. Templates include aspect ratio, track layout, starter effects, placeholder music track, default export config. Differs from text-template gallery (which is per-overlay).
**Integration:** New `ProjectTemplate` model + `ProjectTemplateSheet` (already exists for TextTemplates — extend). Built-ins: "YouTube Vlog", "TikTok Reel", "Wedding Film", "Tutorial", "Product Demo".
**Effort:** L (need curated starter content bundled in assets)

---

## 7. Wave 3 — Additional Research (April 2026)

Third-pass exploration covering: backend/frontend, kindred, VIPTrack, PillSleepTracker, iOSIconPack, mnamer, FedEx, Doordash-Enhanced, Openshop, SkyTrack, XRayAcquisition, LogLens, Vigil. Some projects (backend/frontend/kindred) appeared to be empty or placeholder scaffolds — candidates below are drawn from the projects that yielded usable patterns.

### 7.1 Timeline scrubbing frame-preview grid (Tier 1)
**Source:** Openshop (multi-canvas preview) / SkyTrack (grid tile loading)
**Why:** Hold Shift while hovering the timeline → show a 3-wide × 2-tall mini-grid of frames ±3 frames around the cursor (100×100 px each). Faster than scrubbing for precise cut placement.
**Integration:** Extend `PreviewPanel` gesture handling. New `FrameGridComposable` overlaid on `Timeline`. 7-frame cache in `VideoEngine.thumbnailCache`.
**Effort:** S

### 7.2 Export health pre-flight report (Tier 1)
**Source:** Doordash-Enhanced (fee breakdown transparency) + LogLens (anomaly detection)
**Why:** Before dispatching the render, show: "This will take 47m @ 22 Mbps; bitrate is 3× your last preset; recommended is 8 Mbps for 1080p." Warns on drastic config drift from the previous successful export.
**Integration:** New `ExportHealthReportSheet` shown pre-dispatch in `ExportDelegate.startExport`. Reuses `estimateExportEtaSeconds()` + bitrate comparison against last successful `ExportConfig` (stored in `SettingsRepository`).
**Effort:** S

### 7.3 Usage activity dashboard with heatmaps (Tier 2)
**Source:** Kindred (event correlation) + VIPTrack (engagement tracking)
**Why:** Understand editing patterns — peak hours, avg session length, clip count per day, drop-off after crashes. Pairs with §3.5 (Project Health Analyzer). Opt-in only, local-only.
**Integration:** New `ProjectActivityAnalyzer.kt` logging `ActivityEvent(type, timestamp, projectId)` to new Room `activity_log` table. `AnalyticsSheet` panel renders hourly heatmap, session-length histogram, and top-used effects. Gated behind `SettingsRepository.analyticsEnabled`.
**Effort:** M

### 7.4 Clip EXIF / GPS / camera metadata ingest (Tier 2)
**Source:** XRayAcquisition (medical imaging metadata workflows) + mnamer (content matching)
**Why:** GoPro/DJI/smartphone clips ship with rich metadata — GPS, compass, camera model, firmware, date. Parse + store on `Clip.metadata: Map<String, String>`. Unlocks: filter by camera, batch-rename by date range, geo-tag on map (future §7.7 tie-in).
**Integration:** New `ClipMetadataExtractor.kt` using `MediaMetadataRetriever.extractMetadata()` + `ExifInterface` (for image overlays). Room migration: `Clip.metadataJson: String`. New read-only metadata card in clip detail sheet.
**Effort:** M

### 7.5 Multi-step undo with branching tree (Tier 2)
**Source:** NeonNote (undo tree visualization)
**Why:** Current linear undo forces sequential reversal. Promote to a branching tree — jump to any ancestor checkpoint, preserve descendant branches. UX is a visual chain of "Trim (2m) → Add Music (1m) → Color (now)" with tap-to-rollback.
**Integration:** Extend `EditorViewModel.undoStack` from `List<UndoAction>` to a tree with parent links. New `UndoTimeBranchSheet` Composable rendering a horizontal chain with animated diamond nodes. Subsumes/extends §6.6 — this is the richer implementation of the same concept.
**Effort:** M (non-trivial undo refactor)

### 7.6 Markdown project-report export (Tier 2)
**Source:** KeepSyncNotes (scratchpad) + LogLens (markdown formatting)
**Why:** Upgrade the §6.4 scratchpad sidecar. Export project as `.md` with: table of contents, clip gallery (base64-embedded thumbnails), effects inventory, per-clip notes with timecode anchors, overall summary. Ideal for archival, review loops, and async collaboration.
**Integration:** New `MarkdownExporter.kt` + "Export as Markdown Report" entry in ExportSheet. Template-based rendering. Uses existing `VideoEngine.extractThumbnail()` + base64 encode.
**Effort:** M

### 7.7 Geo-tagged clip map view (Tier 3)
**Source:** SkyTrack (2D/3D map overlays + Leaflet dark tiles)
**Why:** For travel / documentary editors, show all project clips pinned on a map (from §7.4 GPS metadata). Tap pin to jump to that clip in the timeline. Useful for visualizing shot coverage.
**Integration:** New `ClipMapSheet` Composable. Uses Mapbox or osmdroid (no Google Maps key required); dark basemap tiles. Pins placed at `Clip.metadata.gps`. Tap → `EditorViewModel.selectClip(clipId)`.
**Effort:** L (new dependency)

### 7.8 Preset marketplace scaffold (Tier 3)
**Source:** Openshop (e-commerce + in-app asset UI)
**Why:** If NovaCut grows a creator economy, users want to browse/download community effect chains. Stub: browsable gallery of `.novacut-preset` bundles with author, description, rating, download count. Payments/DRM are out-of-scope for v1 — free sharing first.
**Integration:** New `PresetMarketplaceSheet` with paginated card grid. Local: `filesDir/presets/marketplace/` folder. Remote: HTTP endpoint returning JSON `[{name, author, rating, downloadUrl}]`. Depends on §2.1 (unified preset library) landing first.
**Effort:** L

### 7.9 AI edit-coach suggestions per clip (Tier 3)
**Source:** Kindred (conversation coaching pattern) + NovaCut's existing AI tools
**Why:** For learning editors: inline tips on the selected clip ("Try a faster cut during this dialogue", "Music swell at 0:32 could lead a chorus"). Heuristic-only v1 (clip-length + audio energy + effect density); LLM upgrade is §7.9.1.
**Integration:** New `AiCoachDelegate.kt` next to existing `AiToolsDelegate`. Surfaced as a "Coach" chip in `AiToolsPanel` when a clip is selected. Suggestions cached on `Clip.suggestions: List<String>`.
**Effort:** L

---

## 8. Wave 4 — Additional Research (April 2026)

Fourth-pass exploration of LaunchPad, ImprovedTube-research, Job-Search, NexRay, ParkerSuite, ClearGem, DeepPurge, AdapterLock, DefenderShield/Control, and revisits of FrameSnap + Multistreamer. Empty/placeholder projects (LaunchPad, ParkerSuite, DeepPurge) produced no candidates; findings below are drawn from the projects that yielded usable patterns.

### 8.1 Contact-sheet export — ✅ shipped in v3.49.0
**Source:** FrameSnap
**Shipped:** `ContactSheetExporter.kt` composites 320×180 midpoint thumbnails into a single PNG grid (2/3/4/5/6 columns). Captions: filename (24-char cap) + `M:SS` duration. Dark Catppuccin background. New ExportSheet toggle + button-label swap. Routed through existing filename template + save-to-gallery MediaStore path.

### 8.2 Clip filter chips — used / unused / duration / effects (Tier 1)
**Source:** Job-Search (`.cat-btn.active` + `.rcard.hidden` visibility pattern)
**Why:** At 50+ clips in a project, users want to filter the project gallery or a pending "Source Clips" bin. Pills: Used on timeline / Unused / Short (<5s) / Long (>60s) / Has effects / No effects. Toggles visibility without navigation cost.
**Integration:** Extend `ProjectListScreen` — new `ClipFilterChips` row above the clip gallery. Filters applied client-side over `ProjectListViewModel.allProjects`. Could also surface inside the MediaManagerPanel for source clips.
**Effort:** S
**Note:** This is the simpler cousin of §3.3 (Hierarchical tag + search). Could ship first as an MVP and evolve into §3.3.

### 8.3 Multi-pane preview grid (Tier 2)
**Source:** Multistreamer (Brady-Bunch grid layout + synced playhead + featured mode)
**Why:** For projects with 4+ video tracks (multicam, PiP-heavy edits), show 2×2 or 3×3 mini-previews all locked to the same playhead + scrub. Lets editors see every source simultaneously instead of track-isolating.
**Integration:** New `MultiPreviewGrid` Composable, opt-in toggle in the main `PreviewPanel`. Each tile is a lightweight `SurfaceView` playing the same ExoPlayer timeline but visually clipped to its source track. "Featured" tile expands on tap.
**Effort:** M

### 8.4 Encrypted project archive with biometric unlock (Tier 2)
**Source:** AdapterLock (registry ACL + escalation chain) + Android KeyStore
**Why:** Creators editing sensitive content (medical, legal, pre-release brand work) want a passphrase/biometric lock on the project. Extend `ProjectArchive.exportArchive()` to optionally AES-GCM encrypt; unlock via BiometricPrompt → KeyStore-derived key.
**Integration:** New `ProjectEncryption.kt` wrapping `javax.crypto.Cipher` + Android KeyStore. `ProjectArchive.importArchive()` detects the encrypted magic bytes and prompts biometric. Depends on ProjectArchive fleshout noted in main [ROADMAP.md](ROADMAP.md).
**Effort:** M

### 8.5 Output profile pipelines (Tier 2)
**Source:** NexRay (13 specialized multi-stage pipelines — each a named chain of transforms)
**Why:** Richer than the current `PlatformPreset` enum. An "Output Profile" is a **named multi-stage pipeline**: e.g. "YouTube Shorts Long" = {trim to 60s, crop 9:16, caption template = Reels Hook, target-size 100MB, codec H.264, sidecar notes.txt}. Users save their own profiles for repeatable workflows.
**Integration:** New `OutputProfile` model aggregating `ExportConfig` + pre-export clip mutations + post-export actions. New "Profiles" tab in `ExportSheet` alongside existing platform-preset chips. 8 built-in profiles + user-saved.
**Effort:** M
**Note:** Super-set of §2.1 (unified preset library) — when §2.1 lands, OutputProfile lives inside its catalog.

### 8.6 Adaptive render throttle (reinforcement)
**Source:** DefenderControl (phase-based async runspaces)
**Why:** Reinforcement of §6.1. DefenderControl's "run in phases with controllable delays" pattern gives a concrete implementation shape for the Low/Med/High/Max render speed picker — phase the encoder in N-frame chunks with configurable sleep between chunks.
**Integration:** No new roadmap entry — this **informs the implementation** of §6.1.
**Effort:** (tracked under §6.1)

### 8.7 Hierarchical preset sections (reinforcement)
**Source:** DefenderControl (20+ settings in hierarchical collapsible phases)
**Why:** Reinforcement of §2.1 (unified preset library). Collapsible per-category sections with per-preset enable toggles.
**Integration:** Informs §2.1 UI design.
**Effort:** (tracked under §2.1)

### Excluded from Wave 4
- **ClearGem** — too domain-specific; subsumed by §2.5 (watermark removal via inpainting).
- **DeepPurge** — system uninstaller; out of scope.
- **ImprovedTube-research** — no concrete porting targets; YouTube-specific export needs are already covered by the `YOUTUBE_1080` / `YOUTUBE_4K` PlatformPresets.
- **NexRay medical-specific modules (DICOM, dental, vet)** — the architectural pattern is the port, not the domain code.

---

## 9. Next-Pass Research Targets (Wave 5)

- **Z:\repos\backend** / **Z:\repos\frontend** — revisit when those repos have substance (currently empty scaffolds)
- **Z:\repos\FrameworkCut** — if exists, matching-name project likely relevant
- Older repos not yet visited: **AppList**, **CoolSites**, **CSV_Power_Tool**, **Base64Converter**, **NATO_PHONETIC_TRAINING** — skim for any hidden gems but likely low signal
- Periodic rescan of repos with recent commits (pick based on `Z:\repos\*\.git` mtime) — new patterns may have been added to previously-surveyed projects
