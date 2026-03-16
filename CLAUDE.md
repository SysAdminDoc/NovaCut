# NovaCut - Android Video Editor

## Overview
Full-featured Android video editor built as a PowerDirector alternative. Kotlin + Jetpack Compose + Media3 Transformer.

## Version
v0.4.0

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
  - `ui/projects/ProjectListScreen.kt` - Project gallery with create/delete/open
  - `ui/projects/ProjectListViewModel.kt` - Project list state management
  - `ui/editor/EditorScreen.kt` - Main editor composable (preview + timeline + tools)
  - `ui/editor/EditorViewModel.kt` - Editor state management (tracks, clips, effects, undo/redo)
  - `ui/editor/Timeline.kt` - Custom multi-track timeline with thumbnail strips + waveforms + trim handles
  - `ui/editor/PreviewPanel.kt` - ExoPlayer-based video preview with playback controls
  - `ui/editor/ToolPanel.kt` - Tool strip + effects panel + speed panel + transitions
  - `ui/editor/TextEditorSheet.kt` - Text overlay editor with animations
  - `ui/editor/AudioPanel.kt` - Audio controls, waveform visualization, voiceover
  - `ui/editor/AiToolsPanel.kt` - AI tools UI (captions, bg removal, scene detect, etc.)
  - `ui/export/ExportSheet.kt` - Export settings (resolution, codec, quality, progress)
  - `engine/VideoEngine.kt` - Media3 Transformer export + ExoPlayer + thumbnail extraction
  - `engine/AudioEngine.kt` - Audio waveform extraction + PCM mixing
  - `engine/KeyframeEngine.kt` - Keyframe interpolation with 5 easing curves
  - `engine/ExportService.kt` - Foreground service for export notifications (MEDIA_PROCESSING type)
  - `engine/VoiceoverRecorder.kt` - MediaRecorder wrapper for voiceover
  - `engine/ProjectAutoSave.kt` - Periodic auto-save with full JSON serialization/deserialization
  - `engine/AppModule.kt` - Hilt DI module (Room DB + DAO)
  - `engine/db/ProjectDatabase.kt` - Room database + ProjectDao + converters
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
- Audio panel with volume controls + real waveform visualization
- Effects panel with add -> adjust flow (EffectsPanel -> EffectAdjustmentPanel)
- AI tools panel (UI stub)
- Export: resolution, codec, bitrate from config; foreground service with MEDIA_PROCESSING type
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

## Next Steps
- Integrate Whisper ONNX for real auto captions
- Integrate MediaPipe SelfieSegmentation for background removal
- Add video stabilization (EIS via OpenGL transform)
- Camera capture integration
- Project thumbnails on gallery cards
- Template system
- CI/CD workflow for APK builds
