package dev.bstdlss.i18n;

import dev.bstdlss.bridge.McRuntimeBridge;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Universal Multi-Language Internationalization (i18n) for BST DLSS.
 * Dynamically resolves Minecraft's active language at runtime via McRuntimeBridge.
 * Full localization support for English (en), Russian (ru), Ukrainian (uk), German (de),
 * Spanish (es), and French (fr) with universal English fallback.
 */
public final class I18n {
    private static final Map<String, Map<String, String>> TRANSLATIONS = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();
    private static final Map<String, String> RU = new HashMap<>();
    private static final Map<String, String> UK = new HashMap<>();
    private static final Map<String, String> DE = new HashMap<>();
    private static final Map<String, String> ES = new HashMap<>();
    private static final Map<String, String> FR = new HashMap<>();

    static {
        registerDict("en", EN);
        registerDict("en_us", EN);
        registerDict("en_gb", EN);

        registerDict("ru", RU);
        registerDict("ru_ru", RU);

        registerDict("uk", UK);
        registerDict("uk_ua", UK);

        registerDict("de", DE);
        registerDict("de_de", DE);

        registerDict("es", ES);
        registerDict("es_es", ES);
        registerDict("es_mx", ES);

        registerDict("fr", FR);
        registerDict("fr_fr", FR);

        // --- HUD Overlay ---
        tr("hud.title",
            "BST DLSS %s | %s",
            "BST DLSS %s | %s",
            "BST DLSS %s | %s",
            "BST DLSS %s | %s",
            "BST DLSS %s | %s",
            "BST DLSS %s | %s");
        tr("hud.mode",
            "MODE: %s | MODEL: %s",
            "РЕЖИМ: %s | МОДЕЛЬ: %s",
            "РЕЖИМ: %s | МОДЕЛЬ: %s",
            "MODUS: %s | MODELL: %s",
            "MODO: %s | MODELO: %s",
            "MODE: %s | MODÈLE: %s");
        tr("hud.render",
            "RENDER: %dx%d -> %dx%d | CORE: %s",
            "РЕНДЕР: %dx%d -> %dx%d | ЯДРО: %s",
            "РЕНДЕР: %dx%d -> %dx%d | ЯДРО: %s",
            "RENDER: %dx%d -> %dx%d | KERN: %s",
            "RENDER: %dx%d -> %dx%d | NÚCLEO: %s",
            "RENDU: %dx%d -> %dx%d | CŒUR: %s");
        tr("hud.core.ngx",
            "NGX TENSOR CORES",
            "NGX TENSOR CORES",
            "NGX TENSOR CORES",
            "NGX TENSOR CORES",
            "NGX TENSOR CORES",
            "NGX TENSOR CORES");
        tr("hud.core.easu",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU");
        tr("hud.perf",
            "FPS: %d (%.1f MS) | DLSS TENSORS: %.2f MS | TURBO: %s",
            "FPS: %d (%.1f МС) | ТЕНЗОРЫ DLSS: %.2f МС | TURBO: %s",
            "FPS: %d (%.1f МС) | ТЕНЗОРИ DLSS: %.2f МС | TURBO: %s",
            "FPS: %d (%.1f MS) | DLSS TENSOREN: %.2f MS | TURBO: %s",
            "FPS: %d (%.1f MS) | TENSORES DLSS: %.2f MS | TURBO: %s",
            "FPS: %d (%.1f MS) | TENSEURS DLSS: %.2f MS | TURBO: %s");
        tr("hud.arch",
            "%s | SHARPNESS: %d%% | [F8] MENU [F9] MODE [F10] HUD",
            "%s | РЕЗКОСТЬ: %d%% | [F8] МЕНЮ [F9] РЕЖИМ [F10] HUD",
            "%s | РІЗКІСТЬ: %d%% | [F8] МЕНЮ [F9] РЕЖИМ [F10] HUD",
            "%s | SCHÄRFE: %d%% | [F8] MENÜ [F9] MODUS [F10] HUD",
            "%s | NITIDEZ: %d%% | [F8] MENÚ [F9] MODO [F10] HUD",
            "%s | NETTETÉ: %d%% | [F8] MENU [F9] MODE [F10] HUD");

        // --- Status Strings ---
        tr("status.on", "ON", "ВКЛ", "УВІМК", "AN", "SÍ", "OUI");
        tr("status.off", "OFF", "ВЫКЛ", "ВИМК", "AUS", "NO", "NON");
        tr("status.shown", "SHOWN", "ПОКАЗАН", "ПОКАЗАНО", "SICHTBAR", "VISIBLE", "AFFICHÉ");
        tr("status.hidden", "HIDDEN", "СКРЫТ", "ПРИХОВАНО", "VERSTECKT", "OCULTO", "MASQUÉ");

        // --- Toasts & Tabs ---
        tr("toast.mode",
            "DLSS MODE: %s",
            "РЕЖИМ DLSS: %s",
            "РЕЖИМ DLSS: %s",
            "DLSS-MODUS: %s",
            "MODO DLSS: %s",
            "MODE DLSS: %s");
        tr("toast.hud",
            "HUD TELEMETRY: %s",
            "HUD ТЕЛЕМЕТРИЯ: %s",
            "HUD ТЕЛЕМЕТРІЯ: %s",
            "HUD-TELEMETRIE: %s",
            "TELEMETRÍA HUD: %s",
            "TÉLÉMÉTRIE HUD: %s");
        tr("toast.welcome",
            "[F10] BST DLSS | [F8] SETTINGS",
            "[F10] BST DLSS | [F8] НАСТРОЙКИ",
            "[F10] BST DLSS | [F8] НАЛАШТУВАННЯ",
            "[F10] BST DLSS | [F8] EINSTELLUNGEN",
            "[F10] BST DLSS | [F8] AJUSTES",
            "[F10] BST DLSS | [F8] PARAMÈTRES");
        tr("tab.icon",
            "[DLSS F8]",
            "[DLSS F8]",
            "[DLSS F8]",
            "[DLSS F8]",
            "[DLSS F8]",
            "[DLSS F8]");

        // --- Control Panel Menu (F8) ---
        tr("menu.title",
            "BST DLSS %s CONTROL PANEL (RTX 20XX - 50XX)",
            "ПАНЕЛЬ УПРАВЛЕНИЯ BST DLSS %s (RTX 20XX - 50XX)",
            "ПАНЕЛЬ КЕРУВАННЯ BST DLSS %s (RTX 20XX - 50XX)",
            "BST DLSS %s STEUERUNG (RTX 20XX - 50XX)",
            "PANEL DE CONTROL BST DLSS %s (RTX 20XX - 50XX)",
            "PANNEAU DE CONTRÔLE BST DLSS %s (RTX 20XX - 50XX)");
        tr("menu.gpu",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s",
            "GPU: %s [RTX %sXX] | DLSS DLL: %s");
        tr("menu.core_status",
            "CORE: %s | RENDER: %dX%d -> %dX%d",
            "ЯДРО: %s | РЕНДЕР: %dX%d -> %dX%d",
            "ЯДРО: %s | РЕНДЕР: %dX%d -> %dX%d",
            "KERN: %s | RENDER: %dX%d -> %dX%d",
            "NÚCLEO: %s | RENDER: %dX%d -> %dX%d",
            "CŒUR: %s | RENDU: %dX%d -> %dX%d");
        tr("menu.core.hw_desc",
            "HARDWARE NVIDIA NGX TENSOR CORES (D3D11 INTEROP)",
            "АППАРАТНЫЕ ТЕНЗОРНЫЕ ЯДРА NVIDIA NGX (D3D11 INTEROP)",
            "АПАРАТНІ ТЕНЗОРНІ ЯДРА NVIDIA NGX (D3D11 INTEROP)",
            "HARDWARE NVIDIA NGX TENSOR-KERNE (D3D11 INTEROP)",
            "NÚCLEOS TENSOR NVIDIA NGX POR HARDWARE (D3D11)",
            "CŒURS TENSEURS MATÉRIELS NVIDIA NGX (D3D11)");
        tr("menu.core.fallback_desc",
            "OPENGL EASU SHADER UPSCALER",
            "ШЕЙДЕРНЫЙ АПСКЕЙЛЕР OPENGL EASU",
            "ШЕЙДЕРНИЙ АПСКЕЙЛЕР OPENGL EASU",
            "OPENGL EASU SHADER-UPSCALER",
            "ESCALADOR DE SHADER OPENGL EASU",
            "MISE À L'ÉCHELLE SHADER OPENGL EASU");

        // Menu Buttons
        tr("menu.btn.mode",
            "1. MODE: %s",
            "1. РЕЖИМ: %s",
            "1. РЕЖИМ: %s",
            "1. MODUS: %s",
            "1. MODO: %s",
            "1. MODE: %s");
        tr("menu.btn.preset",
            "2. PRESET: %s",
            "2. ПРЕСЕТ: %s",
            "2. ПРЕСЕТ: %s",
            "2. PRESET: %s",
            "2. PERFIL: %s",
            "2. PRÉRÉGLAGE: %s");
        tr("menu.btn.sharpness",
            "3. RCAS SHARPNESS: %d%%",
            "3. РЕЗКОСТЬ RCAS: %d%%",
            "3. РІЗКІСТЬ RCAS: %d%%",
            "3. RCAS-SCHÄRFE: %d%%",
            "3. NITIDEZ RCAS: %d%%",
            "3. NETTETÉ RCAS: %d%%");
        tr("menu.btn.mipmapping",
            "4. VANILLA MIPMAPPING: %s",
            "4. ВАНИЛЬНЫЙ МИПМАППИНГ: %s",
            "4. ВАНІЛЬНИЙ МІПМАПІНГ: %s",
            "4. VANILLA-MIPMAPPING: %s",
            "4. MIPMAPPING VANILLA: %s",
            "4. MIPMAPPING VANILLA: %s");
        tr("menu.mipmapping.crisp",
            "CRISP (LOD BIAS)",
            "ЧЁТКИЙ (LOD BIAS)",
            "ЧІТКИЙ (LOD BIAS)",
            "SCHARF (LOD BIAS)",
            "NÍTIDO (LOD BIAS)",
            "NET (LOD BIAS)");
        tr("menu.mipmapping.standard",
            "STANDARD (SOFT)",
            "СТАНДАРТ (МЯГКО)",
            "СТАНДАРТ (М'ЯКО)",
            "STANDARD (WEICH)",
            "ESTÁNDAR (SUAVE)",
            "STANDARD (DOUX)");

        tr("menu.btn.engine",
            "5. ENGINE: %s",
            "5. ДВИЖОК: %s",
            "5. РУШІЙ: %s",
            "5. ENGINE: %s",
            "5. MOTOR: %s",
            "5. MOTEUR: %s");
        tr("menu.engine.tensor",
            "NVIDIA NGX TENSOR",
            "NVIDIA NGX TENSOR",
            "NVIDIA NGX TENSOR",
            "NVIDIA NGX TENSOR",
            "NVIDIA NGX TENSOR",
            "NVIDIA NGX TENSOR");
        tr("menu.engine.easu",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU",
            "OPENGL EASU");

        tr("menu.btn.fps_uncap",
            "6. FPS UNCAP: %s",
            "6. СНЯТИЕ ЛИМИТА ФПС: %s",
            "6. ЗНЯТТЯ ЛІМІТУ ФПС: %s",
            "6. FPS-BEGRENZUNG: %s",
            "6. DESLIMITAR FPS: %s",
            "6. DÉBLOQUER FPS: %s");
        tr("menu.status.uncapped",
            "ON (UNCAPPED)",
            "ВКЛ (БЕЗ ЛИМИТА)",
            "УВІМК (БЕЗ ЛІМІТУ)",
            "AN (UNBEGRENZT)",
            "SÍ (ILIMITADO)",
            "OUI (ILLIMITÉ)");

        tr("menu.btn.entity_boost",
            "7. ENTITY CPU BOOST: %s",
            "7. БУСТ СУЩНОСТЕЙ (CPU): %s",
            "7. БУСТ СУТНОСТЕЙ (CPU): %s",
            "7. ENTITY-CPU-BOOST: %s",
            "7. BOOST ENTIDADES (CPU): %s",
            "7. BOOST ENTITÉS (CPU): %s");
        tr("menu.status.max_fps",
            "ON (MAX FPS)",
            "ВКЛ (МАКС ФПС)",
            "УВІМК (МАКС ФПС)",
            "AN (MAX FPS)",
            "SÍ (MÁX FPS)",
            "OUI (MAX FPS)");

        tr("menu.btn.chunk_boost",
            "8. CHUNKS & TICKS BOOST: %s",
            "8. БУСТ ЧАНКОВ И ТИКОВ: %s",
            "8. БУСТ ЧАНКІВ ТА ТИКІВ: %s",
            "8. CHUNKS & TICKS BOOST: %s",
            "8. BOOST CHUNKS Y TICKS: %s",
            "8. BOOST TRONÇONS & TICKS: %s");
        tr("menu.status.turbo",
            "ON (TURBO)",
            "ВКЛ (ТУРБО)",
            "УВІМК (ТУРБО)",
            "AN (TURBO)",
            "SÍ (TURBO)",
            "OUI (TURBO)");

        tr("menu.btn.frame_gen",
            "9. FRAME GENERATION (FG): %s",
            "9. ГЕНЕРАЦИЯ КАДРОВ (FG): %s",
            "9. ГЕНЕРАЦІЯ КАДРІВ (FG): %s",
            "9. FRAME-GENERATION (FG): %s",
            "9. GENERACIÓN CUADROS (FG): %s",
            "9. GÉNÉRATION D'IMAGES: %s");

        tr("menu.btn.reflex",
            "10. NVIDIA REFLEX: %s",
            "10. NVIDIA REFLEX: %s",
            "10. NVIDIA REFLEX: %s",
            "10. NVIDIA REFLEX: %s",
            "10. NVIDIA REFLEX: %s",
            "10. NVIDIA REFLEX: %s");
        tr("menu.status.low_latency",
            "ON (LOW LATENCY)",
            "ВКЛ (БЫСТРЫЙ ОТКЛИК)",
            "УВІМК (ШВИДКИЙ ВІДГУК)",
            "AN (GERINGE LATENZ)",
            "SÍ (BAJA LATENCIA)",
            "OUI (FAIBLE LATENCE)");

        tr("menu.btn.hud_overlay",
            "HUD OVERLAY [F10]: %s",
            "HUD ОВЕРЛЕЙ [F10]: %s",
            "HUD ОВЕРЛЕЙ [F10]: %s",
            "HUD-OVERLAY [F10]: %s",
            "OVERLAY HUD [F10]: %s",
            "OVERLAY HUD [F10]: %s");
        tr("menu.btn.save_close",
            "SAVE & CLOSE [F8]",
            "СОХРАНИТЬ И ЗАКРЫТЬ [F8]",
            "ЗБЕРЕГТИ ТА ЗАКРИТИ [F8]",
            "SPEICHERN & BEENDEN [F8]",
            "GUARDAR Y CERRAR [F8]",
            "ENREGISTRER & FERMER [F8]");
        tr("menu.footer",
            "KEYS: [F8] SETTINGS MENU   [F9] CYCLE DLSS MODE   [F10] HUD TELEMETRY   [ESC] CLOSE",
            "КЛАВИШИ: [F8] ПАНЕЛЬ НАСТРОЕК   [F9] ПЕРЕКЛЮЧИТЬ РЕЖИМ DLSS   [F10] HUD ТЕЛЕМЕТРИЯ   [ESC] ЗАКРЫТЬ",
            "КЛАВІШІ: [F8] ПАНЕЛЬ НАЛАШТУВАНЬ   [F9] ЗМІНИТИ РЕЖИМ DLSS   [F10] HUD ТЕЛЕМЕТРІЯ   [ESC] ЗАКРИТИ",
            "TASTEN: [F8] EINSTELLUNGEN   [F9] DLSS-MODUS   [F10] HUD-TELEMETRIE   [ESC] SCHLIESSEN",
            "TECLAS: [F8] MENÚ AJUSTES   [F9] CAMBIAR MODO DLSS   [F10] HUD TELEMETRÍA   [ESC] CERRAR",
            "TOUCHES: [F8] PARAMÈTRES   [F9] CHANGER MODE DLSS   [F10] TÉLÉMÉTRIE HUD   [ESC] FERMER");

        // --- Quality Modes ---
        tr("mode.off",
            "Off (Native 100%)",
            "Выкл (Родное 100%)",
            "Вимк (Рідне 100%)",
            "Aus (Nativ 100%)",
            "Desactivado (Nativo 100%)",
            "Désactivé (Natif 100%)");
        tr("mode.hyper_performance",
            "Hyper Performance (25%)",
            "Гипер-ФПС (25%)",
            "Гіпер-ФПС (25%)",
            "Hyper-Leistung (25%)",
            "Rendimiento Max (25%)",
            "Hyper Performance (25%)");
        tr("mode.ultra_performance",
            "Ultra Performance (33%)",
            "Ультра-ФПС (33%)",
            "Ультра-ФПС (33%)",
            "Ultra-Leistung (33%)",
            "Ultra Rendimiento (33%)",
            "Ultra Performance (33%)");
        tr("mode.performance",
            "Performance (50%)",
            "Производительность (50%)",
            "Продуктивність (50%)",
            "Leistung (50%)",
            "Rendimiento (50%)",
            "Performance (50%)");
        tr("mode.balanced",
            "Balanced (58%)",
            "Баланс (58%)",
            "Баланс (58%)",
            "Ausgewogen (58%)",
            "Equilibrado (58%)",
            "Équilibré (58%)");
        tr("mode.quality",
            "Quality (67%)",
            "Качество (67%)",
            "Якість (67%)",
            "Qualität (67%)",
            "Calidad (67%)",
            "Qualité (67%)");
        tr("mode.ultra_quality",
            "Ultra Quality (77%)",
            "Ультра-качество (77%)",
            "Ультра-якість (77%)",
            "Ultra-Qualität (77%)",
            "Ultra Calidad (77%)",
            "Ultra Qualité (77%)");
        tr("mode.dlaa",
            "DLAA (100% Tensor AA)",
            "DLAA (100% чёткость RTX)",
            "DLAA (100% чіткість RTX)",
            "DLAA (100% Tensor AA)",
            "DLAA (100% Tensor AA)",
            "DLAA (100% Tensor AA)");

        // --- Presets ---
        tr("preset.default",
            "Default (Driver Auto)",
            "Авто (Драйвер)",
            "Авто (Драйвер)",
            "Standard (Treiber)",
            "Auto (Controlador)",
            "Auto (Pilote)");
        tr("preset.preset_c",
            "Preset C (Fast Motion)",
            "Пресет C (Динамика)",
            "Пресет C (Динаміка)",
            "Preset C (Dynamik)",
            "Perfil C (Dinámico)",
            "Profil C (Dynamique)");
        tr("preset.preset_d",
            "Preset D (Temporal Stability)",
            "Пресет D (Стабильность)",
            "Пресет D (Стабільність)",
            "Preset D (Stabilität)",
            "Perfil D (Estabilidad)",
            "Profil D (Stabilité)");
        tr("preset.preset_e",
            "Preset E (DLSS 3.7+ CNN)",
            "Пресет E (DLSS 3.7+ CNN)",
            "Пресет E (DLSS 3.7+ CNN)",
            "Preset E (DLSS 3.7+ CNN)",
            "Perfil E (DLSS 3.7+ CNN)",
            "Profil E (DLSS 3.7+ CNN)");
        tr("preset.preset_j",
            "Preset J (DLSS 4 Transformer)",
            "Пресет J (DLSS 4 Transformer)",
            "Пресет J (DLSS 4 Transformer)",
            "Preset J (DLSS 4 Transformer)",
            "Perfil J (DLSS 4 Transformer)",
            "Profil J (DLSS 4 Transformer)");
        tr("preset.preset_k",
            "Preset K (DLSS 4 Latest)",
            "Пресет K (DLSS 4 Latest)",
            "Пресет K (DLSS 4 Latest)",
            "Preset K (DLSS 4 Neueste)",
            "Perfil K (DLSS 4 Último)",
            "Profil K (DLSS 4 Dernier)");
    }

    private static void registerDict(String code, Map<String, String> dict) {
        TRANSLATIONS.put(code.toLowerCase(Locale.ROOT), dict);
    }

    private static void tr(String key, String en, String ru, String uk, String de, String es, String fr) {
        EN.put(key, en);
        RU.put(key, ru);
        UK.put(key, uk);
        DE.put(key, de);
        ES.put(key, es);
        FR.put(key, fr);
    }

    private I18n() {
    }

    public static String getCurrentLanguage() {
        return McRuntimeBridge.getCurrentLanguage();
    }

    public static boolean isRussian() {
        return McRuntimeBridge.isRussian();
    }

    public static String get(String key) {
        String lang = McRuntimeBridge.getCurrentLanguage();
        if (lang != null && !lang.isEmpty()) {
            lang = lang.toLowerCase(Locale.ROOT);
            Map<String, String> dict = TRANSLATIONS.get(lang);
            if (dict != null) {
                String val = dict.get(key);
                if (val != null) return val;
            }
            int underscore = lang.indexOf('_');
            if (underscore > 0) {
                String prefix = lang.substring(0, underscore);
                dict = TRANSLATIONS.get(prefix);
                if (dict != null) {
                    String val = dict.get(key);
                    if (val != null) return val;
                }
            }
        }
        String v = EN.get(key);
        return v != null ? v : key;
    }

    public static String getFor(String key, String langCode) {
        if (langCode != null && !langCode.isEmpty()) {
            String l = langCode.toLowerCase(Locale.ROOT);
            Map<String, String> dict = TRANSLATIONS.get(l);
            if (dict != null) {
                String val = dict.get(key);
                if (val != null) return val;
            }
            int underscore = l.indexOf('_');
            if (underscore > 0) {
                dict = TRANSLATIONS.get(l.substring(0, underscore));
                if (dict != null) {
                    String val = dict.get(key);
                    if (val != null) return val;
                }
            }
        }
        String v = EN.get(key);
        return v != null ? v : key;
    }

    public static String format(String key, Object... args) {
        String template = get(key);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Throwable t) {
            return template;
        }
    }
}
