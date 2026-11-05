package dev.bstdlss.render;

import dev.bstdlss.boot.FastBootEngine;
import dev.bstdlss.bridge.McRuntimeBridge;
import dev.bstdlss.config.BstDlssConfig;
import dev.bstdlss.config.DlssPreset;
import dev.bstdlss.config.DlssQualityMode;
import dev.bstdlss.gui.BstDlssHudRenderer;
import dev.bstdlss.i18n.I18n;
import dev.bstdlss.nativebridge.BstDlssNative;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Core Per-Frame Orchestrator for BST DLSS (RTX 20xx - 50xx Hardware NGX + Turbo FPS Boost).
 */
public final class BstDlssPipeline {
    private static boolean initialized = false;
    private static long frameIndex = 0L;
    private static long lastFrameNano = System.nanoTime();
    private static float smoothedFrameTimeMs = 16.6f;
    private static float smoothedDlssEvalMs = 0.0f;
    private static int currentFps = 60;
    private static int fpsFrameCounter = 0;
    private static long fpsTimerMs = System.currentTimeMillis();
    private static long lastBoostApplyMs = 0L;

    // Hotkey edge-detection states
    private static boolean prevKeyF8 = false;
    private static boolean prevKeyF9 = false;
    private static boolean prevKeyF10 = false;
    private static boolean prevKeyEsc = false;

    // Frame state tracking
    private static boolean wasInWorld = false;
    private static boolean worldDownscaledThisFrame = false;
    private static boolean worldUpscaledThisFrame = false;
    private static boolean resetHistoryNextFrame = true;
    private static int currentOutW = 1920;
    private static int currentOutH = 1080;
    private static int currentRenderW = 1280;
    private static int currentRenderH = 720;

    // Session state tracking
    private static int sessionRenderW = 0;
    private static int sessionRenderH = 0;
    private static int sessionOutW = 0;
    private static int sessionOutH = 0;
    private static DlssQualityMode sessionQuality = null;
    private static DlssPreset sessionPreset = null;
    private static boolean sessionWantHardwareNgx = false;
    private static boolean sessionHardwareNgx = false;

    // OpenGL / Interop FBO & texture handles
    private static int mrtExtractFbo = 0;
    private static int fallbackOutFbo = 0;
    private static int glColorTex = 0;
    private static int glDepthTex = 0;
    private static int glMvTex = 0;
    private static int glOutTex = 0;
    private static int glHistoryTex = 0;
    private static boolean ownsFallbackTextures = false;

    // Camera pose tracking for 6-DOF motion vectors
    private static final float[] currPose = new float[5];
    private static final float[] prevPose = new float[5];
    private static final ByteBuffer COLOR_MASK_BUF = BufferUtils.createByteBuffer(16);

    private BstDlssPipeline() {
    }

    public static synchronized void ensureInitialized() {
        if (initialized) return;
        initialized = true;

        File gameDir = McRuntimeBridge.getGameDirectory();
        BstDlssConfig.get().load(gameDir);
        FastBootEngine.start(gameDir);
        if (FastBootEngine.isCompleted() || !BstDlssConfig.get().isFastBoot()) {
            BstDlssNative.loadAndInit(gameDir);
        }
    }

    public static void onFrameStart() {
        ensureInitialized();

        if (!FastBootEngine.isCompleted()) {
            if (McRuntimeBridge.isGameReady()) {
                FastBootEngine.markBootComplete();
            } else {
                FastBootEngine.throttleEarlyWindowFps();
            }
        }

        long nowNano = System.nanoTime();
        float dtMs = (nowNano - lastFrameNano) / 1_000_000.0f;
        lastFrameNano = nowNano;
        if (dtMs > 0.1f && dtMs < 500.0f) {
            smoothedFrameTimeMs = smoothedFrameTimeMs * 0.90f + dtMs * 0.10f;
        }

        fpsFrameCounter++;
        long nowMs = System.currentTimeMillis();
        if (nowMs - fpsTimerMs >= 500L) {
            currentFps = Math.round((fpsFrameCounter * 1000.0f) / Math.max(1L, nowMs - fpsTimerMs));
            fpsFrameCounter = 0;
            fpsTimerMs = nowMs;
        }

        BstDlssConfig cfg = BstDlssConfig.get();
        if (nowMs - lastBoostApplyMs >= 1000L) {
            lastBoostApplyMs = nowMs;
            McRuntimeBridge.applyTurboFpsBoosts(
                cfg.isTurboFpsUncap(),
                cfg.isSmartEntityBoost(),
                cfg.isChunkSimulationBoost()
            );
        }

        long glfwWindow = 0L;
        try {
            glfwWindow = GLFW.glfwGetCurrentContext();
        } catch (Throwable ignored) {
        }
        pollHotkeys(glfwWindow);

        boolean nowInWorld = McRuntimeBridge.isInWorld();
        if (nowInWorld && !wasInWorld) {
            wasInWorld = true;
            BstDlssHudRenderer.showToast(I18n.get("toast.welcome"));
        } else if (!nowInWorld) {
            wasInWorld = false;
        }

        worldDownscaledThisFrame = false;
        worldUpscaledThisFrame = false;

        Object rt = McRuntimeBridge.getMainRenderTarget();
        if (rt == null) return;

        int outW = McRuntimeBridge.getRenderTargetWidth(rt);
        int outH = McRuntimeBridge.getRenderTargetHeight(rt);
        if (outW <= 0 || outH <= 0) return;

        currentOutW = outW;
        currentOutH = outH;

        DlssQualityMode mode = cfg.getQualityMode();
        if (!mode.isEnabled() || !McRuntimeBridge.isInWorld()) {
            currentRenderW = outW;
            currentRenderH = outH;
            McRuntimeBridge.setWorldRenderResolution(rt, outW, outH);
            resetHistoryNextFrame = true;
            return;
        }

        int renderW = mode.computeRenderDimension(outW);
        int renderH = mode.computeRenderDimension(outH);
        currentRenderW = renderW;
        currentRenderH = renderH;

        McRuntimeBridge.setWorldRenderResolution(rt, renderW, renderH);
        worldDownscaledThisFrame = true;
    }

    /**
     * Called inside GameRenderer.renderLevel (on ViewportEvent / RenderLevelStageEvent) to ensure
     * the 3D world viewport and block atlas mipmap bias stay locked for crisp distant blocks.
     */
    public static void onWorldViewportLock() {
        if (!worldDownscaledThisFrame || worldUpscaledThisFrame) return;
        Object rt = McRuntimeBridge.getMainRenderTarget();
        if (rt != null) {
            McRuntimeBridge.setWorldRenderResolution(rt, currentRenderW, currentRenderH);
        }
    }

    public static void onWorldRenderComplete() {
        if (!worldDownscaledThisFrame || worldUpscaledThisFrame) {
            return;
        }
        worldUpscaledThisFrame = true;

        Object rt = McRuntimeBridge.getMainRenderTarget();
        if (rt == null) return;

        // Immediately restore native resolution (currentOutW x currentOutH) on both RenderTarget and Window
        // so the player's First-Person Hand and 2D HUD / Hotbar / Crosshair / Menus render at 100% native resolution!
        McRuntimeBridge.setWorldRenderResolution(rt, currentOutW, currentOutH);

        int mainFboId = McRuntimeBridge.getRenderTargetFboId(rt);
        int mainColorTex = McRuntimeBridge.getRenderTargetColorTexId(rt);
        int mainDepthTex = McRuntimeBridge.getRenderTargetDepthTexId(rt);
        if (mainColorTex <= 0) {
            return;
        }

        BstDlssConfig cfg = BstDlssConfig.get();
        boolean wantHardwareNgx = BstDlssNative.isHardwareNgxActive() && cfg.isPreferHardwareNgx();

        if (!ensureSessionResources(currentRenderW, currentRenderH, currentOutW, currentOutH, cfg.getQualityMode(), cfg.getDlssPreset(), wantHardwareNgx)) {
            return;
        }

        // Save full OpenGL state so Minecraft's GlStateManager cache is never desynchronized
        boolean depthTestWasOn = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean blendWasOn     = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean cullWasOn      = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean scissorWasOn   = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depthMaskWasOn = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        COLOR_MASK_BUF.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, COLOR_MASK_BUF);
        boolean cmR = COLOR_MASK_BUF.get(0) != 0;
        boolean cmG = COLOR_MASK_BUF.get(1) != 0;
        boolean cmB = COLOR_MASK_BUF.get(2) != 0;
        boolean cmA = COLOR_MASK_BUF.get(3) != 0;

        int prevProgram   = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao       = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevActiveTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int prevSampler0  = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int prevTex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int prevTex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int prevTex2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDepthMask(false);
        GL11.glColorMask(true, true, true, true);

        // Compute 6-DOF camera velocity for per-pixel Motion Vectors
        System.arraycopy(currPose, 0, prevPose, 0, 5);
        McRuntimeBridge.getCameraPose(currPose);
        if (resetHistoryNextFrame) {
            System.arraycopy(currPose, 0, prevPose, 0, 5);
        }

        float deltaYawDeg = wrapAngleDeg(currPose[0] - prevPose[0]);
        float deltaPitchDeg = currPose[1] - prevPose[1];
        float dx = currPose[2] - prevPose[2];
        float dy = currPose[3] - prevPose[3];
        float dz = currPose[4] - prevPose[4];

        // Guard against initial spawn, pause, or teleport jumps
        if (resetHistoryNextFrame || Math.abs(deltaYawDeg) > 15.0f || Math.abs(deltaPitchDeg) > 15.0f || (dx * dx + dy * dy + dz * dz) > 4.0f) {
            deltaYawDeg = 0.0f;
            deltaPitchDeg = 0.0f;
            dx = 0.0f;
            dy = 0.0f;
            dz = 0.0f;
        }

        float fovYDeg = McRuntimeBridge.getCameraFov();
        if (fovYDeg < 30.0f || fovYDeg > 150.0f) fovYDeg = 70.0f;
        float aspect = (float) currentRenderW / Math.max(1.0f, (float) currentRenderH);
        float tanHalfFovY = (float) Math.tan(Math.toRadians(fovYDeg * 0.5f));
        float tanHalfFovX = aspect * tanHalfFovY;

        // NVIDIA DLSS Motion Vector convention: (PreviousPixel - CurrentPixel)
        // Turning right (deltaYaw > 0) -> previous was to the right (positive mvX)
        // Looking down (deltaPitch > 0) -> previous was lower (negative mvY)
        float rotNdcX = (float) Math.toRadians(deltaYawDeg) / Math.max(0.01f, tanHalfFovX);
        float rotNdcY = -(float) Math.toRadians(deltaPitchDeg) / Math.max(0.01f, tanHalfFovY);

        // Transform world movement to camera view space
        float camYawRad = (float) Math.toRadians(currPose[0]);
        float cosY = (float) Math.cos(camYawRad);
        float sinY = (float) Math.sin(camYawRad);
        float viewDx = cosY * dx - sinY * dz;
        float viewDy = dy;
        float viewDz = sinY * dx + cosY * dz;

        float transNdcX = viewDx / Math.max(0.01f, tanHalfFovX);
        float transNdcY = -viewDy / Math.max(0.01f, tanHalfFovY);
        float transNdcZ = viewDz;

        float uvScaleX = (float) currentRenderW / (float) currentOutW;
        float uvScaleY = (float) currentRenderH / (float) currentOutH;

        float jitterX = 0.0f;
        float jitterY = 0.0f;
        frameIndex++;

        // Step 1: MRT Extract Pass -> writes Color, R32F Depth, and RG16F Motion Vectors at (renderW x renderH)
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mrtExtractFbo);
        GL11.glViewport(0, 0, currentRenderW, currentRenderH);
        GlShaderManager.runMvExtractPass(
            mainColorTex,
            mainDepthTex > 0 ? mainDepthTex : mainColorTex,
            uvScaleX, uvScaleY,
            currentRenderW, currentRenderH,
            jitterX, jitterY,
            rotNdcX, rotNdcY,
            transNdcX, transNdcY, transNdcZ,
            cfg.isPixelArtCrispFilter()
        );

        // Unbind MRT FBO and flush pipeline before NGX or fallback evaluate
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GL11.glFlush();

        // Step 2: Evaluate Hardware NGX DLSS on RTX Tensor Cores (or OpenGL Temporal Fallback)
        boolean evaluatedNgx = false;
        if (sessionHardwareNgx) {
            long evalUs = BstDlssNative.nativeEvaluateDlss(
                jitterX, jitterY,
                1.0f, 1.0f,
                0.0f,
                resetHistoryNextFrame,
                1.0f,
                smoothedFrameTimeMs
            );
            if (evalUs >= 0L) {
                evaluatedNgx = true;
                float evalMs = evalUs / 1000.0f;
                smoothedDlssEvalMs = smoothedDlssEvalMs * 0.85f + evalMs * 0.15f;
            }
        }

        if (!evaluatedNgx && fallbackOutFbo != 0) {
            long t0 = System.nanoTime();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fallbackOutFbo);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE) {
                GL11.glViewport(0, 0, currentOutW, currentOutH);
                GlShaderManager.runFallbackUpscalePass(
                    glColorTex, glHistoryTex, glMvTex,
                    1.0f, 1.0f,
                    currentRenderW, currentRenderH,
                    currentOutW, currentOutH,
                    cfg.getSharpness(),
                    resetHistoryNextFrame
                );
            }
            float evalMs = (System.nanoTime() - t0) / 1_000_000.0f;
            smoothedDlssEvalMs = smoothedDlssEvalMs * 0.85f + evalMs * 0.15f;
        }

        // Step 3: Composite the full-resolution upscaled image (outW x outH) + RCAS sharpening + Pixel-Art Crisp Filter back to mainRenderTarget
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFboId);
        McRuntimeBridge.setGlViewport(0, 0, currentOutW, currentOutH);
        float fgBlend = (cfg.isFrameGenerationEnabled() && !resetHistoryNextFrame) ? 0.22f : 0.0f;
        GlShaderManager.runCompositeRcasPass(
            glOutTex,
            glHistoryTex,
            glMvTex,
            currentOutW, currentOutH,
            cfg.getSharpness(),
            cfg.isPixelArtCrispFilter(),
            fgBlend
        );

        // Restore mainColorTex filter to GL_NEAREST for Minecraft's own blit
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, mainColorTex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);

        // Only copy history texture when OpenGL fallback or FrameGen is active
        // (Hardware NGX DLSS maintains its own internal temporal history on Tensor Cores!)
        if (!evaluatedNgx || cfg.isFrameGenerationEnabled()) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, glHistoryTex);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, currentOutW, currentOutH);
        }

        resetHistoryNextFrame = false;

        // Restore exact OpenGL state for Minecraft's Hand & 2D HUD / GUI pass
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex2);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex1);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex0);
        GL33.glBindSampler(0, prevSampler0);
        GL13.glActiveTexture(prevActiveTex);

        GL30.glBindVertexArray(prevVao);
        GL20.glUseProgram(prevProgram);

        GL11.glDepthMask(depthMaskWasOn);
        GL11.glColorMask(cmR, cmG, cmB, cmA);
        if (depthTestWasOn) GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (blendWasOn)     GL11.glEnable(GL11.GL_BLEND);
        if (cullWasOn)      GL11.glEnable(GL11.GL_CULL_FACE);
        if (scissorWasOn)   GL11.glEnable(GL11.GL_SCISSOR_TEST);
    }

    public static void onFrameEnd() {
        if (worldDownscaledThisFrame && !worldUpscaledThisFrame) {
            onWorldRenderComplete();
        }

        long glfwWindow = 0L;
        try {
            glfwWindow = GLFW.glfwGetCurrentContext();
        } catch (Throwable ignored) {
        }

        BstDlssHudRenderer.renderOverlayAndMenu(
            glfwWindow,
            currentOutW, currentOutH,
            currentRenderW, currentRenderH,
            currentFps, smoothedFrameTimeMs,
            smoothedDlssEvalMs,
            sessionHardwareNgx
        );

        if (!FastBootEngine.isCompleted() && McRuntimeBridge.isGameReady()) {
            FastBootEngine.markBootComplete();
        }
    }

    private static void pollHotkeys(long glfwWindow) {
        if (glfwWindow == 0L) return;
        try {
            boolean f8 = GLFW.glfwGetKey(glfwWindow, GLFW.GLFW_KEY_F8) == GLFW.GLFW_PRESS;
            boolean f9 = GLFW.glfwGetKey(glfwWindow, GLFW.GLFW_KEY_F9) == GLFW.GLFW_PRESS;
            boolean f10 = GLFW.glfwGetKey(glfwWindow, GLFW.GLFW_KEY_F10) == GLFW.GLFW_PRESS;
            boolean esc = GLFW.glfwGetKey(glfwWindow, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;

            if (f8 && !prevKeyF8) {
                BstDlssHudRenderer.toggleMenu(glfwWindow);
            }
            if (esc && !prevKeyEsc && BstDlssHudRenderer.isMenuOpen()) {
                BstDlssHudRenderer.toggleMenu(glfwWindow);
            }
            if (f9 && !prevKeyF9) {
                BstDlssConfig cfg = BstDlssConfig.get();
                DlssQualityMode nextMode = cfg.getQualityMode().next();
                cfg.setQualityMode(nextMode);
                BstDlssHudRenderer.showToast(I18n.format("toast.mode", nextMode.getDisplayName()));
            }
            if (f10 && !prevKeyF10) {
                BstDlssConfig cfg = BstDlssConfig.get();
                cfg.setShowTelemetryOverlay(!cfg.isShowTelemetryOverlay());
                BstDlssHudRenderer.showToast(I18n.format("toast.hud", cfg.isShowTelemetryOverlay() ? I18n.get("status.on") : I18n.get("status.off")));
            }

            prevKeyF8 = f8;
            prevKeyF9 = f9;
            prevKeyF10 = f10;
            prevKeyEsc = esc;
        } catch (Throwable ignored) {
        }
    }

    private static boolean ensureSessionResources(
        int renderW, int renderH,
        int outW, int outH,
        DlssQualityMode quality,
        DlssPreset preset,
        boolean wantHardwareNgx
    ) {
        if (renderW == sessionRenderW &&
            renderH == sessionRenderH &&
            outW == sessionOutW &&
            outH == sessionOutH &&
            quality == sessionQuality &&
            preset == sessionPreset &&
            wantHardwareNgx == sessionWantHardwareNgx &&
            mrtExtractFbo != 0) {
            return true;
        }

        if (mrtExtractFbo != 0) {
            GL30.glDeleteFramebuffers(mrtExtractFbo);
            mrtExtractFbo = 0;
        }
        if (fallbackOutFbo != 0) {
            GL30.glDeleteFramebuffers(fallbackOutFbo);
            fallbackOutFbo = 0;
        }
        if (ownsFallbackTextures) {
            if (glColorTex != 0) GL11.glDeleteTextures(glColorTex);
            if (glDepthTex != 0) GL11.glDeleteTextures(glDepthTex);
            if (glMvTex != 0)    GL11.glDeleteTextures(glMvTex);
            if (glOutTex != 0)   GL11.glDeleteTextures(glOutTex);
            ownsFallbackTextures = false;
        }
        if (glHistoryTex != 0) {
            GL11.glDeleteTextures(glHistoryTex);
            glHistoryTex = 0;
        }

        sessionRenderW = renderW;
        sessionRenderH = renderH;
        sessionOutW = outW;
        sessionOutH = outH;
        sessionQuality = quality;
        sessionPreset = preset;
        sessionWantHardwareNgx = wantHardwareNgx;
        sessionHardwareNgx = false;
        resetHistoryNextFrame = true;

        if (wantHardwareNgx && quality.isEnabled()) {
            int[] sharedTexIds = BstDlssNative.nativeCreateSession(
                renderW, renderH,
                outW, outH,
                quality.getNgxPerfQualityValue(),
                preset.getNgxPresetValue()
            );
            if (sharedTexIds != null && sharedTexIds.length == 4 && sharedTexIds[0] > 0) {
                glColorTex = sharedTexIds[0];
                glDepthTex = sharedTexIds[1];
                glMvTex    = sharedTexIds[2];
                glOutTex   = sharedTexIds[3];
                sessionHardwareNgx = true;
            }
        }

        if (!sessionHardwareNgx) {
            glColorTex = createGlTex(renderW, renderH, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            glDepthTex = createGlTex(renderW, renderH, GL30.GL_R32F,  GL11.GL_RED,  GL11.GL_FLOAT);
            glMvTex    = createGlTex(renderW, renderH, GL30.GL_RG16F, GL30.GL_RG,   GL11.GL_FLOAT);
            glOutTex   = createGlTex(outW,    outH,    GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);
            ownsFallbackTextures = true;

            fallbackOutFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fallbackOutFbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glOutTex, 0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        }

        glHistoryTex = createGlTex(outW, outH, GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE);

        mrtExtractFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mrtExtractFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, glColorTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, glDepthTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2, GL11.GL_TEXTURE_2D, glMvTex, 0);

        IntBuffer drawBufs = BufferUtils.createIntBuffer(3);
        drawBufs.put(GL30.GL_COLOR_ATTACHMENT0);
        drawBufs.put(GL30.GL_COLOR_ATTACHMENT1);
        drawBufs.put(GL30.GL_COLOR_ATTACHMENT2);
        drawBufs.flip();
        GL20.glDrawBuffers(drawBufs);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        return true;
    }

    private static int createGlTex(int w, int h, int internalFormat, int format, int type) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, format, type, 0L);
        return tex;
    }

    private static float wrapAngleDeg(float deg) {
        while (deg > 180.0f) deg -= 360.0f;
        while (deg < -180.0f) deg += 360.0f;
        return deg;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
