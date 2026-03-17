# NovaCut - Android Video Editor

## Overview
Full-featured Android video editor built as a PowerDirector alternative. Kotlin + Jetpack Compose + Media3 Transformer.

## Version
v0.7.0

## Tech Stack
- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose (Material 3, Catppuccin Mocha theme)
- **Video Engine**: Media3 Transformer 1.5.1 + ExoPlayer
- **Effects**: OpenGL ES 3.0 GLSL shaders (via Media3 RgbMatrix + custom GlEffect)
- **Audio**: MediaCodec PCM decode, waveform extraction, mixing engine
- **DI**: Hilt/Dagger
- **DB**: Room (project persistence)
- **Navigation**: Compose Navigation (projects list -> editor)
- **Architecture**: MVVM + single-activity Compose navigation

## Build
- Android Studio Ladybug+ / AGP 8.7.3 / Gradle 8.9
- Min SDK 26, Target SDK 35, Compile SDK 35
- `JAVA_HOME='C:\Program Files\Android\Android Studio\jbr' ./gradlew assembleDebug`
- Needs `local.properties` with `sdk.dir` pointing to Android SDK

## Key Files
- `app/src/main/java/com/novacut/editor/`
  - `MainActivity.kt` - Single activity with NavHost (projects -> editor/{projectId})
  - `ui/projects/ProjectListScreen.kt` - Project gallery with search, sort, create/delete/duplicate/open
  - `ui/projects/ProjectListViewModel.kt` - Project list state management (search, sort, duplicate)
  - `ui/editor/EditorScreen.kt` - Main editor composable (EditorTopBar + preview + timeline + BottomToolArea) with onAction dispatch
  - `ui/editor/EditorViewModel.kt` - Editor state management (tracks, clips, effects, undo/redo)
  - `ui/editor/Timeline.kt` - Custom multi-track timeline with thumbnail strips + waveforms + trim handles
  - `ui/editor/PreviewPanel.kt` - ExoPlayer-based video preview with playback controls
  - `ui/editor/ToolPanel.kt` - PowerDirector-style BottomToolArea (two-mode tab bar + sub-menu grids) + effects/speed/transform/crop/transition panels
  - `ui/editor/TextEditorSheet.kt` - Text overlay editor with animations
  - `ui/editor/AudioPanel.kt` - Audio controls, waveform visualization, voiceover
  - `ui/editor/AiToolsPanel.kt` - AI tools UI (captions, bg removal, scene detect, smart crop)
  - `ui/mediapicker/MediaPicker.kt` - Media picker sheet with camera capture (CaptureVideo + FileProvider)
  - `res/xml/file_paths.xml` - FileProvider paths config for camera capture + export sharing
  - `ui/export/ExportSheet.kt` - Export settings (resolution, codec, quality, progress, share, save to gallery)
  - `engine/VideoEngine.kt` - Media3 Transformer export + ExoPlayer + thumbnail extraction
  - `engine/AudioEngine.kt` - Audio waveform extraction + PCM mixing
  - `engine/KeyframeEngine.kt` - Keyframe interpolation with 5 easing curves
  - `engine/ExportService.kt` - Foreground service for export notifications (MEDIA_PROCESSING type)
  - `engine/VoiceoverRecorder.kt` - MediaRecorder wrapper for voiceover
  - `engine/ProjectAutoSave.kt` - Periodic auto-save with full JSON serialization/deserialization
  - `engine/AppModule.kt` - Hilt DI module (Room DB + DAO)
  - `engine/db/ProjectDatabase.kt` - Room database (v3) + ProjectDao + converters
  - `effects/ShaderEffects.kt` - GLSL shader source (effects + transitions)
  - `ai/AiFeatures.kt` - AI features (auto captions, bg removal, scene detect, motion track)
  - `model/Project.kt` - All data models (Project, Track, Clip, Effect, Transition, Keyframe, etc.)

## Architecture Decisions
- **Immutable collections** in all models (List/Map, not MutableList/MutableMap) for safe undo/redo copy-on-write
- **Transformer.start() on Main thread** - Media3 Transformer requires a Looper, export runs withContext(Dispatchers.Main)
- **Multi-clip playback** via ExoPlayer setMediaItems() with ClippingConfiguration per clip
- **Player.Listener** syncs play/pause/end state; periodic coroutine syncs playhead at ~30fps. Tracked via `setPlayerListener()`/`removePlayerListener()` for proper lifecycle cleanup.
- **Auto-save** uses org.json serialization of full Track/Clip/Effect/Keyframe/TextOverlay model tree with safe deserialization (optString/optLong/safeValueOf — never crashes on missing or unknown values)
- **SavedStateHandle** for projectId in EditorViewModel, loaded from NavHost route arg
- **Panel mutual exclusion** — atomic `dismissedPanelState()` + show in single `_state.update` to prevent intermediate states
- **Trim debounce** — `beginTrim()` saves undo once on drag start; `trimClip()` updates live without undo spam
- **Ripple delete** — clip deletion shifts subsequent clips back to close timeline gaps
- **Media type routing** — MediaPickerSheet passes media type string; audio routed to AUDIO track
- **rebuildPlayerTimeline()** — called after every clip mutation (add, delete, split, trim, speed, reverse, undo, redo) to keep ExoPlayer in sync with visual timeline
- **VideoEngine is @Singleton** — ViewModel calls `removePlayerListener()` + `resetExportState()` in onCleared(), never `release()`. The engine outlives any individual ViewModel.
- **Thumbnail cache** — thread-safe LinkedHashMap with `cacheLock` synchronization and `removeEldestEntry` auto-eviction at 200 entries. `accessOrder=false` prevents ConcurrentModificationException.
- **AudioEngine PCM decode** — ShortArray chunks collected then concatenated via `System.arraycopy` to avoid boxing millions of Shorts through `MutableList<Short>`
- **Project thumbnails** — Stores first video clip's source URI in `Project.thumbnailUri`; Coil `VideoFrameDecoder` renders frame at display time (no file management needed)
- **Camera capture** — `ActivityResultContracts.CaptureVideo()` + FileProvider URI from cache dir. No CAMERA permission needed — delegated to system camera app via intent.
- **AI tools wiring** — `runAiTool(toolId)` dispatches to `AiFeatures` methods in viewModelScope. Scene detect auto-splits clips at boundaries (reverse-order to avoid position drift). Auto captions converted to TextOverlay list. Smart crop shows suggestion toast.
- **Audio fade persistence** — `fadeInMs`/`fadeOutMs` stored on Clip model (not local UI state). AudioPanel reads from clip data so values survive panel close/reopen. Serialized in ProjectAutoSave.
- **Duplicate clip** — copies clip with fresh UUIDs for clip + effects, inserts after original, shifts subsequent clips forward. Transition nulled on copy to avoid doubled transitions.
- **Copy/Paste effects** — `copiedEffects` in EditorState stores copied effect list. Paste creates fresh UUIDs to avoid ID collisions.
- **Freeze frame** — extracts JPEG via MediaMetadataRetriever at playhead, splits clip, inserts 2s still image clip between halves.
- **Share after export** — FileProvider URI + ACTION_SEND intent via `getShareIntent()`. Requires `<external-files-path>` in file_paths.xml.
- **Save to gallery** — MediaStore API with IS_PENDING pattern (API 29+), direct file copy for API 26-28. No WRITE_EXTERNAL_STORAGE needed on API 29+.
- **Project search/sort** — `ProjectListViewModel` combines allProjects with searchQuery + sortMode flows. SortMode enum: DATE_DESC, DATE_ASC, NAME_ASC, NAME_DESC, DURATION_DESC.
- **Project duplicate** — copies Room DB record + auto-save JSON file with fresh projectId and timestamp.
- **Social media crop presets** — CropPanel redesigned with platform labels (YouTube/TV, TikTok/Reels, Instagram Square/Portrait, Classic, Cinematic). Added RATIO_4_5 to AspectRatio enum.
- **PowerDirector-style layout** — EditorTopBar (Home/Undo/Redo/Delete/More/Export) + BottomToolArea with two-mode tab bar. Project mode: Edit/Audio/Text/Effects/AI/Aspect tabs. Clip mode: Back/Edit/Audio Tool/Speed/Transform/Effects/Transition/AI tabs. Tabs with sub-menus (Text, AI, clip-Edit) toggle SubMenuGrid overlays. Direct-action tabs dispatch via onAction string IDs to EditorScreen's when-block routing to ViewModel methods. Tab state resets on clip mode change via LaunchedEffect.

## Features Wired & Working
- Project gallery (create, open, delete, swipe-to-delete with confirm)
- Navigation: projects list -> editor/{projectId} -> back
- Multi-track timeline (video, audio, overlay, text tracks) with real thumbnails
- Multi-clip playback via ExoPlayer (setMediaItems with clipping)
- Interactive trim handles (drag to adjust in/out points)
- Split clip at playhead
- 40+ video effects (color, filters, blur, distortion, keying) with adjustable parameters
- 25 transition types with GLSL shaders
- Speed control (0.1x-16x) + reverse
- Keyframe opacity applied during export via RgbMatrix
- Text overlays with 10 animation styles
- Audio panel with volume, fade in/out (persisted on clip), waveform visualization (requires clip selection)
- Effects panel with add -> adjust flow (EffectsPanel -> EffectAdjustmentPanel)
- Transform panel: position X/Y, scale X/Y, rotation, opacity sliders with reset
- Crop panel: social media presets (YouTube/TV 16:9, TikTok/Reels 9:16, Instagram Square 1:1, Instagram Portrait 4:5, Classic 4:3, Portrait Classic 3:4, Cinematic 21:9) — project-level, no clip selection required
- PowerDirector-style UI: compact top bar + two-mode bottom tab bar with contextual sub-menu grids
- Disabled tool feedback: "Select a clip to use Effects" toast when tapping Effects without selection
- Duplicate clip (inserts copy after selected clip)
- Copy/Paste effects between clips
- Freeze frame (extract frame at playhead, insert as 2s still image)
- Share exported video (ACTION_SEND + FileProvider)
- Save exported video to device gallery (MediaStore)
- Project search (filter by name)
- Project sort (recent, oldest, A-Z, Z-A, longest)
- Duplicate project (Room + auto-save copy)
- AI tools: scene detection (auto-split at boundaries), auto captions (text overlays), smart crop (suggestion)
- Camera capture via system camera app (CaptureVideo intent)
- Project thumbnails on gallery cards (Coil VideoFrameDecoder)
- Export: resolution, codec, bitrate from config; aspect-ratio-aware output dimensions; foreground service with MEDIA_PROCESSING type
- R8 minification enabled with comprehensive ProGuard keep rules (~5MB APK)
- Undo/redo (50 levels, immutable state snapshots)
- Project auto-save every 30s with full state recovery
- Project persistence to Room DB
- Catppuccin Mocha dark theme
- Permission handling (media, audio, notifications)

## Gotchas
- Media3 Transformer effects use `@UnstableApi` annotation - suppress with `@OptIn`
- Thumbnail extraction via `MediaMetadataRetriever` is slow - use caching aggressively
- `largeHeap=true` in manifest is required for video editing workloads
- Room `fallbackToDestructiveMigration()` - no `dropAllTables` parameter in Room 2.6.1
- Room DB stores project metadata only - track/clip state serialized separately via ProjectAutoSave
- FFmpeg not yet integrated - using pure Media3 pipeline
- VideoEngine is @Singleton - don't call `release()` from ViewModel onCleared (use `removePlayerListener()` + `resetExportState()`)
- `local.properties` not in git - must be created with sdk.dir path
- RgbMatrix color matrices are row-major 4x4: `[R_out = row0 dot [R,G,B,A]]`. Alpha channel (=1) serves as offset for translation effects (brightness, invert).
- OES shader extension for GLES 3.0 is `GL_OES_EGL_image_external_essl3` (not `GL_OES_EGL_image_external`)
- Room TypeConverters must handle unknown enum values gracefully (try/catch around valueOf)
- `startForegroundService()` required on API 26+ (minSdk), with SDK version check for compatibility
- Auto-save deserialization uses `opt*` methods throughout to survive missing/corrupt fields
- Room DB v1→v2→v3 migration handled by `fallbackToDestructiveMigration()` — drops all data on schema change. Acceptable for dev; needs proper migration before release with user data.
- FileProvider requires `res/xml/file_paths.xml` with `<cache-path>` entry matching the directory used for camera temp files
- AI scene detection splits clips in reverse-order (sortedByDescending) to prevent timeline position shifts from invalidating subsequent split points
- TRIM and SPLIT tools must call `dismissAllPanels()` — they bypass the boolean-panel pattern since they don't have their own panels
- Speed panel visibility driven by `currentTool == EditorTool.SPEED` (not a boolean flag like other panels), so it self-dismisses on tool change
- BottomToolArea manages `activeTabId` internally — sub-menu visibility is derived from activeTabId + isClipMode, not stored in ViewModel. LaunchedEffect resets activeTabId when isClipMode changes.

## Next Steps
- Integrate Whisper ONNX for real auto captions (currently placeholder frame-based detection)
- Integrate MediaPipe SelfieSegmentation for background removal
- Add video stabilization (EIS via OpenGL transform)
- Template system
- Proper Room migration strategy (replace destructive migration before production)
- Photo picker for Android 13+ (ACTION_PICK_IMAGES)
