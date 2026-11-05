package dev.bstdlss;

import dev.bstdlss.boot.FastBootEngine;
import dev.bstdlss.bridge.McRuntimeBridge;
import dev.bstdlss.config.BstDlssConfig;
import dev.bstdlss.config.DlssQualityMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class FastBootTest {

    static class DummyStaticTarget {
        public static final Object IMMUTABLE_TARGET = new String("INITIAL");
    }

    @BeforeEach
    public void setup() {
        FastBootEngine.resetForTesting();
    }

    @Test
    public void testFastBootConfigDefaultsAndToggle(@TempDir Path tempDir) {
        File gameDir = tempDir.toFile();
        BstDlssConfig config = BstDlssConfig.get();
        config.load(gameDir);

        // 1. Fast Boot must be enabled by default per task requirements
        assertTrue(config.isFastBoot(), "Fast Boot must be enabled by default");

        // 2. Both config files must exist: bstdlss.properties and bst-dlss.json
        File propsFile = new File(gameDir, "config/bstdlss.properties");
        File jsonFile = new File(gameDir, "config/bst-dlss.json");
        assertTrue(propsFile.exists(), "bstdlss.properties must exist");
        assertTrue(jsonFile.exists(), "bst-dlss.json must exist");

        // 3. Test toggle & persistence in both formats
        config.setFastBoot(false);
        config.save();

        config.load(gameDir);
        assertFalse(config.isFastBoot(), "Fast Boot setting must persist after save & reload");

        // Verify JSON contents contain "fastBoot": false
        assertDoesNotThrow(() -> {
            String jsonContent = new String(Files.readAllBytes(jsonFile.toPath()));
            assertTrue(jsonContent.contains("\"fastBoot\": false"), "JSON must reflect fastBoot setting");
        });

        // Re-enable
        config.setFastBoot(true);
        config.save();
        config.load(gameDir);
        assertTrue(config.isFastBoot(), "Fast Boot must be re-enabled");
    }

    @Test
    public void testJsonDirectLoading(@TempDir Path tempDir) throws Exception {
        File gameDir = tempDir.toFile();
        File configDir = new File(gameDir, "config");
        configDir.mkdirs();
        File jsonFile = new File(configDir, "bst-dlss.json");

        String customJson = "{\n" +
            "  \"fastBoot\": true,\n" +
            "  \"qualityMode\": \"PERFORMANCE\",\n" +
            "  \"dlssPreset\": \"PRESET_J\",\n" +
            "  \"sharpness\": 0.45,\n" +
            "  \"frameGenerationEnabled\": true,\n" +
            "  \"turboFpsUncap\": false\n" +
            "}";
        Files.write(jsonFile.toPath(), customJson.getBytes());

        BstDlssConfig config = BstDlssConfig.get();
        config.load(gameDir);

        assertTrue(config.isFastBoot());
        assertEquals(DlssQualityMode.PERFORMANCE, config.getQualityMode());
        assertEquals(0.45f, config.getSharpness(), 0.01f);
        assertTrue(config.isFrameGenerationEnabled());
        assertFalse(config.isTurboFpsUncap());
    }

    @Test
    public void testFastBootEngineStartAndFallbackWithoutMinecraft() {
        File gameDir = new File("build/test-game-dir");
        gameDir.mkdirs();

        assertDoesNotThrow(() -> {
            FastBootEngine.start(gameDir);
            assertTrue(FastBootEngine.isStarted(), "FastBootEngine must mark as started");

            // Repeated start call should be idempotent
            FastBootEngine.start(gameDir);
            assertTrue(FastBootEngine.isStarted());

            // Complete boot
            FastBootEngine.markBootComplete();
            assertTrue(FastBootEngine.isCompleted(), "FastBootEngine must mark as completed");

            // Repeated markBootComplete should be idempotent
            FastBootEngine.markBootComplete();
            assertTrue(FastBootEngine.isCompleted());

            // Estimated seconds saved must be between 1.0 and 3.0 seconds
            double saved = FastBootEngine.getEstimatedSecondsSaved();
            assertTrue(saved >= 1.0 && saved <= 3.0, "Startup acceleration should be 1 to 3 seconds: " + saved);
        });
    }

    @Test
    public void testWorkerThreadPriorityTuning() throws Exception {
        CountDownLatch threadReady = new CountDownLatch(1);
        CountDownLatch threadFinish = new CountDownLatch(1);

        Thread worker = new Thread(() -> {
            threadReady.countDown();
            try {
                threadFinish.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
        }, "Worker-Main-Test-Pool");

        worker.setPriority(Thread.NORM_PRIORITY);
        worker.start();
        assertTrue(threadReady.await(1, TimeUnit.SECONDS));

        try {
            assertEquals(Thread.NORM_PRIORITY, worker.getPriority());

            // Tune priorities
            FastBootEngine.tuneWorkerThreadPriorities();
            assertEquals(7, worker.getPriority(), "Worker thread priority should be boosted to 7");
            assertTrue(FastBootEngine.isThreadsTuned());

            // Normalize back to 5
            FastBootEngine.normalizeWorkerThreadPriorities();
            assertEquals(Thread.NORM_PRIORITY, worker.getPriority(), "Worker thread priority should be normalized back to 5");
        } finally {
            threadFinish.countDown();
            worker.join(1000);
        }
    }

    @Test
    public void testSplashLimiterThrottle() {
        FastBootEngine.resetForTesting();
        BstDlssConfig.get().setFastBoot(true);

        // Simulate 5 quick successive early frames
        for (int i = 0; i < 5; i++) {
            FastBootEngine.throttleEarlyWindowFps();
        }

        assertTrue(FastBootEngine.getThrottledFramesCount() >= 1,
            "Early window FPS limiter should have throttled at least 1 fast frame");
    }

    @Test
    public void testStaticFinalFieldUnsafeFallback() throws Exception {
        Field f = DummyStaticTarget.class.getDeclaredField("IMMUTABLE_TARGET");
        assertEquals("INITIAL", DummyStaticTarget.IMMUTABLE_TARGET);

        boolean mutated = FastBootEngine.setStaticFinalField(f, "FAST_BOOT_OVERRIDE");
        assertTrue(mutated, "Should successfully mutate static final field");
        assertEquals("FAST_BOOT_OVERRIDE", f.get(null),
            "Static final field in memory should be successfully overridden via reflection / Unsafe");
        assertEquals("FAST_BOOT_OVERRIDE", DummyStaticTarget.IMMUTABLE_TARGET,
            "Static final field reference should reflect the mutated object");
    }

    @Test
    public void testMcRuntimeBridgeNewMethods() {
        assertDoesNotThrow(() -> {
            assertNull(McRuntimeBridge.getCurrentScreen());
            assertFalse(McRuntimeBridge.isScreenOpen());
            assertFalse(McRuntimeBridge.isLoadingOverlayActive());
            assertFalse(McRuntimeBridge.isTitleScreen());
            assertFalse(McRuntimeBridge.isGameReady());
            McRuntimeBridge.resetSwapIntervalState();
        });
    }

    @Test
    public void testWorkerThreadPriorityTuningMultiplePools() throws Exception {
        String[] poolNames = new String[]{
            "ResourceReload-Executor-1",
            "modloading-worker-2",
            "Texture-Stitcher-Thread",
            "ModelLoader-Pool-0"
        };
        Thread[] threads = new Thread[poolNames.length];
        CountDownLatch finish = new CountDownLatch(1);

        for (int i = 0; i < poolNames.length; i++) {
            Thread t = new Thread(() -> {
                try {
                    finish.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
            }, poolNames[i]);
            t.setPriority(Thread.NORM_PRIORITY);
            t.start();
            threads[i] = t;
        }

        try {
            FastBootEngine.tuneWorkerThreadPriorities();
            for (Thread t : threads) {
                assertEquals(7, t.getPriority(), "Thread " + t.getName() + " should be boosted to priority 7");
            }

            FastBootEngine.normalizeWorkerThreadPriorities();
            for (Thread t : threads) {
                assertEquals(Thread.NORM_PRIORITY, t.getPriority(), "Thread " + t.getName() + " should be normalized to 5");
            }
        } finally {
            finish.countDown();
            for (Thread t : threads) {
                t.join(500);
            }
        }
    }

    @Test
    public void testQuotedJsonParsingTolerance(@TempDir Path tempDir) throws Exception {
        File gameDir = tempDir.toFile();
        File configDir = new File(gameDir, "config");
        configDir.mkdirs();
        File jsonFile = new File(configDir, "bst-dlss.json");

        String quotedJson = "{\n" +
            "  \"fastBoot\": \"true\",\n" +
            "  \"qualityMode\": \"BALANCED\",\n" +
            "  \"dlssPreset\": \"PRESET_K\",\n" +
            "  \"sharpness\": \"0.35\",\n" +
            "  \"frameGenerationEnabled\": \"true\",\n" +
            "  \"turboFpsUncap\": \"true\"\n" +
            "}";
        Files.write(jsonFile.toPath(), quotedJson.getBytes());

        BstDlssConfig config = BstDlssConfig.get();
        config.load(gameDir);

        assertTrue(config.isFastBoot());
        assertEquals(dev.bstdlss.config.DlssQualityMode.BALANCED, config.getQualityMode());
        assertEquals(0.35f, config.getSharpness(), 0.01f);
        assertTrue(config.isFrameGenerationEnabled());
        assertTrue(config.isTurboFpsUncap());
    }

    @Test
    public void testDfuBackgroundWorkerFallback() {
        assertDoesNotThrow(() -> {
            Object dfu = FastBootEngine.getOrAwaitRealDataFixer();
            // In unit test without Minecraft, returns null gracefully without throwing
            assertNull(dfu);
        });
    }

    @Test
    public void testVersionBump() {
        assertEquals("1.1.0", BstDlssMod.VERSION, "Mod version should be 1.1.0");
    }
}
