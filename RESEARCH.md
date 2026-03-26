# NovaCut — Open Source Research & Feature Improvement Guide

## Overview
Comprehensive research into open source projects that can improve NovaCut's features, expand capabilities, and add new functionality. Each section covers current state, open source alternatives, and specific implementation recommendations.

---

## 1. Timeline & NLE Editing

### Current State
- Basic trim, split, speed adjustment
- `slipClip()` and `slideClip()` implemented but not wired to UI gestures
- No magnetic snapping, no clip grouping
- `beginScrub()`/`endScrub()` wired to ruler drag

### Open Source Projects

#### Kdenlive (github.com/KDE/kdenlive)
- **Ripple/Roll/Slip/Slide edits:** All four trim modes implemented. Ripple changes clip duration and shifts subsequent clips; slip adjusts in/out point without affecting position or neighbors; roll adjusts edit point between adjacent clips.
- **Magnetic snapping:** Proximity-based — when a clip edge enters a configurable pixel threshold of another edge, it "locks." Uses sorted edge list for O(log n) lookup.
- **Clip grouping:** Groups lock clips together preserving relative positions. Supports nested groups.
- **Multi-track compositing:** Unlimited video/audio tracks; highest video track occludes lower ones.

#### Olive Video Editor (github.com/olive-editor/olive)
- **Command pattern:** All timeline operations encapsulated as serializable commands for reliable undo/redo.
- **Node-based compositing:** DAG-based render pipeline — any output feeds any input.
- **GPU-accelerated rendering:** OpenGL/Vulkan for real-time preview from the ground up.

#### OpenTimelineIO (github.com/AcademySoftwareFoundation/OpenTimelineIO)
- Pixar's timeline interchange format with Java bindings (arm64-v8a JNI).
- Adapters for FCPXML, CMX 3600 EDL, AAF, native `.otio` JSON.
- Enables desktop NLE round-tripping (rough cut mobile → finish on DaVinci/Premiere).

### Improvements for NovaCut
- Magnetic snapping: sorted edge list + 8dp proximity threshold
- `ClipGroup` data class for grouped moves
- Command pattern for undo/redo (more reliable than snapshot-based)
- Wire `slipClip()`/`slideClip()` to drag gestures
- OTIO export for pro users

---

## 2. Audio Mixing & Effects

### Current State
- Parametric EQ, compressor, chorus, delay, pitch shift (naive linear interpolation)
- Pan slider, VU meters with ballistic smoothing

### Open Source Projects

#### TarsosDSP (github.com/JorenSix/TarsosDSP)
- Pure Java real-time audio processing — runs directly on Android, no NDK.
- IIR filters (parametric EQ building blocks), YIN pitch detection, WSOLA time-stretching, percussion onset detection.
- `AudioDispatcher` callback-driven architecture.

#### Oboe (github.com/google/oboe)
- Google's C++ low-latency audio library (~10ms round-trip on modern devices).
- Wraps AAudio (API 27+) and OpenSL ES. Includes sinc-based sample rate converter.
- Recommended by Google over Android's AudioEffect API.

#### Soundpipe (github.com/PaulBatchelor/Soundpipe)
- 100+ C DSP modules: Moog filter, Schroeder/zitareverb, compressor, distortion, delay lines.
- Compiles as single static library, minimal deps. Already compiled on Android.

#### libebur128 (github.com/jiixyj/libebur128)
- Pure ANSI C EBU R128 loudness measurement.
- Momentary, short-term, integrated loudness + loudness range (EBU TECH 3342).

### Improvements for NovaCut
- Replace naive pitch shift with TarsosDSP's WSOLA
- Add reverb via Soundpipe (zitareverb module, NDK)
- EBU R128 loudness normalization (YouTube -14, Podcast -16, Broadcast -23 LUFS)
- Oboe resampler for mixing 44.1kHz music with 48kHz video audio
- Sidechain ducking: compress music keyed by voice RMS

---

## 3. Noise Reduction

### Current State
- Basic spectral analysis + DSP filters. No ML-based approach.

### Open Source Projects

| Project | URL | Technique | Android? | Model Size |
|---------|-----|-----------|----------|------------|
| AndroidDeepFilterNet | github.com/KaleyraVideo/AndroidDeepFilterNet | Deep NN predicting complex spectral filters per frequency bin. PESQ 3.5-4.0+ | **Maven dependency** | ~8MB |
| RNNoise | github.com/xiph/rnnoise | GRU RNN with bark-scale band decomposition | NDK (pure C) | ~85KB |
| NSNet2 | github.com/microsoft/DNS-Challenge | ONNX RNN with early-exit adaptive compute | ONNX Runtime | ~5MB |

### Recommendation
- **Primary:** AndroidDeepFilterNet — one-line Maven integration, best quality
- **Fallback:** RNNoise for low-end devices (85KB model)
- Add "Clean Audio" toggle on audio clips

---

## 4. Beat Detection & Music Analysis

### Current State
- Basic energy-based beat detection

### Open Source Projects

| Project | URL | Technique | Android? |
|---------|-----|-----------|----------|
| aubio | aubio.org | Spectral flux onset detection, beat tracking, tempo estimation. C with Android NDK build scripts | Prebuilt NDK module |
| TarsosDSP | github.com/JorenSix/TarsosDSP | BeatRoot algorithm (Simon Dixon), percussion onset | Pure Java |
| Essentia | essentia.upf.edu | RhythmExtractor2013, key detection, chord recognition | Heavy (~50MB) |

### Best Practices for Mobile
- Convert to mono 22050Hz before analysis (4x less computation)
- Use 30+ seconds for reliable BPM (avoids octave errors)
- Post-process with median filtering for tempo changes

### Recommendation
- **aubio via NDK** — best accuracy, prebuilt Android module (github.com/adamski/aubio-android)
- Feature: "Snap cuts to beats"

---

## 5. Speech-to-Text / Auto-Captions

### Current State
- Whisper via ONNX Runtime. No KV-cache (O(n^2) per chunk).

### Open Source Projects

| Project | URL | Speed (Android) | Languages | Model Size |
|---------|-----|-----------------|-----------|------------|
| Sherpa-ONNX | github.com/k2-fsa/sherpa-onnx | **27 tok/s** (Whisper Tiny) | 99 languages | ~100MB |
| Moonshine (via Sherpa) | same | **42 tok/s** | English only | ~125MB |
| Vosk | github.com/alphacep/vosk-api | Streaming | 20+ languages | 50MB-2GB |
| whisper.cpp | github.com/ggml-org/whisper.cpp | 0.55 tok/s (**51x slower**) | 99 languages | ~75MB |

### Recommendation
- **Replace current Whisper with Sherpa-ONNX** — 51x faster on same model
- Android SDK with Kotlin bindings, word-level timestamps
- Moonshine Tiny for English-only (fastest), Whisper Tiny multilingual for international

---

## 6. Color Grading & LUTs

### Current State
- Lift/Gamma/Gain wheels, basic HSL, LUT import with file picker

### Open Source Projects

#### OpenColorIO (github.com/AcademySoftwareFoundation/OpenColorIO)
- ACES pipeline, tetrahedral 3D LUT interpolation, GPU shader code generators.
- Produces optimized GLSL for real-time LUT application.

#### Filmic Tonemapping (github.com/johnhable/fw-public)
- S-curve tone mapping (Uncharted 2). ~10 lines GLSL. HDR→SDR display mapping.

### Improvements
- Tetrahedral interpolation for higher quality LUT application
- GPU-accelerated waveform/vectorscope via compute shaders (ES 3.1)
- Filmic tone mapping as creative preset
- HDR grading support (Android 13+)

---

## 7. Shader Effects & Transitions

### Current State
- Custom GLSL effects. Blend modes composite against mid-gray. 3x3 box blur.

### Open Source Projects

#### gl-transitions (github.com/gl-transitions/gl-transitions)
- **80+ GLSL transition shaders** with standardized interface.
- `vec4 transition(vec2 uv)` with `progress` uniform (0→1).
- Includes: page curl, morph dissolve, pixelation, kaleidoscope, directional wipe, dreamy zoom.
- **Direct Media3 GlEffect compatibility.**

#### GPUImage Android (github.com/cats-oss/android-gpuimage)
- 100+ filter shaders: bilateral (skin smoothing), Kuwahara (oil paint), halftone, sketch, toon, vignette, color matrix.

#### Shadertoy Effects
- Film grain: `fract(sin(dot(uv, vec2(12.9898, 78.233))) * 43758.5453)` + blue noise
- VHS: scanlines + chroma bleeding + tracking distortion
- Glitch: RGB channel split + block corruption
- Lens flare: procedural ghost images with chromatic aberration
- Light leaks: pre-rendered textures with additive blend

### Improvements
- Drop in gl-transitions (80+ instant transitions)
- Two-pass separable Gaussian blur
- Film grain, VHS, glitch, light leak effects (20-50 lines GLSL each)

---

## 8. Video Stabilization

### Current State
- Basic frame differencing, analyzed up to 2 minutes

### Open Source Projects

| Project | URL | Technique | Speed (Mobile) |
|---------|-----|-----------|----------------|
| OpenCV Android | opencv.org | ORB + Lucas-Kanade sparse optical flow + RANSAC + Kalman | ~5-10ms/frame |
| vid.stab | github.com/georgmartius/vid.stab | Block matching + trajectory smoothing (Gaussian kernel) | ~3K lines C, NDK |

### Recommendation
- OpenCV L-K + Kalman (best accuracy/performance tradeoff)
- Process offline during import, apply in real-time via GPU affine transform
- Crop 10-15% for borders

---

## 9. Object Segmentation & Background Removal

### Current State
- MediaPipe Selfie Segmentation with full-res GPU readback (performance issue)

### Open Source Projects

| Project | URL | Quality | Speed | Model Size |
|---------|-----|---------|-------|------------|
| MediaPipe Selfie | developers.google.com/mediapipe | Binary mask | ~30fps @ 256x256 | 1-7MB |
| RobustVideoMatting | github.com/PeterL1n/RobustVideoMatting | True alpha, hair detail, temporal coherent | ~15-20fps @ 512x288 | ~15MB |
| MobileSAM | github.com/ChaoningZhang/MobileSAM | Tap-to-segment any object | ~200ms/frame | ~10MB |

### Recommendation
- Keep MediaPipe for real-time (fix readback to downsample)
- Add RVM for AI green screen quality
- Add MobileSAM for tap-to-select

---

## 10. Chroma Key

### Current State
- Basic similarity/smoothness/spill parameters in RGB/HSV

### Professional Techniques

- **YCbCr distance keying** — better than RGB/HSV (decorrelates luminance from chrominance)
- **Spill suppression:** `pixel.g -= max(0, pixel.g - max(pixel.r, pixel.b) * balance)`
- **Edge refinement:** Erode 1px → blur alpha 0.1-0.9 zone
- **Clean plate keying:** Sample bg-only frame, key = `abs(pixel - cleanPlate)`

### Sources: FFmpeg `vf_chromakey.c`, OBS `color-key-filter.c`

---

## 11. AI Frame Interpolation

### Current State
- Stub (toast only)

### Open Source Projects

| Project | URL | Speed (Android) | Model Size |
|---------|-----|-----------------|------------|
| RIFE v4.6 | github.com/hzwer/ECCV2022-RIFE | 720p: 100ms (NCNN+Vulkan) | ~7-10MB |
| IFRNet | github.com/ltkong218/IFRNet | Comparable via NCNN-Vulkan | ~8MB |

### Recommendation
- RIFE v4.6 via NCNN+Vulkan — proven Android implementation (Jan 2026)
- Export-time slow-motion (24→60/120fps)

---

## 12. AI Object Removal / Inpainting

### Current State
- Stub (toast only)

### Open Source Projects

| Project | URL | Speed | On-Device? |
|---------|-----|-------|------------|
| LaMa-Dilated | github.com/advimman/lama | **40ms/frame @ 512x512** (Galaxy S25) | Qualcomm AI Hub |
| ProPainter | github.com/sczhou/ProPainter | Server-speed | Cloud only |

---

## 13. Smart Reframing

### Current State
- Basic aspect ratio change, no subject tracking

### Approach
- MediaPipe Face Detection (BlazeFace ~400KB, <1ms) + BlazePose (~3-8MB)
- Detect faces/poses → saliency-weighted crop → smooth trajectory (EMA)
- Replicates YouTube Shorts / Instagram Reels auto-crop

---

## 14. AI Upscaling

### Current State
- Not implemented

| Project | URL | Speed (Android) | Model Size |
|---------|-----|-----------------|------------|
| Real-ESRGAN x4plus | github.com/xinntao/Real-ESRGAN | 72ms/frame (Qualcomm AI Hub) | ~17MB |

---

## 15. Style Transfer / AI Filters

### Current State
- Not implemented

| Project | URL | Model Size | Real-Time? |
|---------|-----|------------|------------|
| AnimeGANv2 | github.com/TachibanaYoshino/AnimeGANv2 | **8.6MB** | Yes |
| Fast Neural Style Transfer | github.com/yakhyo/fast-neural-style-transfer | 6-7MB/style | Yes |

---

## 16. Text-to-Speech

### Current State
- Android system TTS

| Project | URL | Quality | Speed | Model Size |
|---------|-----|---------|-------|------------|
| Piper via Sherpa-ONNX | github.com/rhasspy/piper | Near-human (VITS) | 20-30ms | 15-65MB/voice |
| eSpeak NG | github.com/espeak-ng/espeak-ng | Robotic (formant) | Instant | ~2MB |

### Recommendation
- Piper via Sherpa-ONNX — 50+ languages, offline, production-proven on Android

---

## 17. Motion Graphics & Titles

### Current State
- Basic text overlays with font/size/color

| Project | URL | Technique |
|---------|-----|-----------|
| Lottie | github.com/airbnb/lottie-android | After Effects animations as JSON. Render via LottieDrawable → export via Media3 |
| dotLottie | dotlottie.io | Compressed bundles with theming + state machines |
| Rive | github.com/rive-app/rive-android | Interactive animations, 120fps renderer, state machines |

---

## 18. Export & Encoding

### Current State
- Media3 Transformer, MP4/H.264, batch export, EDL/FCPXML

| Project | URL | Improvement |
|---------|-----|-------------|
| FFmpegX-Android | github.com/mzgs/FFmpegX-Android | Fallback encoder, 300+ filters. Replaces archived ffmpeg-kit |
| SVT-AV1 | github.com/AOMediaCodec/SVT-AV1 | AV1 encoding, 30-50% bandwidth savings over HEVC |
| libass | github.com/libass/libass | Burned-in subtitle rendering with full ASS/SSA styling |

### Social Media Export Presets
| Platform | Resolution | Bitrate | FPS | Aspect |
|----------|------------|---------|-----|--------|
| YouTube | 1920x1080 | 8 Mbps | 24-60 | 16:9 |
| TikTok | 1080x1920 | 8-15 Mbps VBR | 30 | 9:16 |
| Instagram Reels | 1080x1920 | 5-8 Mbps | 30 max | 9:16 |

---

## 19. Project Management

| Project | URL | Improvement |
|---------|-----|-------------|
| Protocol Buffers | protobuf.dev | Binary format — 3-10x smaller, 20-100x faster than JSON |
| OpenTimelineIO | github.com/AcademySoftwareFoundation/OpenTimelineIO | Timeline interchange with Java bindings |

---

## 20. Proxy Workflow

### Professional Patterns (DaVinci Resolve / Premiere)
- 3-tier: thumbnail → proxy (540p H.264 CRF 28) → original
- Background generation via WorkManager + ForegroundService
- Auto-switch proxy (preview) vs original (export)
