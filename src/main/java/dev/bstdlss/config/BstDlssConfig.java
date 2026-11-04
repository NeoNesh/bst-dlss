package dev.bstdlss.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Standalone configuration manager for BST DLSS (zero external dependencies).
 * Includes non-conflicting DLSS 4 + CPU/GPU Turbo FPS Boost features.
 */
public final class BstDlssConfig {
    private static final BstDlssConfig INSTANCE = new BstDlssConfig();

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

    private BstDlssConfig() {
    }

    public static BstDlssConfig get() {
        return INSTANCE;
    }

    public synchronized void load(File gameDir) {
        File configDir = new File(gameDir, "config");
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        this.configFile = new File(configDir, "bstdlss.properties");
        if (!this.configFile.exists()) {
            save();
            return;
        }
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(this.configFile)) {
            props.load(fis);
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
        } catch (IOException ignored) {
        }
    }

    public synchronized void save() {
        if (this.configFile == null) {
            this.configFile = new File("config/bstdlss.properties");
            if (this.configFile.getParentFile() != null) {
                this.configFile.getParentFile().mkdirs();
            }
        }
        Properties props = new Properties();
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
    }

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
