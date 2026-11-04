package dev.bstdlss.config;

/**
 * NVIDIA DLSS Super Resolution Quality Modes supported on RTX 20xx, 30xx, 40xx, and 50xx GPUs.
 */
public enum DlssQualityMode {
    OFF("Off (Native)", "Выкл (Родное 100%)", 1.00f, -1),
    HYPER_PERFORMANCE("Hyper Performance (25%)", "Гипер-ФПС (25%)", 0.25f, 3),
    ULTRA_PERFORMANCE("Ultra Performance (33%)", "Ультра-ФПС (33%)", 0.3333333f, 3),
    PERFORMANCE("Performance (50%)", "Производительность (50%)", 0.50f, 0),
    BALANCED("Balanced (58%)", "Баланс (58%)", 0.58f, 1),
    QUALITY("Quality (67%)", "Качество (67%)", 0.6666667f, 2),
    ULTRA_QUALITY("Ultra Quality (77%)", "Ультра-качество (77%)", 0.77f, 4),
    DLAA("DLAA (100% Tensor AA)", "DLAA (100% чёткость RTX)", 1.00f, 5);

    private final String displayNameEn;
    private final String displayNameRu;
    private final float renderScale;
    private final int ngxPerfQualityValue;

    DlssQualityMode(String displayNameEn, String displayNameRu, float renderScale, int ngxPerfQualityValue) {
        this.displayNameEn = displayNameEn;
        this.displayNameRu = displayNameRu;
        this.renderScale = renderScale;
        this.ngxPerfQualityValue = ngxPerfQualityValue;
    }

    public String getDisplayName() {
        return dev.bstdlss.i18n.I18n.get("mode." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    public String getDisplayNameRu() {
        return displayNameRu;
    }

    public float getRenderScale() {
        return renderScale;
    }

    public int getNgxPerfQualityValue() {
        return ngxPerfQualityValue;
    }

    public boolean isEnabled() {
        return this != OFF;
    }

    public int computeRenderDimension(int outputDim) {
        if (this == OFF || this == DLAA) {
            return Math.max(64, outputDim & ~1);
        }
        int scaled = Math.round(outputDim * renderScale);
        scaled &= ~1; // Align to 2-pixel boundary for Tensor Core evaluation
        return Math.max(64, scaled);
    }

    public DlssQualityMode next() {
        DlssQualityMode[] values = values();
        return values[(this.ordinal() + 1) % values.length];
    }
}
