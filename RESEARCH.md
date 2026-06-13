# Research - NovaCut

## Executive Summary

NovaCut is the most feature-complete open-source Android video editor available: Kotlin/Compose/Media3 1.10, 29 injectable engine singletons, 40+ GLSL effects, 37 transitions, Whisper ASR, MediaPipe segmentation, LaMa inpainting, Cut Assistant, multicam sync, OTIO/FCPXML interchange, managed-media durability, and deep trust surfaces (C2PA draft, privacy dashboard, model gates, diagnostic ZIPs). After 29 prior research cycles and 200+ commits, the roadmap is mature and the active direction is adoption/hardening, not feature discovery.

This research pass focuses on competitive signals, community pain points, and platform shifts that the existing 29 cycles did not cover. The highest-value new opportunities are:

1. **Persistent waveform cache** -- disk-backed waveform data eliminates the main re-open latency for large projects, the most-cited mobile editor performance complaint after overlay lag.
2. **In-editor export playback** -- letting users preview the finished output inside NovaCut before share/save closes the "did it export right?" anxiety loop that drives community complaints across CapCut, VN, and Adobe Rush.
3. **Split-screen before/after for grading** -- table-stakes in desktop NLEs (Resolve, Premiere) and now appearing in CapCut Pro; absent in all mobile OSS editors.
4. **ffmpeg-kt evaluation** -- the Kotlin Multiplatform FFmpeg wrapper is the leading active replacement for the retired FFmpegKit; NovaCut's current dependency is a personal fork of an archived project.
5. **Haptic timeline scrub enrichment** -- fine-grained haptic feedback during scrub (intensifying at clip edges, markers, beat markers) differentiates from every competitor; pairs with the existing haptic snap system.
6. **Auto-suggested export settings** -- analyze timeline content (resolution mix, duration, codec sources) and recommend optimal export configuration; no competitor does this beyond basic platform presets.

## Product Map

- Core workflows: import (Photo Picker, SAF, share intents, document intents, camera handoff) -> manage media (health, relink, asset manifest) -> edit multi-track timeline (video/audio/overlay/text/adjustment) -> apply effects/transitions/AI tools -> export/share/save (Media3 Transformer, FFmpeg fallback, GIF encoder, frame capture).
- User personas: mobile-first creators leaving CapCut for privacy/cost/ban reasons; FOSS/privacy advocates; prosumers needing desktop NLE interchange (OTIO/FCPXML); content teams needing no-watermark batch export.
- Platforms: Android minSdk 26 / target 36 / compile 36. Distribution: GitHub Releases (primary), Play Store (metadata ready, listing pending), F-Droid (Fastlane metadata present, reproducible-build policy pending).
- Key data flows: Room DB + JSON autosave, managed-media asset sidecars, model downloads with SHA-256 verification, C2PA draft manifests, AI usage ledger, diagnostic ZIPs.

## Competitive Landscape

### CapCut
- Does well: fastest auto-captions (on-device), AI background removal, massive template library, style transfer presets, TikTok-native export.
- Learn from: on-device caption speed (motivates Sherpa-ONNX upgrade), one-tap platform presets with safe-zone previews, template marketplace ecosystem.
- Avoid: cloud lock-in, $19.99/mo Pro pricing backlash, ban-risk regulatory exposure, watermark pressure on free tier.
- Source: https://www.eesel.ai/blog/capcut-alternatives

### VN Video Editor
- Does well: free with no watermark, multi-track keyframe timeline, speed ramping, broadly praised on Reddit.
- Learn from: VN's most-cited 2026 complaint is overlay lag and sync drift with many layers -- NovaCut's loaded-timeline benchmark and post-export verification address this directly.
- Avoid: VN's lack of project repair/relink surfaces; its "impossible to properly sync overlays" reports.
- Sources: https://codecarbon.com/top-5-free-capcut-alternatives-for-mobile-creators-reddit-picks/, https://nothing.community/d/8047-video-editing-lag-with-vn-editor-and-inshot

### Meta Edits
- Does well: SAM-based per-object segmentation with per-object effects, "Restyle" AI video regeneration, storyboard/teleprompter workflow, beat markers, free positioning.
- Learn from: storyboard-first planning (already roadmapped), SAM object selection UX (already roadmapped as tap-to-segment), beat marker integration in timeline.
- Avoid: cloud-dependent AI (Restyle/Lip Sync need server); Meta account lock-in.
- Source: https://www.inro.social/blog/edits-new-meta-app

### KineMaster
- Does well: layer-based editing, font import, asset store, precise layer control.
- Learn from: per-layer editing precision; custom font import (NovaCut already has this).
- Avoid: watermark on free tier, subscription model friction.
- Source: https://filmora.wondershare.com/video-editor-review/kinemaster-app.html

### PowerDirector Mobile
- Does well: AI text-to-video, anime style transfer, multilingual video generation, strong AI feature marketing.
- Learn from: AI feature discoverability and suggestion-driven workflow.
- Avoid: heavy AI marketing that overpromises; freemium friction.
- Source: https://www.cyberlink.com/products/powerdirector-video-editing-software/overview_en_US.html

### LumaFusion (Android)
- Does well: professional mobile editing, speed ramping, enhanced keyframing, track height adjustment.
- NovaCut advantage: LumaFusion Android lacks multicam and FCPXML export (iOS-only features). NovaCut already has both, plus OTIO.
- Avoid: subscription fatigue (Creator Pass model).
- Source: https://luma-touch.com/multicam-for-lumafusion/

### Open Video Editor
- Does well: minimal, privacy-first, F-Droid published, Media3/Compose-based.
- Learn from: its ffmpeg-kit breakage is a direct warning for NovaCut's fork dependency (already addressed in roadmap dependency verification item).
- Avoid: its narrow scope limits user value; NovaCut should maintain breadth.
- Source: https://github.com/devhyper/open-video-editor

### LibreCuts
- New OSS competitor (2025+), Kotlin/Compose, simplicity-focused. Too early to evaluate feature depth.
- Source: https://github.com/tharunbirla/LibreCuts

## Security, Privacy, and Reliability

- **FFmpegKit fork risk**: upstream retired Jan 2025, binaries deleted from Maven Central Apr 2025, repo archived Jun 2025. NovaCut depends on `com.moizhassan.ffmpeg:ffmpeg-kit-16kb:6.1.1`, a personal fork. The Kotlin Multiplatform `ffmpeg-kt` project is the most actively developed replacement and should be evaluated. Already addressed in roadmap dependency verification item; ffmpeg-kt evaluation is new.
- **C2PA library availability**: `c2pa-android` official AAR (0.0.9) exists as a GitHub release asset with Android Keystore/StrongBox support. JitPack coordinates still unreliable (HTTP 500). Simple C2PA (ProofMode) offers a lighter alternative for JPEG/MP4. Already tracked in Cycle 13.
- **Google developer verification**: enforcement starts September 2026 in Brazil, Indonesia, Singapore, Thailand; all regions from 2027. Requires government ID, $25 fee, and signed APK package-name registration. F-Droid formally objected. Already tracked in Cycle 27.
- **Android 17 foreground-service audio**: blocks audio APIs from background-started FGS. NovaCut's export uses Transformer (not direct audio APIs), but TTS preview and voiceover recording need visible-UI constraints. Already tracked in Cycle 28.
- **Export trust**: Adobe Rush and VN both have widespread export crash/corruption reports in 2025-2026 community forums. NovaCut's post-export verification gate (already roadmapped) directly addresses this class of failure.

## Architecture Assessment

- **Waveform extraction latency**: `AudioEngine.extractWaveform()` runs on every project open for every clip. The 64-entry LRU cache is in-memory only. Persisting waveform data to disk (keyed by source URI + content fingerprint) would eliminate the dominant reopen cost for projects with many audio clips.
- **Export playback gap**: after Transformer/FFmpeg completion, the UI transitions directly to share/save actions. No in-app playback of the output file exists. Users must leave NovaCut to verify the result. Adding an ExoPlayer-backed playback surface in the export completion state would close this trust gap.
- **Effects comparison**: no split-screen or before/after comparison exists for effects or color grading. `PreviewPanel.kt` renders only the effected frame. A togglable split (left=original, right=graded) using the existing player infrastructure would match desktop NLE expectations.
- **ffmpeg-kt**: actively developed Kotlin Multiplatform FFmpeg wrapper (`ffmpeg-kt`) has emerged as the community-preferred replacement for the retired FFmpegKit. NovaCut should evaluate it as a potential replacement for the personal fork, considering: API compatibility, 16KB page-size compliance, license compliance, and native library size.
- **Compose performance**: Strong Skipping is enabled by default since Kotlin 2.0.20. NovaCut's Kotlin 2.1.0 already benefits, but the domain-slice decomposition effectiveness has never been measured with Compose compiler metrics. Already tracked in roadmap.

## Rejected Ideas

- **Cloud-based AI features** (Restyle, Lip Sync, generative video): rejected per NovaCut's local-first philosophy and existing deferred-items policy. Source: Meta Edits 2026 features.
- **In-app asset marketplace**: rejected; stock-provider trust and licensing complexity is already a separate risk lane in Cycle 17. Source: KineMaster/CapCut asset stores.
- **Subscription-first monetization**: rejected as primary model; freemium evaluation already tracked in Cycle 27. Source: CapCut Pro pricing backlash, LumaFusion Creator Pass.
- **Full Photoshop-style branching undo**: rejected; visual undo history panel already shipped (v2.5.0). The jump-to-state UX is sufficient for mobile. Source: Photoshop History Panel UX research.
- **Re-add items already in roadmap**: timeline snapping, Sherpa-ONNX, stabilization, frame interpolation, upscaling, neural TTS, style transfer, proxy workflow, WCAG audit, caption translation, project sync, rights ledger, and 40+ other items are already tracked across 29 research cycles. Not duplicated.
- **Live broadcasting / streaming**: rejected per existing deferred-items policy. Source: Blackmagic Camera, LumaFusion multicam.
- **Desktop/web companion app**: rejected; out of NovaCut's Android-first scope. Source: CapCut/PowerDirector cross-platform.

## Sources

Project and platform:
- https://developer.android.com/about/versions/17/behavior-changes-17
- https://developer.android.com/about/versions/16/behavior-changes-16
- https://developer.android.com/develop/ui/compose/performance/stability/strongskipping
- https://android-developers.googleblog.com/2026/03/media3-110-is-out.html
- https://developer.android.com/jetpack/androidx/releases/media3
- https://developer.android.com/privacy-and-security/developer-verification

Competitors and community:
- https://www.eesel.ai/blog/capcut-alternatives
- https://codecarbon.com/top-5-free-capcut-alternatives-for-mobile-creators-reddit-picks/
- https://www.inro.social/blog/edits-new-meta-app
- https://www.socialmediatoday.com/news/meta-adds-new-features-to-edits-including-ai-segmentation-of-objects/808174/
- https://filmora.wondershare.com/video-editor-review/kinemaster-app.html
- https://www.cyberlink.com/products/powerdirector-video-editing-software/overview_en_US.html
- https://luma-touch.com/multicam-for-lumafusion/
- https://nothing.community/d/8047-video-editing-lag-with-vn-editor-and-inshot
- https://github.com/devhyper/open-video-editor
- https://github.com/tharunbirla/LibreCuts

AI/ML and engines:
- https://voiceping.net/en/blog/research-offline-speech-transcription-benchmark/
- https://k2-fsa.github.io/sherpa/onnx/index.html
- https://modelslab.com/blog/audio-generation/moonshine-vs-whisper-asr-real-time-speech-2026
- https://github.com/IbrahimGhadre/realesrgan-mobile
- https://allenkuo.medium.com/gpu-resident-frame-interpolation-on-android-e9558d19cfab
- https://opensource.contentauthenticity.org/docs/c2pa-android/
- https://proofmode.org/blog/simple-c2pa

Standards and dependencies:
- https://opentimelineio.readthedocs.io/en/latest/
- https://proandroiddev.com/ffmpeg-kit-16-kb-page-size-in-android-d522adc5efa2
- https://www.itpathsolutions.com/ffmpegkit-shutdown-what-to-do-next
- https://zeely.ai/blog/tiktok-safe-zones/
- https://kreatli.com/guides/safe-zone-guide
- https://www.androidauthority.com/f-droid-google-developer-verification-rules-warning-3601860/

## Open Questions

- Should NovaCut evaluate `ffmpeg-kt` as a drop-in FFmpegKit replacement, or vendor the existing fork AAR with checksum pinning and treat migration as a future toolchain item?
- What is the acceptable file-size overhead for persisting waveform data to disk (raw PCM samples vs. downsampled peaks) per clip, and should it be bounded per project?
