# NovaCut

A full-featured Android video editor built with Kotlin and Jetpack Compose. Designed as a stable, open alternative to PowerDirector.

## Features

**Editing**
- Multi-track timeline (video, audio, overlay, text)
- Trim, split, merge, crop, rotate
- Speed control (0.1x - 16x) with reverse
- Keyframe animation for position, scale, rotation, opacity
- Undo/redo with 50 levels

**Effects & Transitions**
- 40+ video effects (color grading, filters, blur, distortion, chroma key)
- 25 GPU-accelerated transitions (dissolve, wipe, zoom, glitch, swirl, etc.)
- GLSL shader-based processing pipeline

**Audio**
- Waveform visualization
- Multi-track mixing with volume controls
- Fade in/out
- Voiceover recording

**Text**
- Customizable text overlays
- 10 animation styles (fade, slide, scale, typewriter, bounce, spin)
- Color picker, font size, bold/italic, alignment, stroke

**AI Tools** (all on-device, no internet required)
- Auto captions — audio energy speech segmentation with timed text overlays
- Scene detection — frame difference analysis with auto-split at boundaries
- Auto color correction — histogram-based brightness/contrast/saturation/temperature
- Video stabilization — motion vector estimation with counter-motion zoom + keyframes
- Audio denoise — noise floor analysis with volume optimization
- Background removal — automatic green/blue screen detection with chroma key
- Motion tracking — template matching with position keyframe generation
- Smart crop — saliency-weighted region analysis for intelligent framing

**Export**
- 480p to 4K Ultra HD
- 24, 30, 60 fps
- H.264 / HEVC codecs
- Adjustable quality presets
- Background export with progress notification

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Theme | Catppuccin Mocha |
| Video | Media3 Transformer + ExoPlayer |
| Effects | OpenGL ES 3.0 (GLSL shaders) |
| Audio | MediaCodec PCM decode/encode |
| DI | Hilt |
| Database | Room |
| Architecture | MVVM |

## Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

**Requirements**: Android Studio Ladybug+, JDK 17, Android SDK 35

## License

MIT
