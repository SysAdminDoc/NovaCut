# Dependency Maintenance

Last reviewed: 2026-06-04.

This note records the dependency-maintenance policy behind `.github/dependabot.yml`
and the v3.74.43 freshness batch. `ROADMAP.md` remains the source of truth for
open work; this file only captures package-review decisions.

## Freshness Batch

| Lane | Current decision | Source checked |
|---|---|---|
| Compose BOM | Update from `2026.05.00` to `2026.05.01`. | Google Maven metadata for `androidx.compose:compose-bom`. |
| Room | Hold at `2.7.2`; `2.8.4` remains queued for a toolchain validation pass. | Google Maven metadata for `androidx.room:room-runtime`. |
| Kotlin Gradle plugin | Hold at `2.1.0`; Kotlin 2.1.21 and 2.3+/2.4+ remain queued for a toolchain validation pass. | Gradle Plugin Portal metadata for `org.jetbrains.kotlin.android`. |
| KSP | Hold at `2.1.0-1.0.29`; KSP `2.3.9` requires an AGP API missing from the AGP 8.7.3 baseline, and the compatible 2.1 maintenance-line compile could not complete on this host before OpenJDK was killed. | Gradle Plugin Portal metadata for `com.google.devtools.ksp`. |
| AGP | Keep `8.7.3` out of this routine batch. | Google Maven metadata shows newer stable and preview AGP trains, which are a separate toolchain migration risk. |

## Dependabot Grouping

Routine Gradle updates are split by likely review and breakage profile:

- `gradle-toolchain` for AGP, Kotlin, KSP, and wrapper/runtime build tool moves.
- `androidx-ui-compose` for Compose BOM and Compose UI artifacts.
- `androidx-runtime` for Activity, Lifecycle, Navigation, Room, DataStore, Work,
  Core, Window, and AndroidX Hilt runtime libraries.
- `media3-runtime` for Media3 playback, Transformer, effects, and muxer.
- `ml-native` for ONNX Runtime, MediaPipe, FFmpeg, and DeepFilterNet.
- `network-and-media-adornments` for OkHttp, Coil, and Lottie.
- `test-support` for JUnit, AndroidX Test, Benchmark, ProfileInstaller, and UI
  Automator support libraries.

## Changelog Tagging Evaluation

Dependabot's configuration supports static labels, scoped commit messages, and
groups, but it does not derive dynamic release tags from `CHANGELOG.md` headings.
NovaCut should not create release tags from dependency PRs automatically.

The existing release path already verifies that the GitHub tag matches
`versionName` and that the first `CHANGELOG.md` release heading matches the
Gradle version. Keep changelog-derived tagging as an explicit release workflow
decision, not a Dependabot feature.

## Source URLs

- https://dl.google.com/dl/android/maven2/androidx/compose/compose-bom/maven-metadata.xml
- https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/maven-metadata.xml
- https://plugins.gradle.org/m2/org/jetbrains/kotlin/android/org.jetbrains.kotlin.android.gradle.plugin/maven-metadata.xml
- https://plugins.gradle.org/m2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/maven-metadata.xml
- https://dl.google.com/dl/android/maven2/com/android/application/com.android.application.gradle.plugin/maven-metadata.xml
