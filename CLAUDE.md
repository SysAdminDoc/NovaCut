# NovaCut - Android Video Editor

## Overview
Full-featured Android video editor built as a PowerDirector alternative. Kotlin + Jetpack Compose + Media3 Transformer.

## Version
v0.1.0

## Tech Stack
- **Language**: Kotlin 2.1.0
- **UI**: Jetpack Compose (Material 3, Catppuccin Mocha theme)
- **Video Engine**: Media3 Transformer 1.5.1 + ExoPlayer
- **Effects**: OpenGL ES 3.0 GLSL shaders (via Media3 RgbMatrix + custom GlEffect)
- **Audio**: MediaCodec PCM decode, waveform extraction, mixing engine
- **DI**: Hilt/Dagger
- **DB**: Room (project persistence)
- **Architecture**: MVVM + single-activity Compose navigation

## Build
- Android Studio Ladybug+ / AGP 8.7.3 / Gradle 8.9
- Min SDK 26, Target SDK 35, Compile SDK 35
- `./gradlew assembleDebug` or `./gradlew assembleRelease`

## Key Files
- `app/src/main/java/com/novacut/editor/`
  - `ui/editor/EditorScreen.kt` - Main editor composable (preview + timeline + tools)
  - `ui/editor/EditorViewModel.kt` - Editor state management (tracks, clips, effects, undo/redo)
  - `ui/editor/Timeline.kt` - Custom multi-track timeline with thumbnail strips
  - `ui/editor/PreviewPanel.kt` - ExoPlayer-based video preview
  - `ui/editor/ToolPanel.kt` - Tool strip + effects panel + speed panel + transitions
  - `ui/editor/TextEditorSheet.kt` - Text overlay editor with animations
  - `ui/editor/AudioPanel.kt` - Audio controls, waveform visualization, voiceover
  - `ui/editor/AiToolsPanel.kt` - AI tools UI (captions, bg removal, scene detect, etc.)
  - `ui/export/ExportSheet.kt` - Export settings (resolution, codec, quality, progress)
  - `engine/VideoEngine.kt` - Media3 Transformer export + ExoPlayer + thumbnail extraction
  - `engine/AudioEngine.kt` - Audio waveform extraction + PCM mixing
  - `engine/KeyframeEngine.kt` - Keyframe interpolation with 5 easing curves
  - `engine/ExportService.kt` - Foreground service for export notifications
  - `engine/VoiceoverRecorder.kt` - MediaRecorder wrapper for voiceover
  - `engine/ProjectAutoSave.kt` - Periodic auto-save to disk
  - `effects/ShaderEffects.kt` - GLSL shader source (effects + transitions)
  - `ai/AiFeatures.kt` - AI features (auto captions, bg removal, scene detect, motion track)
  - `model/Project.kt` - All data models (Project, Track, Clip, Effect, Transition, Keyframe, etc.)

## Features Implemented
- Multi-track timeline (video, audio, overlay, text tracks)
- Trim, split, merge clips
- 40+ video effects (color, filters, blur, distortion, keying)
- 25 transition types with GLSL shaders
- Speed control (0.1x-16x) + reverse
- Keyframe animation (position, scale, rotation, opacity, volume) with 5 easing curves
- Text overlays with 10 animation styles
- Audio waveform visualization + mixing + volume/fade controls
- Voiceover recording
- Chroma key (green screen) with similarity/smoothness/spill controls
- Export: 480p-4K, 24/30/60fps, H.264/HEVC, adjustable quality
- Foreground service export with progress notification
- Undo/redo (50 levels)
- Project auto-save
- AI tools: auto captions, background removal, scene detection, motion tracking, smart crop
- Catppuccin Mocha dark theme
- Permission handling (media, audio, notifications)

## Gotchas
- Media3 Transformer effects use `@UnstableApi` annotation - suppress with `@OptIn`
- Thumbnail extraction via `MediaMetadataRetriever` is slow - use caching aggressively
- `largeHeap=true` in manifest is required for video editing workloads
- Export uses `createInputSurface()` which requires `COLOR_FormatSurface` in MediaCodec config
- Room DB stores project metadata only - track/clip state serialized separately for auto-save
- FFmpeg not yet integrated - using pure Media3 pipeline. Add FFmpegX-Android for format gaps.

## Next Steps
- Integrate Whisper ONNX for real auto captions
- Integrate MediaPipe SelfieSegmentation for background removal
- Add video stabilization (EIS via OpenGL transform)
- Camera capture integration
- Project gallery/browser screen
- Template system
- PyInstaller-style CI/CD for APK builds
