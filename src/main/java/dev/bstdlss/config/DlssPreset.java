package dev.bstdlss.config;

/**
 * NVIDIA NGX DLSS Neural Network Presets (CNN & DLSS 4 Transformer profiles).
 * Supported across RTX 20xx, 30xx, 40xx, and 50xx series.
 */
public enum DlssPreset {
    DEFAULT("Default (Driver Auto)", "Авто (Драйвер)", 0),
    PRESET_C("Preset C (Fast Motion)", "Пресет C (Динамика)", 3),
    PRESET_D("Preset D (Temporal Stability)", "Пресет D (Стабильность)", 4),
    PRESET_E("Preset E (DLSS 3.7+ CNN)", "Пресет E (DLSS 3.7+ CNN)", 5),
    PRESET_J("Preset J (DLSS 4 Transformer)", "Пресет J (DLSS 4 Transformer)", 10),
    PRESET_K("Preset K (DLSS 4 Latest)", "Пресет K (DLSS 4 Latest)", 11);

    private final String displayNameEn;
    private final String displayNameRu;
    private final int ngxPresetValue;

    DlssPreset(String displayNameEn, String displayNameRu, int ngxPresetValue) {
        this.displayNameEn = displayNameEn;
        this.displayNameRu = displayNameRu;
        this.ngxPresetValue = ngxPresetValue;
    }

    public String getDisplayName() {
        return dev.bstdlss.i18n.I18n.get("preset." + name().toLowerCase(java.util.Locale.ROOT));
    }

    public String getDisplayNameEn() {
        return displayNameEn;
    }

    public String getDisplayNameRu() {
        return displayNameRu;
    }

    public int getNgxPresetValue() {
        return ngxPresetValue;
    }

    public DlssPreset next() {
        DlssPreset[] vals = values();
        return vals[(this.ordinal() + 1) % vals.length];
    }
}
