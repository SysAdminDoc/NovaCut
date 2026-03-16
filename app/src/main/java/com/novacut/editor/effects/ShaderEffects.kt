package com.novacut.editor.effects

import android.opengl.GLES30

/**
 * GLSL shader source code for video effects.
 * These are used both for real-time preview (via ExoPlayer setVideoEffects)
 * and for export (via Transformer effects pipeline).
 */
object ShaderEffects {

    // Vertex shader shared by all fragment effects
    const val VERTEX_SHADER = """
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = aTexCoord;
        }
    """

    // OES vertex shader for external textures (camera/video)
    const val VERTEX_SHADER_OES = """
        #extension GL_OES_EGL_image_external_essl3 : require
        attribute vec4 aPosition;
        attribute vec2 aTexCoord;
        uniform mat4 uTexMatrix;
        varying vec2 vTexCoord;
        void main() {
            gl_Position = aPosition;
            vTexCoord = (uTexMatrix * vec4(aTexCoord, 0.0, 1.0)).xy;
        }
    """

    const val BRIGHTNESS_CONTRAST_SATURATION = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uBrightness;
        uniform float uContrast;
        uniform float uSaturation;
        varying vec2 vTexCoord;

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            // Brightness
            color.rgb += uBrightness;
            // Contrast
            color.rgb = (color.rgb - 0.5) * uContrast + 0.5;
            // Saturation
            float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            color.rgb = mix(vec3(gray), color.rgb, uSaturation);
            gl_FragColor = clamp(color, 0.0, 1.0);
        }
    """

    const val VIGNETTE = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        uniform float uRadius;
        varying vec2 vTexCoord;

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec2 center = vec2(0.5, 0.5);
            float dist = distance(vTexCoord, center);
            float vig = smoothstep(uRadius, uRadius - 0.45, dist);
            color.rgb *= mix(1.0 - uIntensity, 1.0, vig);
            gl_FragColor = color;
        }
    """

    const val GAUSSIAN_BLUR_H = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uBlurSize;
        varying vec2 vTexCoord;

        void main() {
            vec4 sum = vec4(0.0);
            float weights[5];
            weights[0] = 0.227027;
            weights[1] = 0.1945946;
            weights[2] = 0.1216216;
            weights[3] = 0.054054;
            weights[4] = 0.016216;

            sum += texture2D(uTexture, vTexCoord) * weights[0];
            for (int i = 1; i < 5; i++) {
                float offset = float(i) * uBlurSize;
                sum += texture2D(uTexture, vec2(vTexCoord.x + offset, vTexCoord.y)) * weights[i];
                sum += texture2D(uTexture, vec2(vTexCoord.x - offset, vTexCoord.y)) * weights[i];
            }
            gl_FragColor = sum;
        }
    """

    const val GAUSSIAN_BLUR_V = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uBlurSize;
        varying vec2 vTexCoord;

        void main() {
            vec4 sum = vec4(0.0);
            float weights[5];
            weights[0] = 0.227027;
            weights[1] = 0.1945946;
            weights[2] = 0.1216216;
            weights[3] = 0.054054;
            weights[4] = 0.016216;

            sum += texture2D(uTexture, vTexCoord) * weights[0];
            for (int i = 1; i < 5; i++) {
                float offset = float(i) * uBlurSize;
                sum += texture2D(uTexture, vec2(vTexCoord.x, vTexCoord.y + offset)) * weights[i];
                sum += texture2D(uTexture, vec2(vTexCoord.x, vTexCoord.y - offset)) * weights[i];
            }
            gl_FragColor = sum;
        }
    """

    const val CHROMA_KEY = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform vec3 uKeyColor;
        uniform float uSimilarity;
        uniform float uSmoothness;
        uniform float uSpill;
        varying vec2 vTexCoord;

        vec2 rgbToUV(vec3 rgb) {
            return vec2(
                rgb.r * -0.169 + rgb.g * -0.331 + rgb.b * 0.500 + 0.500,
                rgb.r * 0.500 + rgb.g * -0.419 + rgb.b * -0.081 + 0.500
            );
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            vec2 chromaVec = rgbToUV(color.rgb) - rgbToUV(uKeyColor);
            float chromaDist = sqrt(dot(chromaVec, chromaVec));
            float baseMask = chromaDist - uSimilarity;
            float fullMask = pow(clamp(baseMask / uSmoothness, 0.0, 1.0), 1.5);

            // Spill suppression
            float spillVal = chromaDist < uSimilarity + uSpill ?
                (1.0 - smoothstep(uSimilarity, uSimilarity + uSpill, chromaDist)) : 0.0;
            color.rgb -= uKeyColor * spillVal * 0.5;

            color.a = fullMask;
            gl_FragColor = color;
        }
    """

    const val FILM_GRAIN = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        uniform float uTime;
        varying vec2 vTexCoord;

        float random(vec2 co) {
            return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            float noise = random(vTexCoord + vec2(uTime)) * 2.0 - 1.0;
            color.rgb += noise * uIntensity;
            gl_FragColor = clamp(color, 0.0, 1.0);
        }
    """

    const val SHARPEN = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uStrength;
        uniform vec2 uTexelSize;
        varying vec2 vTexCoord;

        void main() {
            vec4 center = texture2D(uTexture, vTexCoord);
            vec4 top = texture2D(uTexture, vTexCoord + vec2(0.0, uTexelSize.y));
            vec4 bottom = texture2D(uTexture, vTexCoord - vec2(0.0, uTexelSize.y));
            vec4 left = texture2D(uTexture, vTexCoord - vec2(uTexelSize.x, 0.0));
            vec4 right = texture2D(uTexture, vTexCoord + vec2(uTexelSize.x, 0.0));

            vec4 sharpened = center * (1.0 + 4.0 * uStrength) -
                (top + bottom + left + right) * uStrength;
            gl_FragColor = clamp(sharpened, 0.0, 1.0);
        }
    """

    const val GLITCH = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        uniform float uTime;
        varying vec2 vTexCoord;

        float random(vec2 co) {
            return fract(sin(dot(co.xy, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
            vec2 uv = vTexCoord;
            float blockY = floor(uv.y * 20.0) / 20.0;
            float noise = random(vec2(blockY, uTime));

            if (noise > 1.0 - uIntensity * 0.3) {
                float shift = (random(vec2(blockY + 1.0, uTime)) - 0.5) * uIntensity * 0.1;
                uv.x += shift;
            }

            vec4 color;
            color.r = texture2D(uTexture, uv + vec2(uIntensity * 0.01, 0.0)).r;
            color.g = texture2D(uTexture, uv).g;
            color.b = texture2D(uTexture, uv - vec2(uIntensity * 0.01, 0.0)).b;
            color.a = 1.0;

            gl_FragColor = color;
        }
    """

    const val CHROMATIC_ABERRATION = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        varying vec2 vTexCoord;

        void main() {
            vec2 dir = vTexCoord - vec2(0.5);
            float dist = length(dir);
            vec2 offset = dir * dist * uIntensity * 0.02;

            vec4 color;
            color.r = texture2D(uTexture, vTexCoord + offset).r;
            color.g = texture2D(uTexture, vTexCoord).g;
            color.b = texture2D(uTexture, vTexCoord - offset).b;
            color.a = 1.0;
            gl_FragColor = color;
        }
    """

    const val PIXELATE = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uPixelSize;
        uniform vec2 uResolution;
        varying vec2 vTexCoord;

        void main() {
            vec2 pixelCount = uResolution / uPixelSize;
            vec2 uv = floor(vTexCoord * pixelCount) / pixelCount;
            gl_FragColor = texture2D(uTexture, uv);
        }
    """

    const val TILT_SHIFT = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uBlurSize;
        uniform float uFocusY;
        uniform float uFocusWidth;
        varying vec2 vTexCoord;

        void main() {
            float dist = abs(vTexCoord.y - uFocusY);
            float blur = smoothstep(uFocusWidth, uFocusWidth + 0.2, dist) * uBlurSize;

            vec4 sum = vec4(0.0);
            float total = 0.0;
            for (int i = -2; i <= 2; i++) {
                for (int j = -2; j <= 2; j++) {
                    vec2 offset = vec2(float(i), float(j)) * blur;
                    float weight = 1.0 - length(vec2(float(i), float(j))) / 3.0;
                    weight = max(weight, 0.0);
                    sum += texture2D(uTexture, vTexCoord + offset) * weight;
                    total += weight;
                }
            }
            gl_FragColor = sum / total;
        }
    """

    const val CYBERPUNK = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        varying vec2 vTexCoord;

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            // Push toward cyan/magenta palette
            float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
            vec3 tinted;
            if (gray < 0.5) {
                tinted = mix(color.rgb, vec3(0.0, gray * 1.5, gray * 1.8), uIntensity * 0.6);
            } else {
                tinted = mix(color.rgb, vec3(gray * 1.2, gray * 0.3, gray * 1.5), uIntensity * 0.5);
            }
            // Boost contrast
            tinted = (tinted - 0.5) * (1.0 + uIntensity * 0.4) + 0.5;
            gl_FragColor = vec4(clamp(tinted, 0.0, 1.0), color.a);
        }
    """

    const val NOIR = """
        precision mediump float;
        uniform sampler2D uTexture;
        uniform float uIntensity;
        varying vec2 vTexCoord;

        void main() {
            vec4 color = texture2D(uTexture, vTexCoord);
            float gray = dot(color.rgb, vec3(0.2126, 0.7152, 0.0722));
            // High contrast B&W
            gray = pow(gray, 1.0 + uIntensity * 0.5);
            gray = (gray - 0.5) * (1.0 + uIntensity) + 0.5;
            vec3 result = mix(color.rgb, vec3(gray), uIntensity);
            // Slight warm tone
            result += vec3(0.02, 0.01, 0.0) * uIntensity;
            gl_FragColor = vec4(clamp(result, 0.0, 1.0), color.a);
        }
    """

    // --- Transition shaders (ported from GL Transitions) ---

    const val TRANSITION_DISSOLVE = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            vec4 from = texture2D(uTexFrom, vTexCoord);
            vec4 to = texture2D(uTexTo, vTexCoord);
            gl_FragColor = mix(from, to, uProgress);
        }
    """

    const val TRANSITION_FADE_BLACK = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            vec4 from = texture2D(uTexFrom, vTexCoord);
            vec4 to = texture2D(uTexTo, vTexCoord);
            float p = uProgress * 2.0;
            if (p < 1.0) {
                gl_FragColor = mix(from, vec4(0.0, 0.0, 0.0, 1.0), p);
            } else {
                gl_FragColor = mix(vec4(0.0, 0.0, 0.0, 1.0), to, p - 1.0);
            }
        }
    """

    const val TRANSITION_WIPE_LEFT = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            vec4 from = texture2D(uTexFrom, vTexCoord);
            vec4 to = texture2D(uTexTo, vTexCoord);
            float edge = smoothstep(uProgress - 0.02, uProgress + 0.02, vTexCoord.x);
            gl_FragColor = mix(to, from, edge);
        }
    """

    const val TRANSITION_ZOOM_IN = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            float scale = 1.0 + uProgress * 0.5;
            vec2 center = vec2(0.5);
            vec2 uv = (vTexCoord - center) / scale + center;

            vec4 from = texture2D(uTexFrom, uv);
            vec4 to = texture2D(uTexTo, vTexCoord);

            float mixer = smoothstep(0.0, 1.0, uProgress);
            gl_FragColor = mix(from, to, mixer);
        }
    """

    const val TRANSITION_CIRCLE_OPEN = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            vec4 from = texture2D(uTexFrom, vTexCoord);
            vec4 to = texture2D(uTexTo, vTexCoord);
            float dist = distance(vTexCoord, vec2(0.5));
            float radius = uProgress * 0.75;
            float mixer = smoothstep(radius - 0.02, radius + 0.02, dist);
            gl_FragColor = mix(to, from, mixer);
        }
    """

    const val TRANSITION_SWIRL = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        void main() {
            vec2 center = vec2(0.5);
            vec2 tc = vTexCoord - center;
            float angle = uProgress * 6.2831853;
            float s = sin(angle);
            float c = cos(angle);
            tc = mat2(c, -s, s, c) * tc;
            tc += center;

            vec4 from = texture2D(uTexFrom, tc);
            vec4 to = texture2D(uTexTo, vTexCoord);
            gl_FragColor = mix(from, to, smoothstep(0.0, 1.0, uProgress));
        }
    """

    const val TRANSITION_GLITCH = """
        precision mediump float;
        uniform sampler2D uTexFrom;
        uniform sampler2D uTexTo;
        uniform float uProgress;
        varying vec2 vTexCoord;

        float random(vec2 co) {
            return fract(sin(dot(co, vec2(12.9898, 78.233))) * 43758.5453);
        }

        void main() {
            vec2 uv = vTexCoord;
            float blockY = floor(uv.y * 16.0) / 16.0;
            float noise = random(vec2(blockY, floor(uProgress * 10.0)));

            vec2 shift = vec2(0.0);
            if (noise > 0.5 && uProgress > 0.1 && uProgress < 0.9) {
                shift.x = (noise - 0.5) * 0.2 * sin(uProgress * 3.14159);
            }

            vec4 from = texture2D(uTexFrom, uv + shift);
            vec4 to = texture2D(uTexTo, uv - shift);

            float mixer = step(0.5, uProgress);
            if (random(vec2(uv.y, uProgress)) > 0.7 - abs(uProgress - 0.5) * 0.4) {
                mixer = 1.0 - mixer;
            }
            gl_FragColor = mix(from, to, mixer);
        }
    """

    /**
     * Compiles and links a shader program from vertex + fragment source.
     */
    fun compileProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource)
        val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource)

        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vertexShader)
        GLES30.glAttachShader(program, fragmentShader)
        GLES30.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(program)
            GLES30.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $log")
        }

        GLES30.glDeleteShader(vertexShader)
        GLES30.glDeleteShader(fragmentShader)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            GLES30.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed: $log")
        }
        return shader
    }
}
