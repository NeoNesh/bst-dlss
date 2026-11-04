package dev.bstdlss.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

/**
 * Self-contained OpenGL 3.2 Core Profile Shader & Full-Screen Pass Manager for BST DLSS.
 * Features 1:1 Pixel-Perfect MRT Extraction + Halo-Free Contrast-Adaptive Sharpening (RCAS).
 */
public final class GlShaderManager {
    private static boolean initialized = false;
    private static int emptyVao = 0;

    // Program 1: MRT Extraction (Color + R32F Depth + RG16F Motion Vectors)
    private static int progMvExtract = 0;
    private static int uMvColorTex = -1;
    private static int uMvDepthTex = -1;
    private static int uMvUvScale = -1;
    private static int uMvRenderSize = -1;
    private static int uMvJitterUv = -1;
    private static int uMvCamDeltaRot = -1;
    private static int uMvCamDeltaPos = -1;

    // Program 2: Composite + Halo-Free RCAS Sharpening + Frame Interpolation
    private static int progCompositeRcas = 0;
    private static int uCompCurrentTex = -1;
    private static int uCompHistoryTex = -1;
    private static int uCompMvTex = -1;
    private static int uCompOutSize = -1;
    private static int uCompSharpness = -1;
    private static int uCompCrispMode = -1;
    private static int uCompFrameGenBlend = -1;

    // Program 3: Temporal + Spatial EASU Fallback Upscaler
    private static int progFallbackUpscale = 0;
    private static int uFbColorTex = -1;
    private static int uFbHistoryTex = -1;
    private static int uFbMvTex = -1;
    private static int uFbUvScale = -1;
    private static int uFbRenderSize = -1;
    private static int uFbOutSize = -1;
    private static int uFbSharpness = -1;
    private static int uFbReset = -1;

    private static final String VERT_FULLSCREEN =
        "#version 150 core\n" +
        "out vec2 vUv;\n" +
        "void main() {\n" +
        "    vec2 pos = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);\n" +
        "    vUv = pos;\n" +
        "    gl_Position = vec4(pos * 2.0 - 1.0, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAG_MV_EXTRACT =
        "#version 150 core\n" +
        "in vec2 vUv;\n" +
        "uniform sampler2D uColorTex;\n" +
        "uniform sampler2D uDepthTex;\n" +
        "uniform vec2 uUvScale;\n" +
        "uniform vec2 uRenderSize;\n" +
        "uniform vec2 uJitterUv;\n" +
        "uniform vec2 uCamDeltaRot;\n" +
        "uniform vec3 uCamDeltaPos;\n" +
        "out vec4 outColor;\n" +
        "out vec4 outDepth;\n" +
        "out vec4 outMotionVec;\n" +
        "void main() {\n" +
        "    ivec2 pix = clamp(ivec2(gl_FragCoord.xy), ivec2(0), ivec2(uRenderSize) - ivec2(1));\n" +
        "    vec4 col = texelFetch(uColorTex, pix, 0);\n" +
        "    float depth = texelFetch(uDepthTex, pix, 0).r;\n" +
        "    float zNdc = depth * 2.0 - 1.0;\n" +
        "    float linearZ = (2.0 * 0.05 * 512.0) / max(0.001, 512.0 + 0.05 - zNdc * (512.0 - 0.05));\n" +
        "    float invZ = (depth < 0.999) ? clamp(1.0 / max(0.5, linearZ), 0.0, 2.0) : 0.0;\n" +
        "    vec2 ndc = vUv * 2.0 - 1.0;\n" +
        "    vec2 rotPixels = uCamDeltaRot * (1.0 + 0.12 * ndc * ndc) * (0.5 * uRenderSize);\n" +
        "    vec2 transPixels = (vec2(uCamDeltaPos.x, uCamDeltaPos.y) + ndc * uCamDeltaPos.z) * (invZ * 0.5 * uRenderSize);\n" +
        "    vec2 mvPixels = clamp(rotPixels + transPixels, vec2(-32.0), vec2(32.0));\n" +
        "    outColor = vec4(col.rgb, 1.0);\n" +
        "    outDepth = vec4(depth, depth, depth, 1.0);\n" +
        "    outMotionVec = vec4(mvPixels.x, mvPixels.y, 0.0, 1.0);\n" +
        "}\n";

    private static final String FRAG_COMPOSITE_RCAS =
        "#version 150 core\n" +
        "in vec2 vUv;\n" +
        "uniform sampler2D uCurrentTex;\n" +
        "uniform sampler2D uHistoryTex;\n" +
        "uniform sampler2D uMvTex;\n" +
        "uniform vec2 uOutSize;\n" +
        "uniform float uSharpness;\n" +
        "uniform int uCrispMode;\n" +
        "uniform float uFrameGenBlend;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "    vec2 texel = 1.0 / max(vec2(1.0), uOutSize);\n" +
        "    vec3 c = texture(uCurrentTex, vUv).rgb;\n" +
        "    if (uSharpness > 0.005) {\n" +
        "        vec3 n = texture(uCurrentTex, vUv + vec2(0.0, -texel.y)).rgb;\n" +
        "        vec3 s = texture(uCurrentTex, vUv + vec2(0.0,  texel.y)).rgb;\n" +
        "        vec3 w = texture(uCurrentTex, vUv + vec2(-texel.x, 0.0)).rgb;\n" +
        "        vec3 e = texture(uCurrentTex, vUv + vec2( texel.x, 0.0)).rgb;\n" +
        "        vec3 bMin = min(c, min(min(n, s), min(w, e)));\n" +
        "        vec3 bMax = max(c, max(max(n, s), max(w, e)));\n" +
        "        vec3 avg = 0.25 * (n + s + w + e);\n" +
        "        vec3 diff = c - avg;\n" +
        "        float contrast = max(max(bMax.r - bMin.r, bMax.g - bMin.g), bMax.b - bMin.b);\n" +
        "        float weight = clamp(uSharpness * 0.35 * (1.0 - contrast * 0.5), 0.0, 0.30);\n" +
        "        c = clamp(c + diff * weight, bMin, bMax);\n" +
        "    }\n" +
        "    if (uFrameGenBlend > 0.01) {\n" +
        "        vec2 mv = texture(uMvTex, vUv).xy * texel * 0.5;\n" +
        "        vec3 prev = texture(uHistoryTex, clamp(vUv - mv, vec2(0.001), vec2(0.999))).rgb;\n" +
        "        c = mix(c, prev, clamp(uFrameGenBlend, 0.0, 0.25));\n" +
        "    }\n" +
        "    fragColor = vec4(c, 1.0);\n" +
        "}\n";

    private static final String FRAG_FALLBACK_UPSCALE =
        "#version 150 core\n" +
        "in vec2 vUv;\n" +
        "uniform sampler2D uColorTex;\n" +
        "uniform sampler2D uHistoryTex;\n" +
        "uniform sampler2D uMvTex;\n" +
        "uniform vec2 uUvScale;\n" +
        "uniform vec2 uRenderSize;\n" +
        "uniform vec2 uOutSize;\n" +
        "uniform float uSharpness;\n" +
        "uniform int uReset;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "    vec2 srcUv = clamp(vUv * uUvScale, vec2(0.0005), uUvScale - vec2(0.0005));\n" +
        "    vec2 srcTexel = uUvScale / max(vec2(1.0), uRenderSize);\n" +
        "    vec3 c  = texture(uColorTex, srcUv).rgb;\n" +
        "    vec3 n  = texture(uColorTex, srcUv + vec2(0.0, -srcTexel.y)).rgb;\n" +
        "    vec3 s  = texture(uColorTex, srcUv + vec2(0.0,  srcTexel.y)).rgb;\n" +
        "    vec3 w  = texture(uColorTex, srcUv + vec2(-srcTexel.x, 0.0)).rgb;\n" +
        "    vec3 e  = texture(uColorTex, srcUv + vec2( srcTexel.x, 0.0)).rgb;\n" +
        "    vec3 boxMin = min(c, min(min(n, s), min(w, e)));\n" +
        "    vec3 boxMax = max(c, max(max(n, s), max(w, e)));\n" +
        "    vec3 edgeSharpened = clamp(c + (c - 0.25 * (n + s + w + e)) * (uSharpness * 0.35), boxMin, boxMax);\n" +
        "    if (uReset == 0) {\n" +
        "        vec2 mvPixels = texture(uMvTex, vUv).xy;\n" +
        "        vec2 prevUv = clamp(vUv + (mvPixels / max(vec2(1.0), uRenderSize)), vec2(0.001), vec2(0.999));\n" +
        "        vec3 hist = clamp(texture(uHistoryTex, prevUv).rgb, boxMin, boxMax);\n" +
        "        edgeSharpened = mix(edgeSharpened, hist, 0.35);\n" +
        "    }\n" +
        "    fragColor = vec4(edgeSharpened, 1.0);\n" +
        "}\n";

    private GlShaderManager() {
    }

    public static void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        emptyVao = GL30.glGenVertexArrays();

        progMvExtract = createProgram(VERT_FULLSCREEN, FRAG_MV_EXTRACT, new String[]{"outColor", "outDepth", "outMotionVec"});
        uMvColorTex    = GL20.glGetUniformLocation(progMvExtract, "uColorTex");
        uMvDepthTex    = GL20.glGetUniformLocation(progMvExtract, "uDepthTex");
        uMvUvScale     = GL20.glGetUniformLocation(progMvExtract, "uUvScale");
        uMvRenderSize  = GL20.glGetUniformLocation(progMvExtract, "uRenderSize");
        uMvJitterUv    = GL20.glGetUniformLocation(progMvExtract, "uJitterUv");
        uMvCamDeltaRot = GL20.glGetUniformLocation(progMvExtract, "uCamDeltaRot");
        uMvCamDeltaPos = GL20.glGetUniformLocation(progMvExtract, "uCamDeltaPos");

        progCompositeRcas = createProgram(VERT_FULLSCREEN, FRAG_COMPOSITE_RCAS, new String[]{"fragColor"});
        uCompCurrentTex    = GL20.glGetUniformLocation(progCompositeRcas, "uCurrentTex");
        uCompHistoryTex    = GL20.glGetUniformLocation(progCompositeRcas, "uHistoryTex");
        uCompMvTex         = GL20.glGetUniformLocation(progCompositeRcas, "uMvTex");
        uCompOutSize       = GL20.glGetUniformLocation(progCompositeRcas, "uOutSize");
        uCompSharpness     = GL20.glGetUniformLocation(progCompositeRcas, "uSharpness");
        uCompCrispMode     = GL20.glGetUniformLocation(progCompositeRcas, "uCrispMode");
        uCompFrameGenBlend = GL20.glGetUniformLocation(progCompositeRcas, "uFrameGenBlend");

        progFallbackUpscale = createProgram(VERT_FULLSCREEN, FRAG_FALLBACK_UPSCALE, new String[]{"fragColor"});
        uFbColorTex   = GL20.glGetUniformLocation(progFallbackUpscale, "uColorTex");
        uFbHistoryTex = GL20.glGetUniformLocation(progFallbackUpscale, "uHistoryTex");
        uFbMvTex      = GL20.glGetUniformLocation(progFallbackUpscale, "uMvTex");
        uFbUvScale    = GL20.glGetUniformLocation(progFallbackUpscale, "uUvScale");
        uFbRenderSize = GL20.glGetUniformLocation(progFallbackUpscale, "uRenderSize");
        uFbOutSize    = GL20.glGetUniformLocation(progFallbackUpscale, "uOutSize");
        uFbSharpness  = GL20.glGetUniformLocation(progFallbackUpscale, "uSharpness");
        uFbReset      = GL20.glGetUniformLocation(progFallbackUpscale, "uReset");
    }

    public static void runMvExtractPass(
        int srcColorTex, int srcDepthTex,
        float uvScaleX, float uvScaleY,
        int renderW, int renderH,
        float jitterX, float jitterY,
        float deltaYaw, float deltaPitch,
        float deltaX, float deltaY, float deltaZ,
        boolean crispFilter
    ) {
        ensureInitialized();
        GL20.glUseProgram(progMvExtract);
        GL20.glUniform1i(uMvColorTex, 0);
        GL20.glUniform1i(uMvDepthTex, 1);
        GL20.glUniform2f(uMvUvScale, uvScaleX, uvScaleY);
        GL20.glUniform2f(uMvRenderSize, (float) renderW, (float) renderH);
        GL20.glUniform2f(uMvJitterUv, jitterX / Math.max(1.0f, renderW), jitterY / Math.max(1.0f, renderH));
        GL20.glUniform2f(uMvCamDeltaRot, deltaYaw, deltaPitch);
        GL20.glUniform3f(uMvCamDeltaPos, deltaX, deltaY, deltaZ);

        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcColorTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcDepthTex);

        GL30.glBindVertexArray(emptyVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    public static void runCompositeRcasPass(
        int currentOutTex, int historyTex, int mvTex,
        int outW, int outH,
        float sharpness, boolean crispMode, float frameGenBlend
    ) {
        ensureInitialized();
        GL20.glUseProgram(progCompositeRcas);
        GL20.glUniform1i(uCompCurrentTex, 0);
        GL20.glUniform1i(uCompHistoryTex, 1);
        GL20.glUniform1i(uCompMvTex, 2);
        GL20.glUniform2f(uCompOutSize, (float) outW, (float) outH);
        GL20.glUniform1f(uCompSharpness, sharpness);
        GL20.glUniform1i(uCompCrispMode, crispMode ? 1 : 0);
        GL20.glUniform1f(uCompFrameGenBlend, frameGenBlend);

        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentOutTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mvTex);

        GL30.glBindVertexArray(emptyVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    public static void runFallbackUpscalePass(
        int srcColorTex, int historyTex, int mvTex,
        float uvScaleX, float uvScaleY,
        int renderW, int renderH,
        int outW, int outH,
        float sharpness, boolean reset
    ) {
        ensureInitialized();
        GL20.glUseProgram(progFallbackUpscale);
        GL20.glUniform1i(uFbColorTex, 0);
        GL20.glUniform1i(uFbHistoryTex, 1);
        GL20.glUniform1i(uFbMvTex, 2);
        GL20.glUniform2f(uFbUvScale, uvScaleX, uvScaleY);
        GL20.glUniform2f(uFbRenderSize, (float) renderW, (float) renderH);
        GL20.glUniform2f(uFbOutSize, (float) outW, (float) outH);
        GL20.glUniform1f(uFbSharpness, sharpness);
        GL20.glUniform1i(uFbReset, reset ? 1 : 0);

        GL33.glBindSampler(0, 0);
        GL33.glBindSampler(1, 0);
        GL33.glBindSampler(2, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, srcColorTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, historyTex);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mvTex);

        GL30.glBindVertexArray(emptyVao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static int createProgram(String vsSource, String fsSource, String[] fragOutputs) {
        int vs = compileShader(GL20.GL_VERTEX_SHADER, vsSource);
        int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fsSource);
        int prog = GL20.glCreateProgram();
        GL20.glAttachShader(prog, vs);
        GL20.glAttachShader(prog, fs);
        if (fragOutputs != null) {
            for (int i = 0; i < fragOutputs.length; i++) {
                GL30.glBindFragDataLocation(prog, i, fragOutputs[i]);
            }
        }
        GL20.glLinkProgram(prog);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        return prog;
    }

    private static int compileShader(int type, String source) {
        int sh = GL20.glCreateShader(type);
        GL20.glShaderSource(sh, source);
        GL20.glCompileShader(sh);
        return sh;
    }
}
