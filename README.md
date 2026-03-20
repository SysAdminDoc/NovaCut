# NovaCut v2.3.0

A full-featured Android video editor built with Kotlin and Jetpack Compose. Designed as a stable, open alternative to PowerDirector and DaVinci Resolve.

## Features

**Editing**
- Multi-track timeline (video, audio, overlay, text, adjustment layers)
- Trim, split, merge, crop, rotate, slip/slide edits
- Speed control (0.1x-16x) with bezier speed ramping curves
- Keyframe animation for position, scale, rotation, opacity, volume
- Bezier/hold interpolation with 8 keyframe presets (Ken Burns, pulse, shake, etc.)
- Undo/redo (50 levels) with full state restoration
- Long-press multi-select for batch clip operations
- Proxy editing for smooth playback on lower-end devices
- Pinch-to-zoom + zoom in/out/fit buttons on timeline

**Effects & Transitions**
- 40+ video effects (color grading, filters, blur, distortion, chroma key, BG removal)
- 25 GPU-accelerated GLSL transitions (dissolve, wipe, zoom, glitch, swirl, etc.)
- 18 blend modes with per-track compositing
- Freehand/rect/ellipse/gradient masks with feather + keyframing
- Lift/gamma/gain color wheels, RGB curves, HSL qualifier
- LUT import (.cube/.3dl) with intensity control

**Audio**
- Full audio mixer with per-track faders, pan, mute/solo, VU meters
- 15 DSP effects (EQ, compressor, limiter, reverb, delay, chorus, de-esser, etc.)
- Waveform visualization with fade envelope overlay
- Beat detection and auto-duck (speech-aware volume keyframing)
- Voiceover recording
- Audio normalization

**AI Tools**
- **Auto Captions** — Whisper ONNX speech-to-text (downloads ~75MB model on first use). Falls back to energy-based segmentation.
- **Background Removal** — MediaPipe selfie segmentation (~256KB model) with per-frame alpha mask. Falls back to chroma key.
- **Style Transfer** — Cinematic color grade from frame analysis (Noir, Warm Cinematic, Moody, etc.)
- **Neural Upscale** — Resolution upgrade (480p-4K) with compensating sharpness
- **Scene Detection** — Frame difference analysis with auto-split
- **Auto Color** — Histogram-based brightness/contrast/saturation/temperature
- **Stabilization** — Motion vector estimation with counter-motion keyframes
- **Audio Denoise** — Noise floor analysis with volume optimization
- **Motion/Face Tracking** — Template matching with position keyframe generation
- **Smart Crop/Reframe** — Saliency-weighted region analysis

**Text**
- Rich text overlays with 10+ animation styles
- Text templates (lower thirds, title cards, end screens, CTAs)
- Text on path (straight, curved, circular, wave)
- Shadow, glow, letter spacing, line height controls
- Auto captions with karaoke/word-pop/bounce styles

**Export**
- 480p to 4K Ultra HD (H.264 / HEVC / AV1)
- One-tap platform presets (YouTube, TikTok, Instagram, Twitter/X, LinkedIn)
- Batch export with multiple presets
- Audio-only and stems export modes
- Background export with progress notification + cancel
- Chapter markers and subtitle export (SRT/VTT/ASS)

**Project Management**
- User template system (save/load/delete project templates)
- Project snapshots with version history
- Project archive (ZIP export)
- Auto-save with configurable interval
- Cloud backup UI (Google Sign-In backend pending)

**Settings**
- Default resolution, frame rate, aspect ratio
- Auto-save toggle + interval (15-300s)
- Proxy resolution selector
- All settings persist via DataStore

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1.0 |
| UI | Jetpack Compose + Material 3 (Catppuccin Mocha) |
| Video | Media3 1.9.2 (Transformer + ExoPlayer) |
| Effects | OpenGL ES 3.0 (GLSL shaders) |
| Audio | MediaCodec PCM + DSP engine |
| Speech | ONNX Runtime 1.17.0 (Whisper tiny.en) |
| Segmentation | MediaPipe Tasks Vision 0.10.14 |
| DI | Hilt/Dagger |
| Database | Room (v4 with migration chain) |
| Settings | DataStore Preferences |
| Architecture | MVVM + single-activity Compose navigation |

## Build

```bash
JAVA_HOME='C:\Program Files\Android\Android Studio\jbr' ./gradlew assembleDebug
```

**Requirements**: Android Studio Ladybug+, AGP 8.7.3, Gradle 8.9, JDK 17, Android SDK 35

**Min SDK**: 26 (Android 8.0) | **Target SDK**: 35

## License

MIT
