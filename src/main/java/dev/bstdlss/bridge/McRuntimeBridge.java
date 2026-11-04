package dev.bstdlss.bridge;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Universal Multi-Version Runtime Bridge for Minecraft Forge 1.18.2 through 26.3.
 * Supports both SRG obfuscation (1.18.2 - 1.20.4) and Mojang Official Mappings (1.20.5 - 26.3).
 */
public final class McRuntimeBridge {
    private static boolean resolved = false;

    private static Class<?> minecraftClass;
    private static Method getInstanceMethod;
    private static Field gameDirField;
    private static Method getMainRenderTargetMethod;
    private static Field mainRenderTargetField;
    private static Method getWindowMethod;
    private static Field windowField;
    private static Field levelField;
    private static Field levelRendererField;
    private static Field screenField;
    private static Field gameRendererField;
    private static Field optionsField;

    // Auxiliary RenderTarget fields inside LevelRenderer (entityTarget, translucentTarget, etc.)
    private static final List<Field> levelRendererTargetFields = new ArrayList<>();

    // TextureManager fields for Negative Mipmap LOD Bias & Crisp Vanilla Textures
    private static Field textureManagerField;
    private static Field tmByPathField;
    private static Field abstractTextureIdField;

    // Window fields (framebufferWidth / framebufferHeight read by Window.getWidth() / getHeight())
    private static Field winFbWidthField;
    private static Field winFbHeightField;
    private static Field winFramerateLimitField;

    // GlStateManager._viewport(x, y, w, h)
    private static Method glStateViewportMethod;

    // RenderTarget fields/methods
    private static Field rtWidthField;
    private static Field rtHeightField;
    private static Field rtViewWidthField;
    private static Field rtViewHeightField;
    private static Field rtFrameBufferIdField;
    private static Method rtGetColorTexMethod;
    private static Field rtColorTexField;
    private static Method rtGetDepthTexMethod;
    private static Field rtDepthTexField;

    // Camera fields/methods for 6-DOF motion vectors
    private static Method grGetMainCameraMethod;
    private static Method camGetYRotMethod;
    private static Method camGetXRotMethod;
    private static Field camYRotField;
    private static Field camXRotField;
    private static Method camGetPositionMethod;
    private static Field vec3XField;
    private static Field vec3YField;
    private static Field vec3ZField;

    // Options fields for Turbo FPS Boost (verified against 1.18.2 - 1.20.4 SRG & Mojang mappings)
    private static Field optFovField;
    private static Field optRenderDistanceField;
    private static Field optSimulationDistanceField;
    private static Field optEntityDistanceScalingField;
    private static Field optFramerateLimitField;
    private static Field optEnableVsyncField;
    private static Field optEntityShadowsField;
    private static Field optBiomeBlendRadiusField;
    private static Field optLanguageField;
    private static Method optionInstanceGetMethod;
    private static Method optionInstanceSetMethod;
    private static Field optionInstanceValueField;

    // Language fields for dynamic i18n
    private static Field mcLanguageManagerField;
    private static Method mcGetLanguageManagerMethod;

    private static boolean swapIntervalZeroApplied = false;

    private McRuntimeBridge() {
    }

    public static synchronized void resolveMinecraftInternals() {
        if (resolved) return;
        resolved = true;
        try {
            minecraftClass = Class.forName("net.minecraft.client.Minecraft");
            getInstanceMethod = findMethod(minecraftClass, new String[]{"getInstance", "m_91087_"});
            gameDirField = findField(minecraftClass, new String[]{"gameDirectory", "f_91069_"});
            getMainRenderTargetMethod = findMethod(minecraftClass, new String[]{"getMainRenderTarget", "m_91385_"});
            mainRenderTargetField = findField(minecraftClass, new String[]{"mainRenderTarget", "f_91042_"});
            getWindowMethod = findMethod(minecraftClass, new String[]{"getWindow", "m_91268_"});
            windowField = findField(minecraftClass, new String[]{"window", "f_90990_", "f_91043_"});
            levelField = findField(minecraftClass, new String[]{"level", "f_91073_"});
            levelRendererField = findField(minecraftClass, new String[]{"levelRenderer", "f_91060_"});
            screenField = findField(minecraftClass, new String[]{"screen", "f_91080_"});
            gameRendererField = findField(minecraftClass, new String[]{"gameRenderer", "f_91063_"});
            optionsField = findField(minecraftClass, new String[]{"options", "f_91066_"});
            mcLanguageManagerField = findField(minecraftClass, new String[]{"languageManager", "f_91039_", "f_91040_"});
            mcGetLanguageManagerMethod = findMethod(minecraftClass, new String[]{"getLanguageManager", "m_91102_", "m_91350_"});

            try {
                Class<?> tmClass = Class.forName("net.minecraft.client.renderer.texture.TextureManager");
                tmByPathField = findField(tmClass, new String[]{"byPath", "f_118468_"});
                Class<?> atClass = Class.forName("net.minecraft.client.renderer.texture.AbstractTexture");
                abstractTextureIdField = findField(atClass, new String[]{"id", "f_117950_"});
            } catch (Throwable ignored) {
            }

            try {
                Class<?> windowClass = Class.forName("com.mojang.blaze3d.platform.Window");
                winFbWidthField = findField(windowClass, new String[]{"framebufferWidth", "f_85361_", "f_85381_"});
                winFbHeightField = findField(windowClass, new String[]{"framebufferHeight", "f_85362_", "f_85382_"});
                winFramerateLimitField = findField(windowClass, new String[]{"framerateLimit", "f_85368_"});
            } catch (Throwable ignored) {
            }

            try {
                Class<?> gsmClass = Class.forName("com.mojang.blaze3d.platform.GlStateManager");
                for (Method m : gsmClass.getDeclaredMethods()) {
                    if ((m.getName().equals("_viewport") || m.getName().equals("m_84430_")) && m.getParameterCount() == 4) {
                        m.setAccessible(true);
                        glStateViewportMethod = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }

            Class<?> renderTargetClass = Class.forName("com.mojang.blaze3d.pipeline.RenderTarget");
            rtWidthField = findField(renderTargetClass, new String[]{"width", "f_83915_"});
            rtHeightField = findField(renderTargetClass, new String[]{"height", "f_83916_"});
            rtViewWidthField = findField(renderTargetClass, new String[]{"viewWidth", "f_83917_", "f_83913_"});
            rtViewHeightField = findField(renderTargetClass, new String[]{"viewHeight", "f_83918_", "f_83914_"});
            rtFrameBufferIdField = findField(renderTargetClass, new String[]{"frameBufferId", "f_83920_"});
            rtGetColorTexMethod = findMethod(renderTargetClass, new String[]{"getColorTextureId", "m_83975_"});
            rtColorTexField = findField(renderTargetClass, new String[]{"colorTextureId", "f_83923_", "f_83906_"});
            rtGetDepthTexMethod = findMethod(renderTargetClass, new String[]{"getDepthTextureId", "m_83980_"});
            rtDepthTexField = findField(renderTargetClass, new String[]{"depthBufferId", "f_83924_", "f_83907_"});

            // Discover all auxiliary RenderTarget fields in LevelRenderer (entityTarget, translucentTarget, etc.)
            try {
                Class<?> levelRendererClass = Class.forName("net.minecraft.client.renderer.LevelRenderer");
                for (Field f : levelRendererClass.getDeclaredFields()) {
                    if (renderTargetClass.isAssignableFrom(f.getType())) {
                        f.setAccessible(true);
                        levelRendererTargetFields.add(f);
                    }
                }
            } catch (Throwable ignored) {
            }

            Class<?> gameRendererClass = Class.forName("net.minecraft.client.renderer.GameRenderer");
            grGetMainCameraMethod = findMethod(gameRendererClass, new String[]{"getMainCamera", "m_109153_"});

            Class<?> cameraClass = Class.forName("net.minecraft.client.Camera");
            camGetYRotMethod = findMethod(cameraClass, new String[]{"getYRot", "m_90590_", "m_170909_"});
            camGetXRotMethod = findMethod(cameraClass, new String[]{"getXRot", "m_90589_", "m_170906_"});
            camYRotField = findField(cameraClass, new String[]{"yRot", "f_90558_"});
            camXRotField = findField(cameraClass, new String[]{"xRot", "f_90557_"});
            camGetPositionMethod = findMethod(cameraClass, new String[]{"getPosition", "m_90583_"});

            Class<?> vec3Class = Class.forName("net.minecraft.world.phys.Vec3");
            vec3XField = findField(vec3Class, new String[]{"x", "f_82479_"});
            vec3YField = findField(vec3Class, new String[]{"y", "f_82480_"});
            vec3ZField = findField(vec3Class, new String[]{"z", "f_82481_"});

            try {
                Class<?> optionsClass = Class.forName("net.minecraft.client.Options");
                optFovField = findField(optionsClass, new String[]{"fov", "f_92068_", "field_74334_t"});
                optRenderDistanceField = findField(optionsClass, new String[]{"renderDistance", "f_92106_"});
                optSimulationDistanceField = findField(optionsClass, new String[]{"simulationDistance", "f_193768_"});
                optEntityDistanceScalingField = findField(optionsClass, new String[]{"entityDistanceScaling", "f_92112_"});
                optFramerateLimitField = findField(optionsClass, new String[]{"framerateLimit", "f_92113_"});
                optEnableVsyncField = findField(optionsClass, new String[]{"enableVsync", "f_92041_"});
                optEntityShadowsField = findField(optionsClass, new String[]{"entityShadows", "f_92042_"});
                optBiomeBlendRadiusField = findField(optionsClass, new String[]{"biomeBlendRadius", "f_92032_"});
                optLanguageField = findField(optionsClass, new String[]{"language", "f_92075_", "f_92066_", "field_74363_ab"});

                Class<?> optInstClass = Class.forName("net.minecraft.client.OptionInstance");
                optionInstanceGetMethod = findMethod(optInstClass, new String[]{"get", "m_231551_"});
                optionInstanceValueField = findField(optInstClass, new String[]{"value", "f_231481_"});
                for (Method m : optInstClass.getDeclaredMethods()) {
                    if ((m.getName().equals("set") || m.getName().equals("m_231514_")) && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        optionInstanceSetMethod = m;
                        break;
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    public static Object getMinecraft() {
        resolveMinecraftInternals();
        if (getInstanceMethod == null) return null;
        try {
            return getInstanceMethod.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object getWindow() {
        Object mc = getMinecraft();
        if (mc == null) return null;
        try {
            if (getWindowMethod != null) return getWindowMethod.invoke(mc);
            if (windowField != null) return windowField.get(mc);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static File getGameDirectory() {
        Object mc = getMinecraft();
        if (mc != null && gameDirField != null) {
            try {
                Object dir = gameDirField.get(mc);
                if (dir instanceof File) return (File) dir;
                if (dir instanceof java.nio.file.Path) return ((java.nio.file.Path) dir).toFile();
            } catch (Throwable ignored) {
            }
        }
        File mcDir = new File("D:/Games/Minecraft");
        if (mcDir.exists() && mcDir.isDirectory()) return mcDir;
        return new File(".");
    }

    public static boolean isInWorld() {
        Object mc = getMinecraft();
        if (mc == null || levelField == null) return false;
        try {
            return levelField.get(mc) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isScreenOpen() {
        Object mc = getMinecraft();
        if (mc == null || screenField == null) return false;
        try {
            return screenField.get(mc) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Dynamically determines the active Minecraft language (e.g. "ru_ru", "en_us").
     * Checks Minecraft Options -> LanguageManager -> options.txt -> JVM Locale -> fallback "en_us".
     */
    public static String getCurrentLanguage() {
        resolveMinecraftInternals();
        Object mc = getMinecraft();
        if (mc != null) {
            // 1. Try Options.language
            if (optionsField != null) {
                try {
                    Object opts = optionsField.get(mc);
                    if (opts != null) {
                        if (optLanguageField != null) {
                            Object langVal = optLanguageField.get(opts);
                            if (langVal instanceof String) {
                                String s = ((String) langVal).trim();
                                if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
                            } else if (langVal != null) {
                                Object val = null;
                                if (optionInstanceGetMethod != null) {
                                    val = optionInstanceGetMethod.invoke(langVal);
                                } else if (optionInstanceValueField != null) {
                                    val = optionInstanceValueField.get(langVal);
                                }
                                if (val instanceof String) {
                                    String s = ((String) val).trim();
                                    if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
                                }
                            }
                        }
                        // Dynamic scan across options fields for language code (e.g. "ru_ru", "en_us")
                        for (Field f : opts.getClass().getDeclaredFields()) {
                            if (f.getType() == String.class) {
                                f.setAccessible(true);
                                Object val = f.get(opts);
                                if (val instanceof String) {
                                    String s = ((String) val).trim().toLowerCase(Locale.ROOT);
                                    if (s.matches("^[a-z]{2}(?:_[a-z]{2,3})?$")) {
                                        return s;
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }

            // 2. Try LanguageManager.getSelected()
            try {
                Object lm = null;
                if (mcGetLanguageManagerMethod != null) {
                    lm = mcGetLanguageManagerMethod.invoke(mc);
                } else if (mcLanguageManagerField != null) {
                    lm = mcLanguageManagerField.get(mc);
                }
                if (lm != null) {
                    for (Method m : lm.getClass().getMethods()) {
                        if (m.getParameterCount() == 0 && (
                            m.getName().equals("m_264236_") ||
                            m.getName().equals("getSelected") ||
                            m.getName().equals("m_118919_") ||
                            m.getName().equals("getCode") ||
                            m.getName().equals("m_118933_")
                        )) {
                            m.setAccessible(true);
                            Object res = m.invoke(lm);
                            if (res instanceof String) {
                                String s = ((String) res).trim();
                                if (!s.isEmpty()) return s.toLowerCase(Locale.ROOT);
                            }
                        }
                    }
                    // Field fallback on LanguageManager (f_118967_ in 1.18 - 1.20.4)
                    for (Field f : lm.getClass().getDeclaredFields()) {
                        if (f.getType() == String.class) {
                            f.setAccessible(true);
                            Object res = f.get(lm);
                            if (res instanceof String) {
                                String s = ((String) res).trim().toLowerCase(Locale.ROOT);
                                if (s.matches("^[a-z]{2}(?:_[a-z]{2,3})?$")) {
                                    return s;
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        }

        // 3. Try reading options.txt in game directory
        try {
            File gameDir = getGameDirectory();
            if (gameDir != null) {
                File optFile = new File(gameDir, "options.txt");
                if (optFile.exists() && optFile.isFile()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(optFile, StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            line = line.trim();
                            if (line.startsWith("lang:")) {
                                String lang = line.substring(5).trim();
                                if (!lang.isEmpty()) {
                                    return lang.toLowerCase(Locale.ROOT);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        // 4. Fallback to JVM Locale
        try {
            String sys = Locale.getDefault().getLanguage();
            if (sys != null && sys.equalsIgnoreCase("ru")) {
                return "ru_ru";
            }
        } catch (Throwable ignored) {
        }

        return "en_us";
    }

    public static boolean isRussian() {
        String lang = getCurrentLanguage();
        return lang != null && lang.startsWith("ru");
    }

    public static Object getMainRenderTarget() {
        Object mc = getMinecraft();
        if (mc == null) return null;
        try {
            if (getMainRenderTargetMethod != null) {
                return getMainRenderTargetMethod.invoke(mc);
            }
            if (mainRenderTargetField != null) {
                return mainRenderTargetField.get(mc);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static int getRenderTargetWidth(Object rt) {
        if (rt == null || rtWidthField == null) return 0;
        try {
            return rtWidthField.getInt(rt);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int getRenderTargetHeight(Object rt) {
        if (rt == null || rtHeightField == null) return 0;
        try {
            return rtHeightField.getInt(rt);
        } catch (Throwable t) {
            return 0;
        }
    }

    /**
     * Synchronizes:
     * 1. Main RenderTarget.viewWidth / viewHeight (f_83917_ / f_83918_)
     * 2. All LevelRenderer auxiliary RenderTargets (entityTarget, translucentTarget, etc.)
     *    so entityTarget.clear() NEVER resets the viewport to 100% before rendering entities!
     * 3. Window.framebufferWidth / framebufferHeight (f_85361_ / f_85362_)
     * 4. GlStateManager._viewport(0, 0, viewW, viewH)
     */
    public static void setWorldRenderResolution(Object rt, int viewW, int viewH) {
        try {
            if (rt != null) {
                if (rtViewWidthField != null) rtViewWidthField.setInt(rt, viewW);
                if (rtViewHeightField != null) rtViewHeightField.setInt(rt, viewH);
            }
            Object mc = getMinecraft();
            if (mc != null && levelRendererField != null && !levelRendererTargetFields.isEmpty()) {
                Object lr = levelRendererField.get(mc);
                if (lr != null) {
                    for (int i = 0, n = levelRendererTargetFields.size(); i < n; i++) {
                        Object auxRt = levelRendererTargetFields.get(i).get(lr);
                        if (auxRt != null) {
                            if (rtViewWidthField != null) rtViewWidthField.setInt(auxRt, viewW);
                            if (rtViewHeightField != null) rtViewHeightField.setInt(auxRt, viewH);
                        }
                    }
                }
            }
            // CRITICAL: Window.framebufferWidth/Height must remain the true physical display size.
            // Modifying winFbWidthField causes oscillating window dimensions, breaking mouse coordinates and GUI matrices.
            setGlViewport(0, 0, viewW, viewH);
        } catch (Throwable ignored) {
        }
    }

    public static void setGlViewport(int x, int y, int w, int h) {
        try {
            if (glStateViewportMethod != null) {
                glStateViewportMethod.invoke(null, x, y, w, h);
                return;
            }
        } catch (Throwable ignored) {
        }
        GL11.glViewport(x, y, w, h);
    }

    public static int getRenderTargetFboId(Object rt) {
        if (rt == null || rtFrameBufferIdField == null) return 0;
        try {
            return rtFrameBufferIdField.getInt(rt);
        } catch (Throwable t) {
            return 0;
        }
    }

    public static int getRenderTargetColorTexId(Object rt) {
        if (rt == null) return 0;
        try {
            if (rtGetColorTexMethod != null) {
                return (int) rtGetColorTexMethod.invoke(rt);
            }
            if (rtColorTexField != null) {
                return rtColorTexField.getInt(rt);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    public static int getRenderTargetDepthTexId(Object rt) {
        if (rt == null) return 0;
        try {
            if (rtGetDepthTexMethod != null) {
                return (int) rtGetDepthTexMethod.invoke(rt);
            }
            if (rtDepthTexField != null) {
                return rtDepthTexField.getInt(rt);
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * Applies negative LOD bias to mipmapped block/item texture atlases.
     * Keeps distant blocks tack-sharp without ruining UI, font, or particle rendering.
     * Never clamps GL_TEXTURE_MAX_LOD to 1, avoiding incomplete mipmap texture bugs.
     */
    public static void applyTextureLodBias(float bias, int maxLod) {
    }

    public static void applyTextureLodBias(float bias) {
    }

    /**
     * Applies non-conflicting Turbo FPS Boost settings to Minecraft's Options when enabled.
     * Checks current values first so OptionInstance.set is never spammed and never logs errors.
     */
    public static void applyTurboFpsBoosts(boolean uncapFps, boolean entityBoost, boolean chunkSimBoost) {
        try {
            if (uncapFps && !swapIntervalZeroApplied) {
                GLFW.glfwSwapInterval(0);
                swapIntervalZeroApplied = true;
            } else if (!uncapFps && swapIntervalZeroApplied) {
                swapIntervalZeroApplied = false;
            }

            Object mc = getMinecraft();
            if (mc == null || optionsField == null) return;
            Object opts = optionsField.get(mc);
            if (opts == null) return;

            if (uncapFps) {
                setOptionIfDifferent(opts, optFramerateLimitField, 260);
                setOptionIfDifferent(opts, optEnableVsyncField, Boolean.FALSE);
                Object win = getWindow();
                if (win != null && winFramerateLimitField != null) {
                    if (winFramerateLimitField.getInt(win) != 260) {
                        winFramerateLimitField.setInt(win, 260);
                    }
                }
            }
            if (entityBoost) {
                setOptionIfDifferent(opts, optEntityShadowsField, Boolean.FALSE);
                Number curScale = getOptionNumber(opts, optEntityDistanceScalingField);
                if (curScale == null || curScale.doubleValue() > 0.55D) {
                    setOptionIfDifferent(opts, optEntityDistanceScalingField, 0.5D);
                }
            }
            if (chunkSimBoost) {
                // Optimize biome blend radius to 1 (3x3) to eliminate CPU meshing stutter when loading chunks.
                // Do not clamp simulationDistance or renderDistance; player chunk settings are fully respected!
                Number curBiome = getOptionNumber(opts, optBiomeBlendRadiusField);
                if (curBiome == null || curBiome.intValue() > 1) {
                    setOptionIfDifferent(opts, optBiomeBlendRadiusField, 1);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Number getOptionNumber(Object opts, Field field) {
        if (field == null || opts == null) return null;
        try {
            Class<?> ft = field.getType();
            if (ft == int.class) return field.getInt(opts);
            if (ft == double.class) return field.getDouble(opts);
            if (ft == float.class) return field.getFloat(opts);
            Object optInst = field.get(opts);
            if (optInst != null) {
                Object val = null;
                if (optionInstanceGetMethod != null) {
                    val = optionInstanceGetMethod.invoke(optInst);
                } else if (optionInstanceValueField != null) {
                    val = optionInstanceValueField.get(optInst);
                }
                if (val instanceof Number) return (Number) val;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static void setOptionIfDifferent(Object opts, Field field, Object targetVal) {
        if (field == null || opts == null || targetVal == null) return;
        try {
            Class<?> ft = field.getType();
            if (ft == int.class && targetVal instanceof Number) {
                int v = ((Number) targetVal).intValue();
                if (field.getInt(opts) != v) field.setInt(opts, v);
            } else if (ft == boolean.class && targetVal instanceof Boolean) {
                boolean v = (Boolean) targetVal;
                if (field.getBoolean(opts) != v) field.setBoolean(opts, v);
            } else if (ft == double.class && targetVal instanceof Number) {
                double v = ((Number) targetVal).doubleValue();
                if (Math.abs(field.getDouble(opts) - v) > 0.01D) field.setDouble(opts, v);
            } else if (ft == float.class && targetVal instanceof Number) {
                float v = ((Number) targetVal).floatValue();
                if (Math.abs(field.getFloat(opts) - v) > 0.01f) field.setFloat(opts, v);
            } else {
                Object optInst = field.get(opts);
                if (optInst == null) return;
                Object curVal = null;
                if (optionInstanceGetMethod != null) {
                    curVal = optionInstanceGetMethod.invoke(optInst);
                } else if (optionInstanceValueField != null) {
                    curVal = optionInstanceValueField.get(optInst);
                }
                if (curVal != null && curVal.equals(targetVal)) {
                    return;
                }
                if (optionInstanceSetMethod != null) {
                    optionInstanceSetMethod.invoke(optInst, targetVal);
                } else if (optionInstanceValueField != null) {
                    optionInstanceValueField.set(optInst, targetVal);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Retrieves camera pose: float[5] { yaw, pitch, posX, posY, posZ }
     */
    public static void getCameraPose(float[] outPose5) {
        Object mc = getMinecraft();
        if (mc == null || gameRendererField == null || grGetMainCameraMethod == null) return;
        try {
            Object gr = gameRendererField.get(mc);
            if (gr == null) return;
            Object cam = grGetMainCameraMethod.invoke(gr);
            if (cam == null) return;
            if (camGetYRotMethod != null) {
                outPose5[0] = ((Number) camGetYRotMethod.invoke(cam)).floatValue();
            } else if (camYRotField != null) {
                outPose5[0] = camYRotField.getFloat(cam);
            }
            if (camGetXRotMethod != null) {
                outPose5[1] = ((Number) camGetXRotMethod.invoke(cam)).floatValue();
            } else if (camXRotField != null) {
                outPose5[1] = camXRotField.getFloat(cam);
            }
            if (camGetPositionMethod != null) {
                Object vec = camGetPositionMethod.invoke(cam);
                if (vec != null && vec3XField != null) {
                    outPose5[2] = (float) vec3XField.getDouble(vec);
                    outPose5[3] = (float) vec3YField.getDouble(vec);
                    outPose5[4] = (float) vec3ZField.getDouble(vec);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static float getCameraFov() {
        try {
            Object mc = getMinecraft();
            if (mc == null || optionsField == null) return 70.0f;
            Object opts = optionsField.get(mc);
            if (opts == null) return 70.0f;
            if (optFovField != null) {
                Number num = getOptionNumber(opts, optFovField);
                if (num != null && num.floatValue() >= 10.0f && num.floatValue() <= 160.0f) {
                    return num.floatValue();
                }
            }
        } catch (Throwable ignored) {
        }
        return 70.0f;
    }

    /**
     * Dynamically hooks Forge's MinecraftForge.EVENT_BUS across versions 1.18.2 through 26.3.
     */
    public static void registerForgeEvents(
        Runnable onRenderStart,
        Runnable onWorldViewportLock,
        Runnable onWorldRenderEnd,
        Runnable onRenderEnd
    ) {
        try {
            Class<?> mfClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            Field busField = mfClass.getDeclaredField("EVENT_BUS");
            busField.setAccessible(true);
            Object eventBus = busField.get(null);
            if (eventBus == null) return;

            // 1. Hook RenderTickEvent
            boolean hookedPrePost = false;
            try {
                Class<?> preClass = Class.forName("net.minecraftforge.event.TickEvent$RenderTickEvent$Pre");
                Class<?> postClass = Class.forName("net.minecraftforge.event.TickEvent$RenderTickEvent$Post");
                registerEventOnBus(eventBus, preClass, ev -> onRenderStart.run());
                registerEventOnBus(eventBus, postClass, ev -> onRenderEnd.run());
                hookedPrePost = true;
            } catch (ClassNotFoundException ignored) {
            }

            if (!hookedPrePost) {
                try {
                    Class<?> rteClass = Class.forName("net.minecraftforge.event.TickEvent$RenderTickEvent");
                    Field phaseField = findField(rteClass.getSuperclass(), new String[]{"phase"});
                    if (phaseField == null) phaseField = findField(rteClass, new String[]{"phase"});
                    final Field finalPhaseField = phaseField;
                    registerEventOnBus(eventBus, rteClass, ev -> {
                        try {
                            if (finalPhaseField != null) {
                                Object phase = finalPhaseField.get(ev);
                                if (phase != null) {
                                    String pName = phase.toString();
                                    if ("START".equals(pName)) {
                                        onRenderStart.run();
                                    } else if ("END".equals(pName)) {
                                        onRenderEnd.run();
                                    }
                                }
                            }
                        } catch (Throwable ignored) {
                        }
                    });
                } catch (ClassNotFoundException ignored) {
                }
            }

            // 2. Hook ViewportEvent$ComputeFov & ComputeCameraAngles (fires inside GameRenderer.renderLevel right before world render)
            for (String vpEventName : new String[]{
                "net.minecraftforge.client.event.ViewportEvent$ComputeFov",
                "net.minecraftforge.client.event.ViewportEvent$ComputeCameraAngles",
                "net.minecraftforge.client.event.EntityViewRenderEvent$FOVModifier",
                "net.minecraftforge.client.event.EntityViewRenderEvent$CameraSetup"
            }) {
                try {
                    Class<?> vpClass = Class.forName(vpEventName);
                    registerEventOnBus(eventBus, vpClass, ev -> onWorldViewportLock.run());
                } catch (ClassNotFoundException ignored) {
                }
            }

            // 3. Hook RenderLevelStageEvent:
            //    - Every 3D stage (AFTER_SKY, AFTER_SOLID_BLOCKS, AFTER_CUTOUT_BLOCKS, AFTER_ENTITIES, etc.)
            //      keeps the viewport locked at (renderW x renderH) so entities, terrain, and water all match 1:1!
            //    - AFTER_LEVEL triggers the DLSS Super Resolution pass and restores native resolution before the hand!
            try {
                Class<?> rlseClass = Class.forName("net.minecraftforge.client.event.RenderLevelStageEvent");
                Method getStageMethod = findMethod(rlseClass, new String[]{"getStage"});
                registerEventOnBus(eventBus, rlseClass, ev -> {
                    try {
                        if (getStageMethod != null) {
                            Object stage = getStageMethod.invoke(ev);
                            if (stage != null) {
                                String s = stage.toString().toUpperCase(java.util.Locale.ROOT);
                                if (s.contains("AFTER_LEVEL")) {
                                    onWorldRenderEnd.run();
                                } else {
                                    onWorldViewportLock.run();
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                });
            } catch (ClassNotFoundException ignored) {
            }

            // 3b. Hook RenderHandEvent & RenderLevelLastEvent (ensures 3D world is upscaled BEFORE first-person hand is drawn)
            for (String handOrLastEvent : new String[]{
                "net.minecraftforge.client.event.RenderHandEvent",
                "net.minecraftforge.client.event.RenderLevelLastEvent"
            }) {
                try {
                    Class<?> heClass = Class.forName(handOrLastEvent);
                    registerEventOnBus(eventBus, heClass, ev -> onWorldRenderEnd.run());
                } catch (ClassNotFoundException ignored) {
                }
            }

            // 4. Hook RenderGuiOverlayEvent$Pre (1.19-1.20), RenderGameOverlayEvent$Pre (1.18.2), RenderGuiEvent$Pre (1.20.4-26.3)
            for (String overlayClassName : new String[]{
                "net.minecraftforge.client.event.RenderGuiOverlayEvent$Pre",
                "net.minecraftforge.client.event.RenderGameOverlayEvent$Pre",
                "net.minecraftforge.client.event.RenderGuiEvent$Pre"
            }) {
                try {
                    Class<?> ovClass = Class.forName(overlayClassName);
                    registerEventOnBus(eventBus, ovClass, ev -> onWorldRenderEnd.run());
                } catch (ClassNotFoundException ignored) {
                }
            }

            // 5. Hook ScreenEvent$Render$Pre / ScreenEvent$DrawScreenEvent$Pre
            for (String screenEventName : new String[]{
                "net.minecraftforge.client.event.ScreenEvent$Render$Pre",
                "net.minecraftforge.client.event.ScreenEvent$DrawScreenEvent$Pre"
            }) {
                try {
                    Class<?> seClass = Class.forName(screenEventName);
                    registerEventOnBus(eventBus, seClass, ev -> onWorldRenderEnd.run());
                } catch (ClassNotFoundException ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerEventOnBus(Object eventBus, Class<?> eventClass, Consumer<Object> handler) {
        try {
            for (Method m : eventBus.getClass().getMethods()) {
                if (!"addListener".equals(m.getName())) continue;
                Class<?>[] pts = m.getParameterTypes();
                if (pts.length == 4 && pts[1] == boolean.class && pts[2] == Class.class && Consumer.class.isAssignableFrom(pts[3])) {
                    Object normalPriority = pts[0].getEnumConstants()[2];
                    m.setAccessible(true);
                    m.invoke(eventBus, normalPriority, false, eventClass, handler);
                    return;
                }
                if (pts.length == 2 && pts[0] == Class.class && Consumer.class.isAssignableFrom(pts[1])) {
                    m.setAccessible(true);
                    m.invoke(eventBus, eventClass, handler);
                    return;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static Method findMethod(Class<?> clazz, String[] names) {
        for (String name : names) {
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String[] names) {
        if (clazz == null) return null;
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        return null;
    }
}
