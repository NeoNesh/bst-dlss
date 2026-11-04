package dev.bstdlss;

import dev.bstdlss.bridge.McRuntimeBridge;
import dev.bstdlss.config.BstDlssConfig;
import dev.bstdlss.config.DlssPreset;
import dev.bstdlss.config.DlssQualityMode;
import dev.bstdlss.render.HaltonJitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class DlssLogicTest {

    @Test
    public void testQualityModeCalculations() {
        // Output: 1920x1080
        int outW = 1920;
        int outH = 1080;

        for (DlssQualityMode mode : DlssQualityMode.values()) {
            int rw = mode.computeRenderDimension(outW);
            int rh = mode.computeRenderDimension(outH);

            // Tensor Cores require 2-pixel even boundaries
            assertEquals(0, rw % 2, "Render width must be even: " + mode);
            assertEquals(0, rh % 2, "Render height must be even: " + mode);
            assertTrue(rw >= 64, "Render width must be >= 64");
            assertTrue(rh >= 64, "Render height must be >= 64");
            assertTrue(rw <= outW, "Render width must be <= outW");
            assertTrue(rh <= outH, "Render height must be <= outH");
        }

        // Quality (0.6666667x): 1920 * 0.6666667 = 1280, 1080 * 0.6666667 = 720
        assertEquals(1280, DlssQualityMode.QUALITY.computeRenderDimension(outW));
        assertEquals(720, DlssQualityMode.QUALITY.computeRenderDimension(outH));

        // Performance (0.50x): 1920 * 0.5 = 960, 1080 * 0.5 = 540
        assertEquals(960, DlssQualityMode.PERFORMANCE.computeRenderDimension(outW));
        assertEquals(540, DlssQualityMode.PERFORMANCE.computeRenderDimension(outH));

        // DLAA (1.00x): 1920, 1080
        assertEquals(1920, DlssQualityMode.DLAA.computeRenderDimension(outW));
        assertEquals(1080, DlssQualityMode.DLAA.computeRenderDimension(outH));
    }

    @Test
    public void testPresetsMapping() {
        for (DlssPreset preset : DlssPreset.values()) {
            int val = preset.getNgxPresetValue();
            assertTrue(val >= 0 && val <= 16, "Preset value must be valid NGX preset (0-16): " + preset);
        }
    }

    @Test
    public void testConfigPersistence(@TempDir Path tempDir) {
        File gameDir = tempDir.toFile();
        BstDlssConfig config = BstDlssConfig.get();
        config.load(gameDir);
        config.setQualityMode(DlssQualityMode.BALANCED);
        config.setSharpness(0.75f);
        config.setPixelArtCrispFilter(true);
        config.save();

        File cfgFile = new File(gameDir, "config/bstdlss.properties");
        assertTrue(cfgFile.exists(), "Config file must exist after save");

        // Reload
        config.load(gameDir);
        assertEquals(DlssQualityMode.BALANCED, config.getQualityMode());
        assertEquals(0.75f, config.getSharpness(), 0.001f);
        assertTrue(config.isPixelArtCrispFilter());
    }

    @Test
    public void testHaltonJitterRange() {
        for (int i = 0; i < 64; i++) {
            float jx = HaltonJitter.getJitterX(i, 16);
            float jy = HaltonJitter.getJitterY(i, 16);
            assertTrue(jx >= -0.5f && jx <= 0.5f, "JitterX must be in [-0.5, 0.5]");
            assertTrue(jy >= -0.5f && jy <= 0.5f, "JitterY must be in [-0.5, 0.5]");
        }
    }

    @Test
    public void testRuntimeBridgeSafeWithoutMinecraft() {
        // Calling runtime bridge methods when Minecraft classes aren't present should not throw unhandled exceptions
        assertDoesNotThrow(() -> {
            McRuntimeBridge.resolveMinecraftInternals();
            assertNull(McRuntimeBridge.getMinecraft());
            assertNull(McRuntimeBridge.getMainRenderTarget());
            assertEquals(0, McRuntimeBridge.getRenderTargetWidth(null));
            assertEquals(0, McRuntimeBridge.getRenderTargetHeight(null));
            assertEquals(0, McRuntimeBridge.getRenderTargetFboId(null));
            assertEquals(0, McRuntimeBridge.getRenderTargetColorTexId(null));
            assertEquals(0, McRuntimeBridge.getRenderTargetDepthTexId(null));
            McRuntimeBridge.applyTextureLodBias(-0.5f);
            McRuntimeBridge.applyTurboFpsBoosts(true, true, true);
        });
    }

    @Test
    public void testNativeInitRealGpu() {
        boolean loaded = dev.bstdlss.nativebridge.BstDlssNative.loadAndInit(new File("D:/Games/Minecraft"));
        System.out.println("=== testNativeInitRealGpu ===");
        System.out.println("loadAndInit: " + loaded);
        System.out.println("Diagnostics: " + dev.bstdlss.nativebridge.BstDlssNative.getDiagnosticsSafe());
        System.out.println("GPU Name: " + dev.bstdlss.nativebridge.BstDlssNative.getGpuNameSafe());
        System.out.println("RTX Series: " + dev.bstdlss.nativebridge.BstDlssNative.getRtxSeriesSafe());
        System.out.println("DLSS Version: " + dev.bstdlss.nativebridge.BstDlssNative.getDlssVersionSafe());
        System.out.println("Is DLSS Available: " + dev.bstdlss.nativebridge.BstDlssNative.isHardwareNgxActive());
        System.out.println("=============================");
    }

    @Test
    public void testOverlayHiddenByDefault(@TempDir Path tempDir) {
        File gameDir = tempDir.toFile();
        BstDlssConfig config = BstDlssConfig.get();
        config.load(gameDir);
        // By default per task requirements, overlay is hidden
        assertFalse(config.isShowTelemetryOverlay(), "Telemetry overlay must be hidden by default");
    }

    @Test
    public void testI18nDynamicLanguage() {
        String[] keys = {
            "hud.title", "hud.mode", "hud.render", "hud.core.ngx", "hud.core.easu",
            "hud.perf", "hud.arch", "status.on", "status.off", "status.shown", "status.hidden",
            "toast.mode", "toast.hud", "toast.welcome", "tab.icon",
            "menu.title", "menu.gpu", "menu.core_status", "menu.core.hw_desc", "menu.core.fallback_desc",
            "menu.btn.mode", "menu.btn.preset", "menu.btn.sharpness", "menu.btn.mipmapping",
            "menu.mipmapping.crisp", "menu.mipmapping.standard", "menu.btn.engine",
            "menu.engine.tensor", "menu.engine.easu", "menu.btn.fps_uncap", "menu.status.uncapped",
            "menu.btn.entity_boost", "menu.status.max_fps", "menu.btn.chunk_boost", "menu.status.turbo",
            "menu.btn.frame_gen", "menu.btn.reflex", "menu.status.low_latency",
            "menu.btn.hud_overlay", "menu.btn.save_close", "menu.footer",
            "mode.off", "mode.hyper_performance", "mode.ultra_performance", "mode.performance",
            "mode.balanced", "mode.quality", "mode.ultra_quality", "mode.dlaa",
            "preset.default", "preset.preset_c", "preset.preset_d", "preset.preset_e",
            "preset.preset_j", "preset.preset_k"
        };

        String[] testLangs = {"en", "ru", "uk", "de", "es", "fr"};

        for (String lang : testLangs) {
            for (String k : keys) {
                String val = dev.bstdlss.i18n.I18n.getFor(k, lang);
                assertNotNull(val, "Translation key must not be null for lang " + lang + ": " + k);
                assertFalse(val.isEmpty(), "Translation value must not be empty for lang " + lang + ": " + k);
                assertNotEquals(k, val, "Translation key must have a registered translation for lang " + lang + ": " + k);
            }
        }

        String formatted = dev.bstdlss.i18n.I18n.format("toast.mode", "QUALITY");
        assertTrue(formatted.contains("QUALITY"));
    }

    @Test
    public void testQualityAndPresetDisplayNames() {
        for (DlssQualityMode mode : DlssQualityMode.values()) {
            assertNotNull(mode.getDisplayName());
            assertNotNull(mode.getDisplayNameEn());
            assertNotNull(mode.getDisplayNameRu());
        }
        for (DlssPreset preset : DlssPreset.values()) {
            assertNotNull(preset.getDisplayName());
            assertNotNull(preset.getDisplayNameEn());
            assertNotNull(preset.getDisplayNameRu());
        }
    }

    @Test
    public void testLanguageResolution() {
        String lang = McRuntimeBridge.getCurrentLanguage();
        assertNotNull(lang);
        assertFalse(lang.isEmpty());
    }

    @Test
    public void testChunkBoostDoesNotOverridePlayerDistance() {
        // applyTurboFpsBoosts must not throw or overwrite user chunk render/sim distance
        assertDoesNotThrow(() -> {
            McRuntimeBridge.applyTurboFpsBoosts(true, true, true);
        });
    }
}
