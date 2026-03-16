package com.novacut.editor.model

import android.net.Uri
import androidx.room.*
import java.util.UUID

@Entity(tableName = "projects")
data class Project(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled",
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val frameRate: Int = 30,
    val resolution: Resolution = Resolution.FHD_1080P,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val durationMs: Long = 0L
)

enum class AspectRatio(val widthRatio: Int, val heightRatio: Int, val label: String) {
    RATIO_16_9(16, 9, "16:9"),
    RATIO_9_16(9, 16, "9:16"),
    RATIO_1_1(1, 1, "1:1"),
    RATIO_4_3(4, 3, "4:3"),
    RATIO_3_4(3, 4, "3:4"),
    RATIO_21_9(21, 9, "21:9");

    fun toFloat(): Float = widthRatio.toFloat() / heightRatio.toFloat()
}

enum class Resolution(val width: Int, val height: Int, val label: String) {
    SD_480P(854, 480, "480p"),
    HD_720P(1280, 720, "720p"),
    FHD_1080P(1920, 1080, "1080p"),
    QHD_1440P(2560, 1440, "1440p"),
    UHD_4K(3840, 2160, "4K");

    fun forAspect(aspect: AspectRatio): Pair<Int, Int> {
        val h = height
        val w = (h * aspect.toFloat()).toInt().let { it - (it % 2) }
        return w to h
    }
}

enum class TrackType { VIDEO, AUDIO, OVERLAY, TEXT }

data class Track(
    val id: String = UUID.randomUUID().toString(),
    val type: TrackType,
    val index: Int,
    val clips: List<Clip> = emptyList(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true,
    val isMuted: Boolean = false
)

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: Uri,
    val sourceDurationMs: Long,
    val timelineStartMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val effects: List<Effect> = emptyList(),
    val transition: Transition? = null,
    val volume: Float = 1.0f,
    val speed: Float = 1.0f,
    val isReversed: Boolean = false,
    val opacity: Float = 1.0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val positionX: Float = 0f,
    val positionY: Float = 0f,
    val keyframes: List<Keyframe> = emptyList()
) {
    val durationMs: Long get() = ((trimEndMs - trimStartMs) / speed).toLong()
    val timelineEndMs: Long get() = timelineStartMs + durationMs
}

data class Effect(
    val id: String = UUID.randomUUID().toString(),
    val type: EffectType,
    val params: Map<String, Float> = emptyMap(),
    val enabled: Boolean = true
)

enum class EffectType(val displayName: String, val category: EffectCategory) {
    // Color
    BRIGHTNESS("Brightness", EffectCategory.COLOR),
    CONTRAST("Contrast", EffectCategory.COLOR),
    SATURATION("Saturation", EffectCategory.COLOR),
    TEMPERATURE("Temperature", EffectCategory.COLOR),
    TINT("Tint", EffectCategory.COLOR),
    EXPOSURE("Exposure", EffectCategory.COLOR),
    GAMMA("Gamma", EffectCategory.COLOR),
    HIGHLIGHTS("Highlights", EffectCategory.COLOR),
    SHADOWS("Shadows", EffectCategory.COLOR),
    VIBRANCE("Vibrance", EffectCategory.COLOR),

    // Filters
    GRAYSCALE("Grayscale", EffectCategory.FILTER),
    SEPIA("Sepia", EffectCategory.FILTER),
    INVERT("Invert", EffectCategory.FILTER),
    POSTERIZE("Posterize", EffectCategory.FILTER),
    VIGNETTE("Vignette", EffectCategory.FILTER),
    SHARPEN("Sharpen", EffectCategory.FILTER),
    FILM_GRAIN("Film Grain", EffectCategory.FILTER),
    VINTAGE("Vintage", EffectCategory.FILTER),
    COOL_TONE("Cool Tone", EffectCategory.FILTER),
    WARM_TONE("Warm Tone", EffectCategory.FILTER),
    CYBERPUNK("Cyberpunk", EffectCategory.FILTER),
    NOIR("Noir", EffectCategory.FILTER),

    // Blur
    GAUSSIAN_BLUR("Gaussian Blur", EffectCategory.BLUR),
    RADIAL_BLUR("Radial Blur", EffectCategory.BLUR),
    MOTION_BLUR("Motion Blur", EffectCategory.BLUR),
    TILT_SHIFT("Tilt Shift", EffectCategory.BLUR),
    MOSAIC("Mosaic", EffectCategory.BLUR),

    // Distortion
    FISHEYE("Fisheye", EffectCategory.DISTORTION),
    MIRROR("Mirror", EffectCategory.DISTORTION),
    GLITCH("Glitch", EffectCategory.DISTORTION),
    PIXELATE("Pixelate", EffectCategory.DISTORTION),
    WAVE("Wave", EffectCategory.DISTORTION),
    CHROMATIC_ABERRATION("Chromatic Aberration", EffectCategory.DISTORTION),

    // Keying
    CHROMA_KEY("Chroma Key", EffectCategory.KEYING),

    // Speed
    SPEED("Speed", EffectCategory.SPEED),
    REVERSE("Reverse", EffectCategory.SPEED)
}

enum class EffectCategory(val displayName: String) {
    COLOR("Color"),
    FILTER("Filters"),
    BLUR("Blur"),
    DISTORTION("Distortion"),
    KEYING("Keying"),
    SPEED("Speed")
}

data class Transition(
    val type: TransitionType,
    val durationMs: Long = 500L
)

enum class TransitionType(val displayName: String) {
    DISSOLVE("Dissolve"),
    FADE_BLACK("Fade to Black"),
    FADE_WHITE("Fade to White"),
    WIPE_LEFT("Wipe Left"),
    WIPE_RIGHT("Wipe Right"),
    WIPE_UP("Wipe Up"),
    WIPE_DOWN("Wipe Down"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    SPIN("Spin"),
    FLIP("Flip"),
    CUBE("Cube"),
    RIPPLE("Ripple"),
    PIXELATE("Pixelate"),
    DIRECTIONAL_WARP("Directional Warp"),
    WIND("Wind"),
    MORPH("Morph"),
    GLITCH("Glitch"),
    CIRCLE_OPEN("Circle Open"),
    CROSS_ZOOM("Cross Zoom"),
    DREAMY("Dreamy"),
    HEART("Heart"),
    SWIRL("Swirl")
}

data class Keyframe(
    val timeOffsetMs: Long,
    val property: KeyframeProperty,
    val value: Float,
    val easing: Easing = Easing.LINEAR
)

enum class KeyframeProperty {
    POSITION_X, POSITION_Y, SCALE_X, SCALE_Y, ROTATION, OPACITY, VOLUME
}

enum class Easing {
    LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, SPRING
}

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontFamily: String = "sans-serif",
    val fontSize: Float = 48f,
    val color: Long = 0xFFFFFFFF,
    val backgroundColor: Long = 0x00000000,
    val strokeColor: Long = 0xFF000000,
    val strokeWidth: Float = 0f,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 3000L,
    val animationIn: TextAnimation = TextAnimation.NONE,
    val animationOut: TextAnimation = TextAnimation.NONE
)

enum class TextAlignment { LEFT, CENTER, RIGHT }

enum class TextAnimation(val displayName: String) {
    NONE("None"),
    FADE("Fade"),
    SLIDE_UP("Slide Up"),
    SLIDE_DOWN("Slide Down"),
    SLIDE_LEFT("Slide Left"),
    SLIDE_RIGHT("Slide Right"),
    SCALE("Scale"),
    TYPEWRITER("Typewriter"),
    BOUNCE("Bounce"),
    SPIN("Spin")
}

data class ExportConfig(
    val resolution: Resolution = Resolution.FHD_1080P,
    val frameRate: Int = 30,
    val codec: VideoCodec = VideoCodec.H264,
    val quality: ExportQuality = ExportQuality.HIGH,
    val audioCodec: AudioCodec = AudioCodec.AAC,
    val audioBitrate: Int = 256_000
) {
    val videoBitrate: Int get() = when (resolution) {
        Resolution.SD_480P -> when (quality) {
            ExportQuality.LOW -> 2_000_000
            ExportQuality.MEDIUM -> 4_000_000
            ExportQuality.HIGH -> 6_000_000
        }
        Resolution.HD_720P -> when (quality) {
            ExportQuality.LOW -> 4_000_000
            ExportQuality.MEDIUM -> 6_000_000
            ExportQuality.HIGH -> 10_000_000
        }
        Resolution.FHD_1080P -> when (quality) {
            ExportQuality.LOW -> 6_000_000
            ExportQuality.MEDIUM -> 12_000_000
            ExportQuality.HIGH -> 20_000_000
        }
        Resolution.QHD_1440P -> when (quality) {
            ExportQuality.LOW -> 12_000_000
            ExportQuality.MEDIUM -> 25_000_000
            ExportQuality.HIGH -> 40_000_000
        }
        Resolution.UHD_4K -> when (quality) {
            ExportQuality.LOW -> 25_000_000
            ExportQuality.MEDIUM -> 50_000_000
            ExportQuality.HIGH -> 80_000_000
        }
    }
}

enum class VideoCodec(val mimeType: String, val label: String) {
    H264("video/avc", "H.264"),
    HEVC("video/hevc", "H.265/HEVC")
}

enum class AudioCodec(val mimeType: String, val label: String) {
    AAC("audio/mp4a-latm", "AAC")
}

enum class ExportQuality(val label: String) {
    LOW("Small File"),
    MEDIUM("Balanced"),
    HIGH("Best Quality")
}
