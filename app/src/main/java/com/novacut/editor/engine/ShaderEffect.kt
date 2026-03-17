package com.novacut.editor.engine

import android.content.Context
import android.opengl.GLES30
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Custom GLSL fragment shader effect for Media3 Transformer export.
 * Wraps a GLES 3.0 fragment shader into a GlEffect with fullscreen quad rendering.
 */
@UnstableApi
class ShaderEffect(
    private val fragmentShader: String,
    private val uniforms: Map<String, Float> = emptyMap()
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return ShaderProgram(fragmentShader, uniforms, useHdr)
    }
}

@UnstableApi
private class ShaderProgram(
    private val fragmentShaderSource: String,
    private val uniforms: Map<String, Float>,
    useHdr: Boolean
) : BaseGlShaderProgram(useHdr, 1) {

    private var glProgram = 0
    private var vao = 0
    private var vbo = 0
    private var width = 0
    private var height = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        width = inputWidth
        height = inputHeight
        if (glProgram == 0) setupGl()
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        GLES30.glUseProgram(glProgram)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, inputTexId)
        uniform1i("uTexSampler", 0)
        uniform2f("uResolution", width.toFloat(), height.toFloat())
        uniform1f("uTime", presentationTimeUs / 1_000_000f)
        for ((name, value) in uniforms) uniform1f(name, value)
        GLES30.glBindVertexArray(vao)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glBindVertexArray(0)
    }

    override fun release() {
        super.release()
        if (glProgram != 0) { GLES30.glDeleteProgram(glProgram); glProgram = 0 }
        if (vao != 0) { GLES30.glDeleteVertexArrays(1, intArrayOf(vao), 0); vao = 0 }
        if (vbo != 0) { GLES30.glDeleteBuffers(1, intArrayOf(vbo), 0); vbo = 0 }
    }

    private fun setupGl() {
        val vs = compile(GLES30.GL_VERTEX_SHADER, VERT)
        val fs = compile(GLES30.GL_FRAGMENT_SHADER, fragmentShaderSource)
        glProgram = GLES30.glCreateProgram()
        GLES30.glAttachShader(glProgram, vs)
        GLES30.glAttachShader(glProgram, fs)
        GLES30.glLinkProgram(glProgram)
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        val s = IntArray(1)
        GLES30.glGetProgramiv(glProgram, GLES30.GL_LINK_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(glProgram)
            GLES30.glDeleteProgram(glProgram); glProgram = 0
            throw RuntimeException("GL program link failed: $log")
        }
        val vaos = IntArray(1); GLES30.glGenVertexArrays(1, vaos, 0); vao = vaos[0]
        val vbos = IntArray(1); GLES30.glGenBuffers(1, vbos, 0); vbo = vbos[0]
        val quad = floatArrayOf(-1f,-1f,0f,0f, 1f,-1f,1f,0f, -1f,1f,0f,1f, 1f,1f,1f,1f)
        val buf = ByteBuffer.allocateDirect(quad.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(quad).apply { position(0) }
        GLES30.glBindVertexArray(vao)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, quad.size * 4, buf, GLES30.GL_STATIC_DRAW)
        val p = GLES30.glGetAttribLocation(glProgram, "aPosition")
        GLES30.glEnableVertexAttribArray(p)
        GLES30.glVertexAttribPointer(p, 2, GLES30.GL_FLOAT, false, 16, 0)
        val t = GLES30.glGetAttribLocation(glProgram, "aTexCoord")
        GLES30.glEnableVertexAttribArray(t)
        GLES30.glVertexAttribPointer(t, 2, GLES30.GL_FLOAT, false, 16, 8)
        GLES30.glBindVertexArray(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, 0)
    }

    private fun compile(type: Int, src: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val s = IntArray(1); GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, s, 0)
        if (s[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh); GLES30.glDeleteShader(sh)
            throw RuntimeException("Shader compile failed: $log")
        }
        return sh
    }

    private fun uniform1i(n: String, v: Int) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform1i(l, v)
    }
    private fun uniform1f(n: String, v: Float) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform1f(l, v)
    }
    private fun uniform2f(n: String, a: Float, b: Float) {
        val l = GLES30.glGetUniformLocation(glProgram, n); if (l >= 0) GLES30.glUniform2f(l, a, b)
    }

    companion object {
        private const val VERT = "#version 300 es\n" +
            "in vec4 aPosition;\nin vec2 aTexCoord;\nout vec2 vTexCoord;\n" +
            "void main() { gl_Position = aPosition; vTexCoord = aTexCoord; }"
    }
}

// ─── Factory for all GLSL-based effects ─────────────────────────────────────

@UnstableApi
object EffectShaders {

    fun vignette(intensity: Float, radius: Float) = ShaderEffect(
        FRAG_VIGNETTE, mapOf("uIntensity" to intensity, "uRadius" to radius)
    )

    fun sharpen(strength: Float) = ShaderEffect(
        FRAG_SHARPEN, mapOf("uAmount" to strength)
    )

    fun filmGrain(intensity: Float) = ShaderEffect(
        FRAG_FILM_GRAIN, mapOf("uIntensity" to intensity)
    )

    fun gaussianBlur(radius: Float) = ShaderEffect(
        FRAG_GAUSSIAN_BLUR, mapOf("uRadius" to radius)
    )

    fun radialBlur(intensity: Float) = ShaderEffect(
        FRAG_RADIAL_BLUR, mapOf("uIntensity" to intensity)
    )

    fun motionBlur(intensity: Float, angle: Float = 0f) = ShaderEffect(
        FRAG_MOTION_BLUR, mapOf("uIntensity" to intensity, "uAngle" to angle)
    )

    fun tiltShift(focusY: Float, width: Float, blur: Float) = ShaderEffect(
        FRAG_TILT_SHIFT, mapOf("uFocusY" to focusY, "uWidth" to width, "uBlur" to blur)
    )

    fun mosaic(size: Float) = ShaderEffect(FRAG_MOSAIC, mapOf("uSize" to size))

    fun fisheye(intensity: Float) = ShaderEffect(
        FRAG_FISHEYE, mapOf("uIntensity" to intensity)
    )

    fun glitch(intensity: Float) = ShaderEffect(
        FRAG_GLITCH, mapOf("uIntensity" to intensity)
    )

    fun pixelate(size: Float) = ShaderEffect(FRAG_PIXELATE, mapOf("uSize" to size))

    fun wave(amplitude: Float, frequency: Float) = ShaderEffect(
        FRAG_WAVE, mapOf("uAmplitude" to amplitude, "uFrequency" to frequency)
    )

    fun chromaticAberration(intensity: Float) = ShaderEffect(
        FRAG_CHROMATIC_ABERRATION, mapOf("uIntensity" to intensity)
    )

    fun chromaKey(keyR: Float, keyG: Float, keyB: Float, threshold: Float, smoothing: Float) =
        ShaderEffect(FRAG_CHROMA_KEY, mapOf(
            "uKeyR" to keyR, "uKeyG" to keyG, "uKeyB" to keyB,
            "uThreshold" to threshold, "uSmoothing" to smoothing
        ))

    // ─── Transition shaders (applied to clip start/end) ─────────────────

    fun transitionFadeIn(durationUs: Float, fadeToWhite: Boolean = false) = ShaderEffect(
        if (fadeToWhite) FRAG_FADE_IN_WHITE else FRAG_FADE_IN_BLACK,
        mapOf("uDurationUs" to durationUs)
    )

    fun transitionWipe(durationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_WIPE_IN, mapOf("uDurationUs" to durationUs, "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionZoomIn(durationUs: Float) = ShaderEffect(
        FRAG_ZOOM_IN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionCircleOpen(durationUs: Float) = ShaderEffect(
        FRAG_CIRCLE_OPEN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionZoomOut(durationUs: Float) = ShaderEffect(
        FRAG_ZOOM_OUT, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSpin(durationUs: Float) = ShaderEffect(
        FRAG_SPIN, mapOf("uDurationUs" to durationUs)
    )

    fun transitionFlip(durationUs: Float) = ShaderEffect(
        FRAG_FLIP, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSlideIn(durationUs: Float, dirX: Float, dirY: Float) = ShaderEffect(
        FRAG_SLIDE_IN, mapOf("uDurationUs" to durationUs, "uDirX" to dirX, "uDirY" to dirY)
    )

    fun transitionCube(durationUs: Float) = ShaderEffect(
        FRAG_CUBE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionRipple(durationUs: Float) = ShaderEffect(
        FRAG_RIPPLE, mapOf("uDurationUs" to durationUs)
    )

    fun transitionPixelate(durationUs: Float) = ShaderEffect(
        FRAG_PIXELATE_TRANS, mapOf("uDurationUs" to durationUs)
    )

    fun transitionDirectionalWarp(durationUs: Float) = ShaderEffect(
        FRAG_DIRECTIONAL_WARP, mapOf("uDurationUs" to durationUs)
    )

    fun transitionWind(durationUs: Float) = ShaderEffect(
        FRAG_WIND, mapOf("uDurationUs" to durationUs)
    )

    fun transitionMorph(durationUs: Float) = ShaderEffect(
        FRAG_MORPH, mapOf("uDurationUs" to durationUs)
    )

    fun transitionGlitch(durationUs: Float) = ShaderEffect(
        FRAG_GLITCH_TRANS, mapOf("uDurationUs" to durationUs)
    )

    fun transitionCrossZoom(durationUs: Float) = ShaderEffect(
        FRAG_CROSS_ZOOM, mapOf("uDurationUs" to durationUs)
    )

    fun transitionDreamy(durationUs: Float) = ShaderEffect(
        FRAG_DREAMY, mapOf("uDurationUs" to durationUs)
    )

    fun transitionHeart(durationUs: Float) = ShaderEffect(
        FRAG_HEART, mapOf("uDurationUs" to durationUs)
    )

    fun transitionSwirl(durationUs: Float) = ShaderEffect(
        FRAG_SWIRL, mapOf("uDurationUs" to durationUs)
    )

    // ─── Fragment shader sources ────────────────────────────────────────

    private const val H = "#version 300 es\nprecision mediump float;\n" +
        "uniform sampler2D uTexSampler;\nin vec2 vTexCoord;\nout vec4 fragColor;\n"

    private const val FRAG_VIGNETTE = H +
        "uniform float uIntensity;\nuniform float uRadius;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float d = length(vTexCoord - 0.5) * 1.414;\n" +
        "  float v = smoothstep(uRadius + 0.4, uRadius - 0.3, d * (0.5 + uIntensity * 1.5));\n" +
        "  fragColor = vec4(c.rgb * v, c.a);\n}"

    private const val FRAG_SHARPEN = H +
        "uniform vec2 uResolution;\nuniform float uAmount;\n" +
        "void main() {\n" +
        "  vec2 t = 1.0 / uResolution;\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  vec4 n = texture(uTexSampler, vTexCoord + vec2(-t.x, 0.0))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(t.x, 0.0))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(0.0, -t.y))\n" +
        "         + texture(uTexSampler, vTexCoord + vec2(0.0, t.y));\n" +
        "  fragColor = vec4(clamp(c.rgb + (c.rgb * 4.0 - n.rgb) * uAmount, 0.0, 1.0), c.a);\n}"

    private const val FRAG_FILM_GRAIN = H +
        "uniform float uIntensity;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float noise = rand(vTexCoord * 1000.0 + uTime) * 2.0 - 1.0;\n" +
        "  fragColor = vec4(clamp(c.rgb + noise * uIntensity * 0.3, 0.0, 1.0), c.a);\n}"

    private const val FRAG_GAUSSIAN_BLUR = H +
        "uniform vec2 uResolution;\nuniform float uRadius;\n" +
        "void main() {\n" +
        "  vec2 t = uRadius / uResolution;\n" +
        "  vec4 s = texture(uTexSampler, vTexCoord) * 4.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + t);\n" +
        "  s += texture(uTexSampler, vTexCoord - t);\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, -t.y));\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(-t.x, t.y));\n" +
        "  fragColor = s / 16.0;\n}"

    private const val FRAG_RADIAL_BLUR = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 dir = vTexCoord - 0.5;\n" +
        "  float str = uIntensity * 0.02;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = 0; i < 10; i++) {\n" +
        "    s += texture(uTexSampler, vTexCoord - dir * float(i) / 10.0 * str);\n" +
        "  }\n" +
        "  fragColor = s / 10.0;\n}"

    private const val FRAG_MOTION_BLUR = H +
        "uniform vec2 uResolution;\nuniform float uIntensity;\nuniform float uAngle;\n" +
        "void main() {\n" +
        "  float rad = uAngle * 3.14159 / 180.0;\n" +
        "  vec2 dir = vec2(cos(rad), sin(rad)) * uIntensity * 5.0 / uResolution;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = -5; i <= 5; i++) {\n" +
        "    s += texture(uTexSampler, vTexCoord + dir * float(i));\n" +
        "  }\n" +
        "  fragColor = s / 11.0;\n}"

    private const val FRAG_TILT_SHIFT = H +
        "uniform vec2 uResolution;\nuniform float uFocusY;\nuniform float uWidth;\nuniform float uBlur;\n" +
        "void main() {\n" +
        "  float dist = abs(vTexCoord.y - uFocusY);\n" +
        "  float blur = smoothstep(uWidth, uWidth + 0.15, dist) * uBlur * 5.0;\n" +
        "  vec2 t = blur / uResolution;\n" +
        "  vec4 s = texture(uTexSampler, vTexCoord) * 4.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(t.x, 0.0)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord - vec2(0.0, t.y)) * 2.0;\n" +
        "  s += texture(uTexSampler, vTexCoord + t);\n" +
        "  s += texture(uTexSampler, vTexCoord - t);\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(t.x, -t.y));\n" +
        "  s += texture(uTexSampler, vTexCoord + vec2(-t.x, t.y));\n" +
        "  vec4 blurred = s / 16.0;\n" +
        "  vec4 orig = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = mix(orig, blurred, smoothstep(uWidth, uWidth + 0.15, dist));\n}"

    private const val FRAG_MOSAIC = H +
        "uniform vec2 uResolution;\nuniform float uSize;\n" +
        "void main() {\n" +
        "  vec2 bs = vec2(uSize) / uResolution;\n" +
        "  vec2 uv = floor(vTexCoord / bs) * bs + bs * 0.5;\n" +
        "  fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_FISHEYE = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float power = 1.0 + uIntensity * 3.0;\n" +
        "  if (dist < 0.5) {\n" +
        "    float f = pow(dist / 0.5, power) * 0.5 / dist;\n" +
        "    uv *= f;\n" +
        "  }\n" +
        "  fragColor = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n}"

    private const val FRAG_GLITCH = H +
        "uniform vec2 uResolution;\nuniform float uIntensity;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  float off = uIntensity * 0.02;\n" +
        "  float shift = rand(vec2(floor(vTexCoord.y * 20.0), floor(uTime * 5.0))) * off;\n" +
        "  float r = texture(uTexSampler, vec2(vTexCoord.x + shift, vTexCoord.y)).r;\n" +
        "  float g = texture(uTexSampler, vTexCoord).g;\n" +
        "  float b = texture(uTexSampler, vec2(vTexCoord.x - shift, vTexCoord.y)).b;\n" +
        "  float scan = sin(vTexCoord.y * uResolution.y * 1.5) * 0.04 * uIntensity;\n" +
        "  fragColor = vec4(r + scan, g + scan, b + scan, 1.0);\n}"

    private const val FRAG_PIXELATE = H +
        "uniform vec2 uResolution;\nuniform float uSize;\n" +
        "void main() {\n" +
        "  vec2 bs = vec2(uSize) / uResolution;\n" +
        "  vec2 uv = floor(vTexCoord / bs) * bs + bs * 0.5;\n" +
        "  fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_WAVE = H +
        "uniform float uAmplitude;\nuniform float uFrequency;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  uv.x += sin(uv.y * uFrequency + uTime * 3.0) * uAmplitude;\n" +
        "  uv.y += cos(uv.x * uFrequency + uTime * 2.0) * uAmplitude * 0.7;\n" +
        "  fragColor = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n}"

    private const val FRAG_CHROMATIC_ABERRATION = H +
        "uniform float uIntensity;\n" +
        "void main() {\n" +
        "  vec2 dir = (vTexCoord - 0.5) * uIntensity * 0.01;\n" +
        "  float r = texture(uTexSampler, vTexCoord + dir).r;\n" +
        "  float g = texture(uTexSampler, vTexCoord).g;\n" +
        "  float b = texture(uTexSampler, vTexCoord - dir).b;\n" +
        "  fragColor = vec4(r, g, b, 1.0);\n}"

    private const val FRAG_CHROMA_KEY = H +
        "uniform float uKeyR;\nuniform float uKeyG;\nuniform float uKeyB;\n" +
        "uniform float uThreshold;\nuniform float uSmoothing;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float diff = distance(c.rgb, vec3(uKeyR, uKeyG, uKeyB));\n" +
        "  float alpha = smoothstep(uThreshold, uThreshold + uSmoothing, diff);\n" +
        "  fragColor = vec4(c.rgb, c.a * alpha);\n}"

    // ─── Transition shaders ─────────────────────────────────────────────

    private const val FRAG_FADE_IN_BLACK = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_FADE_IN_WHITE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  fragColor = vec4(mix(vec3(1.0), c.rgb, progress), c.a);\n}"

    private const val FRAG_WIPE_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float pos = vTexCoord.x * uDirX + vTexCoord.y * uDirY;\n" +
        "  float lo = min(uDirX, 0.0) + min(uDirY, 0.0);\n" +
        "  float hi = max(uDirX, 0.0) + max(uDirY, 0.0);\n" +
        "  float edge = (pos - lo) / max(hi - lo, 0.001);\n" +
        "  float p = progress * 1.04 - 0.02;\n" +
        "  float reveal = 1.0 - smoothstep(p - 0.02, p + 0.02, edge);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_ZOOM_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float scale = mix(3.0, 1.0, progress);\n" +
        "  vec2 uv = (vTexCoord - 0.5) * scale + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_CIRCLE_OPEN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float dist = length(vTexCoord - 0.5);\n" +
        "  float radius = progress * 0.75;\n" +
        "  float reveal = smoothstep(radius - 0.02, radius + 0.02, 0.75 - dist);\n" +
        "  fragColor = vec4(c.rgb * reveal, c.a);\n}"

    private const val FRAG_ZOOM_OUT = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float scale = mix(0.3, 1.0, progress);\n" +
        "  vec2 uv = (vTexCoord - 0.5) / scale + 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_SPIN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float angle = (1.0 - progress) * 6.28318;\n" +
        "  float sc = max(progress, 0.01);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float cs = cos(angle), sn = sin(angle);\n" +
        "  uv = vec2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs) / sc + 0.5;\n" +
        "  vec4 col = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(col.rgb * progress, col.a);\n}"

    private const val FRAG_FLIP = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float xScale = abs(progress * 2.0 - 1.0);\n" +
        "  if (progress < 0.5) { fragColor = vec4(0.0, 0.0, 0.0, 1.0); }\n" +
        "  else {\n" +
        "    vec2 uv = vec2((vTexCoord.x - 0.5) / max(xScale, 0.01) + 0.5, vTexCoord.y);\n" +
        "    fragColor = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  }\n}"

    private const val FRAG_SLIDE_IN = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "uniform float uDirX;\nuniform float uDirY;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 offset = vec2(uDirX, uDirY) * (1.0 - progress);\n" +
        "  vec2 uv = vTexCoord + offset;\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0)\n" +
        "    fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else fragColor = texture(uTexSampler, uv);\n}"

    private const val FRAG_CUBE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float angle = (1.0 - progress) * 1.5708;\n" +
        "  float xOff = sin(angle) * 0.5;\n" +
        "  float xSc = cos(angle);\n" +
        "  vec2 uv = vec2((vTexCoord.x - 0.5 + xOff) / max(xSc, 0.01) + 0.5, vTexCoord.y);\n" +
        "  if (uv.x < 0.0 || uv.x > 1.0) fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else {\n" +
        "    vec4 c = texture(uTexSampler, uv);\n" +
        "    fragColor = vec4(c.rgb * (0.5 + 0.5 * xSc), c.a);\n" +
        "  }\n}"

    private const val FRAG_RIPPLE = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float wave = sin(dist * 30.0 - progress * 10.0) * (1.0 - progress) * 0.03;\n" +
        "  uv += normalize(uv + 0.001) * wave;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_PIXELATE_TRANS = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blocks = mix(5.0, 200.0, progress * progress);\n" +
        "  vec2 uv = floor(vTexCoord * blocks) / blocks;\n" +
        "  vec4 c = texture(uTexSampler, uv);\n" +
        "  fragColor = vec4(c.rgb * smoothstep(0.0, 0.3, progress), c.a);\n}"

    private const val FRAG_DIRECTIONAL_WARP = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float warp = (1.0 - progress) * 0.3;\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  uv.x += sin(uv.y * 10.0) * warp;\n" +
        "  uv.y += cos(uv.x * 10.0) * warp * 0.5;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_WIND = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float wind = 1.0 - progress;\n" +
        "  float offset = wind * (sin(vTexCoord.y * 30.0 + uTime * 5.0) * 0.1 + 0.5);\n" +
        "  vec2 uv = vec2(vTexCoord.x + offset, vTexCoord.y);\n" +
        "  if (uv.x > 1.0) fragColor = vec4(0.0, 0.0, 0.0, 1.0);\n" +
        "  else {\n" +
        "    vec4 c = texture(uTexSampler, uv);\n" +
        "    fragColor = vec4(c.rgb * smoothstep(0.0, 0.2, progress), c.a);\n" +
        "  }\n}"

    private const val FRAG_MORPH = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float morph = (1.0 - progress) * 0.15;\n" +
        "  vec2 uv = vTexCoord;\n" +
        "  float noise = sin(uv.x * 20.0) * cos(uv.y * 20.0);\n" +
        "  uv += noise * morph;\n" +
        "  vec4 c = texture(uTexSampler, clamp(uv, 0.0, 1.0));\n" +
        "  fragColor = vec4(c.rgb * progress, c.a);\n}"

    private const val FRAG_GLITCH_TRANS = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "float rand(vec2 co) { return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453); }\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float str = (1.0 - progress) * 0.1;\n" +
        "  float n = rand(vec2(floor(vTexCoord.y * 15.0), floor(uTime * 8.0)));\n" +
        "  float shift = (n - 0.5) * str;\n" +
        "  float r = texture(uTexSampler, vec2(vTexCoord.x + shift, vTexCoord.y)).r;\n" +
        "  float g = texture(uTexSampler, vTexCoord).g;\n" +
        "  float b = texture(uTexSampler, vec2(vTexCoord.x - shift, vTexCoord.y)).b;\n" +
        "  fragColor = vec4(vec3(r, g, b) * progress, 1.0);\n}"

    private const val FRAG_CROSS_ZOOM = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blur = (1.0 - progress) * 0.1;\n" +
        "  vec2 dir = vTexCoord - 0.5;\n" +
        "  vec4 s = vec4(0.0);\n" +
        "  for (int i = 0; i < 8; i++) {\n" +
        "    float t = float(i) / 7.0;\n" +
        "    s += texture(uTexSampler, vTexCoord - dir * blur * t);\n" +
        "  }\n" +
        "  fragColor = vec4((s / 8.0).rgb * progress, 1.0);\n}"

    private const val FRAG_DREAMY = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  float blur = (1.0 - progress) * 0.01;\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  c += texture(uTexSampler, vTexCoord + vec2(blur, 0.0));\n" +
        "  c += texture(uTexSampler, vTexCoord - vec2(blur, 0.0));\n" +
        "  c += texture(uTexSampler, vTexCoord + vec2(0.0, blur));\n" +
        "  c += texture(uTexSampler, vTexCoord - vec2(0.0, blur));\n" +
        "  c /= 5.0;\n" +
        "  float glow = 1.0 + (1.0 - progress) * 0.5;\n" +
        "  fragColor = vec4(clamp(c.rgb * glow * progress, 0.0, 1.0), 1.0);\n}"

    private const val FRAG_HEART = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 p = (vTexCoord - vec2(0.5, 0.6)) * vec2(2.5, -3.0);\n" +
        "  float h = pow(p.x * p.x + p.y * p.y - 1.0, 3.0) - p.x * p.x * p.y * p.y * p.y;\n" +
        "  float mask = smoothstep(0.1, -0.1, h + (1.0 - progress) * 2.0);\n" +
        "  vec4 c = texture(uTexSampler, vTexCoord);\n" +
        "  fragColor = vec4(c.rgb * mask, c.a);\n}"

    private const val FRAG_SWIRL = H +
        "uniform float uDurationUs;\nuniform float uTime;\n" +
        "void main() {\n" +
        "  float progress = clamp(uTime * 1000000.0 / uDurationUs, 0.0, 1.0);\n" +
        "  vec2 uv = vTexCoord - 0.5;\n" +
        "  float dist = length(uv);\n" +
        "  float angle = (1.0 - progress) * dist * 15.0;\n" +
        "  float cs = cos(angle), sn = sin(angle);\n" +
        "  uv = vec2(uv.x * cs - uv.y * sn, uv.x * sn + uv.y * cs);\n" +
        "  vec4 col = texture(uTexSampler, clamp(uv + 0.5, 0.0, 1.0));\n" +
        "  fragColor = vec4(col.rgb * progress, col.a);\n}"
}
