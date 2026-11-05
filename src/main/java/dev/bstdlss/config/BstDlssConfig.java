package dev.bstdlss.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Standalone configuration manager for BST DLSS (zero external dependencies).
 * Includes Fast Boot (Turbo Startup Acceleration) + DLSS 4 + CPU/GPU Turbo FPS Boost features.
 * Supports both bstdlss.properties and bst-dlss.json configuration formats.
 */
public final class BstDlssConfig {
    private static final BstDlssConfig INSTANCE = new BstDlssConfig();

    private boolean fastBoot = true;
    private DlssQualityMode qualityMode = DlssQualityMode.QUALITY;
    private DlssPreset dlssPreset = DlssPreset.PRESET_E;
    private float sharpness = 0.20f;
    private boolean frameGenerationEnabled = false;
    private boolean turboFpsUncap = true;
    private boolean smartEntityBoost = true;
    private boolean chunkSimulationBoost = true;
    private boolean reflexLowLatency = true;
    private boolean pixelArtCrispFilter = true;
    private boolean showTelemetryOverlay = false;
    private boolean preferHardwareNgx = true;

    private File configFile;
    private File jsonConfigFile;

    private BstDlssConfig() {
    }

    public static BstDlssConfig get() {
        return INSTANCE;
    }

    public synchronized void load(File gameDir) {
        if (gameDir == null) {
            gameDir = new File(".");
        }
        File configDir = new File(gameDir, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.configFile = new File(configDir, "bstdlss.properties");
        this.jsonConfigFile = new File(configDir, "bst-dlss.json");

        File rootJson = new File(gameDir, "bst-dlss.json");
        if (!this.jsonConfigFile.exists() && rootJson.exists()) {
            this.jsonConfigFile = rootJson;
        }

        boolean loaded = false;
        if (this.jsonConfigFile != null && this.jsonConfigFile.exists()) {
            loaded = loadFromJson(this.jsonConfigFile);
        }
        if (!loaded && this.configFile != null && this.configFile.exists()) {
            loaded = loadFromProperties(this.configFile);
        }

        if (!loaded) {
            save();
        }
    }

    private boolean loadFromProperties(File file) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);
            this.fastBoot = Boolean.parseBoolean(props.getProperty("fastBoot", "true"));
            String modeStr = props.getProperty("qualityMode", DlssQualityMode.QUALITY.name());
            try {
                this.qualityMode = DlssQualityMode.valueOf(modeStr);
            } catch (IllegalArgumentException ignored) {
                this.qualityMode = DlssQualityMode.QUALITY;
            }
            String presetStr = props.getProperty("dlssPreset", DlssPreset.PRESET_E.name());
            try {
                this.dlssPreset = DlssPreset.valueOf(presetStr);
            } catch (IllegalArgumentException ignored) {
                this.dlssPreset = DlssPreset.PRESET_E;
            }
            this.sharpness = clamp(parseFloat(props.getProperty("sharpness"), 0.20f), 0.0f, 1.0f);
            this.frameGenerationEnabled = Boolean.parseBoolean(props.getProperty("frameGenerationEnabled", "false"));
            this.turboFpsUncap = Boolean.parseBoolean(props.getProperty("turboFpsUncap", "true"));
            this.smartEntityBoost = Boolean.parseBoolean(props.getProperty("smartEntityBoost", "true"));
            this.chunkSimulationBoost = Boolean.parseBoolean(props.getProperty("chunkSimulationBoost", "true"));
            this.reflexLowLatency = Boolean.parseBoolean(props.getProperty("reflexLowLatency", "true"));
            this.pixelArtCrispFilter = Boolean.parseBoolean(props.getProperty("pixelArtCrispFilter", "true"));
            this.showTelemetryOverlay = Boolean.parseBoolean(props.getProperty("showTelemetryOverlay", "false"));
            this.preferHardwareNgx = Boolean.parseBoolean(props.getProperty("preferHardwareNgx", "true"));
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean loadFromJson(File file) {
        try {
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            this.fastBoot = parseJsonBoolean(json, "fastBoot", true);
            String modeStr = parseJsonString(json, "qualityMode", DlssQualityMode.QUALITY.name());
            try {
                this.qualityMode = DlssQualityMode.valueOf(modeStr);
            } catch (IllegalArgumentException ignored) {
                this.qualityMode = DlssQualityMode.QUALITY;
            }
            String presetStr = parseJsonString(json, "dlssPreset", DlssPreset.PRESET_E.name());
            try {
                this.dlssPreset = DlssPreset.valueOf(presetStr);
            } catch (IllegalArgumentException ignored) {
                this.dlssPreset = DlssPreset.PRESET_E;
            }
            this.sharpness = clamp(parseJsonFloat(json, "sharpness", 0.20f), 0.0f, 1.0f);
            this.frameGenerationEnabled = parseJsonBoolean(json, "frameGenerationEnabled", false);
            this.turboFpsUncap = parseJsonBoolean(json, "turboFpsUncap", true);
            this.smartEntityBoost = parseJsonBoolean(json, "smartEntityBoost", true);
            this.chunkSimulationBoost = parseJsonBoolean(json, "chunkSimulationBoost", true);
            this.reflexLowLatency = parseJsonBoolean(json, "reflexLowLatency", true);
            this.pixelArtCrispFilter = parseJsonBoolean(json, "pixelArtCrispFilter", true);
            this.showTelemetryOverlay = parseJsonBoolean(json, "showTelemetryOverlay", false);
            this.preferHardwareNgx = parseJsonBoolean(json, "preferHardwareNgx", true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public synchronized void save() {
        if (this.configFile == null) {
            this.configFile = new File("config/bstdlss.properties");
        }
        if (this.jsonConfigFile == null) {
            this.jsonConfigFile = new File("config/bst-dlss.json");
        }
        if (this.configFile.getParentFile() != null) {
            this.configFile.getParentFile().mkdirs();
        }
        if (this.jsonConfigFile.getParentFile() != null) {
            this.jsonConfigFile.getParentFile().mkdirs();
        }

        // 1. Save Properties
        Properties props = new Properties();
        props.setProperty("fastBoot", Boolean.toString(this.fastBoot));
        props.setProperty("qualityMode", this.qualityMode.name());
        props.setProperty("dlssPreset", this.dlssPreset.name());
        props.setProperty("sharpness", Float.toString(this.sharpness));
        props.setProperty("frameGenerationEnabled", Boolean.toString(this.frameGenerationEnabled));
        props.setProperty("turboFpsUncap", Boolean.toString(this.turboFpsUncap));
        props.setProperty("smartEntityBoost", Boolean.toString(this.smartEntityBoost));
        props.setProperty("chunkSimulationBoost", Boolean.toString(this.chunkSimulationBoost));
        props.setProperty("reflexLowLatency", Boolean.toString(this.reflexLowLatency));
        props.setProperty("pixelArtCrispFilter", Boolean.toString(this.pixelArtCrispFilter));
        props.setProperty("showTelemetryOverlay", Boolean.toString(this.showTelemetryOverlay));
        props.setProperty("preferHardwareNgx", Boolean.toString(this.preferHardwareNgx));
        try (FileOutputStream fos = new FileOutputStream(this.configFile)) {
            props.store(fos, "BST DLSS - RTX 20xx-50xx Hardware Upscaler & FPS Boost Configuration");
        } catch (IOException ignored) {
        }

        // 2. Save JSON
        try {
            String jsonContent = String.format(java.util.Locale.ROOT,
                "{\n" +
                "  \"fastBoot\": %b,\n" +
                "  \"qualityMode\": \"%s\",\n" +
                "  \"dlssPreset\": \"%s\",\n" +
                "  \"sharpness\": %.2f,\n" +
                "  \"frameGenerationEnabled\": %b,\n" +
                "  \"turboFpsUncap\": %b,\n" +
                "  \"smartEntityBoost\": %b,\n" +
                "  \"chunkSimulationBoost\": %b,\n" +
                "  \"reflexLowLatency\": %b,\n" +
                "  \"pixelArtCrispFilter\": %b,\n" +
                "  \"showTelemetryOverlay\": %b,\n" +
                "  \"preferHardwareNgx\": %b\n" +
                "}\n",
                this.fastBoot,
                this.qualityMode.name(),
                this.dlssPreset.name(),
                this.sharpness,
                this.frameGenerationEnabled,
                this.turboFpsUncap,
                this.smartEntityBoost,
                this.chunkSimulationBoost,
                this.reflexLowLatency,
                this.pixelArtCrispFilter,
                this.showTelemetryOverlay,
                this.preferHardwareNgx
            );
            Files.write(this.jsonConfigFile.toPath(), jsonContent.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
        }
    }

    public boolean isFastBoot() { return fastBoot; }
    public void setFastBoot(boolean fastBoot) { this.fastBoot = fastBoot; save(); }

    public DlssQualityMode getQualityMode() { return qualityMode; }
    public void setQualityMode(DlssQualityMode qualityMode) { this.qualityMode = qualityMode; save(); }

    public DlssPreset getDlssPreset() { return dlssPreset; }
    public void setDlssPreset(DlssPreset dlssPreset) { this.dlssPreset = dlssPreset; save(); }

    public float getSharpness() { return sharpness; }
    public void setSharpness(float sharpness) { this.sharpness = clamp(sharpness, 0.0f, 1.0f); save(); }

    public boolean isFrameGenerationEnabled() { return frameGenerationEnabled; }
    public void setFrameGenerationEnabled(boolean frameGenerationEnabled) { this.frameGenerationEnabled = frameGenerationEnabled; save(); }

    public boolean isTurboFpsUncap() { return turboFpsUncap; }
    public void setTurboFpsUncap(boolean turboFpsUncap) { this.turboFpsUncap = turboFpsUncap; save(); }

    public boolean isSmartEntityBoost() { return smartEntityBoost; }
    public void setSmartEntityBoost(boolean smartEntityBoost) { this.smartEntityBoost = smartEntityBoost; save(); }

    public boolean isChunkSimulationBoost() { return chunkSimulationBoost; }
    public void setChunkSimulationBoost(boolean chunkSimulationBoost) { this.chunkSimulationBoost = chunkSimulationBoost; save(); }

    public boolean isReflexLowLatency() { return reflexLowLatency; }
    public void setReflexLowLatency(boolean reflexLowLatency) { this.reflexLowLatency = reflexLowLatency; save(); }

    public boolean isPixelArtCrispFilter() { return pixelArtCrispFilter; }
    public void setPixelArtCrispFilter(boolean pixelArtCrispFilter) { this.pixelArtCrispFilter = pixelArtCrispFilter; save(); }

    public boolean isShowTelemetryOverlay() { return showTelemetryOverlay; }
    public void setShowTelemetryOverlay(boolean showTelemetryOverlay) { this.showTelemetryOverlay = showTelemetryOverlay; save(); }

    public boolean isPreferHardwareNgx() { return preferHardwareNgx; }
    public void setPreferHardwareNgx(boolean preferHardwareNgx) { this.preferHardwareNgx = preferHardwareNgx; save(); }

    private static boolean parseJsonBoolean(String json, String key, boolean def) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?(true|false)\"?", Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return Boolean.parseBoolean(m.group(1));
        }
        return def;
    }

    private static String parseJsonString(String json, String key, String def) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1);
        }
        return def;
    }

    private static float parseJsonFloat(String json, String key, float def) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"?([0-9.]+)\"?");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return parseFloat(m.group(1), def);
        }
        return def;
    }

    private static float parseFloat(String s, float def) {
        if (s == null) return def;
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
