package dev.bstdlss.gui;

import dev.bstdlss.bridge.McRuntimeBridge;
import dev.bstdlss.config.BstDlssConfig;
import dev.bstdlss.i18n.I18n;
import dev.bstdlss.nativebridge.BstDlssNative;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Pure OpenGL 3.2 Core Profile 2D UI & Telemetry Renderer for BST DLSS.
 * Features an explicit 7-row (byte[7]) 5x7 pixel font supporting both ASCII (A-Z, 0-9)
 * and Russian Cyrillic (А-Я) with zero bit-shift overflow.
 */
public final class BstDlssHudRenderer {
    private static boolean initialized = false;
    private static int uiProg = 0;
    private static int uScreenSize = -1;
    private static int vao = 0;
    private static int vbo = 0;

    private static final int MAX_VERTS = 49152;
    private static final FloatBuffer VERT_BUF = BufferUtils.createFloatBuffer(MAX_VERTS * 6);
    private static int vertCount = 0;

    private static boolean menuOpen = false;
    private static boolean prevMouseDown = false;
    private static int savedCursorMode = GLFW.GLFW_CURSOR_NORMAL;
    private static String toastMessage = "";
    private static long toastExpireTimeMs = 0L;

    private static final Map<Character, byte[]> GLYPHS = new HashMap<>();

    static {
        initGlyphs();
    }

    private static final String UI_VERT =
        "#version 150 core\n" +
        "in vec2 aPos;\n" +
        "in vec4 aColor;\n" +
        "uniform vec2 uScreenSize;\n" +
        "out vec4 vColor;\n" +
        "void main() {\n" +
        "    vec2 ndc = (aPos / max(vec2(1.0), uScreenSize)) * 2.0 - 1.0;\n" +
        "    ndc.y = -ndc.y;\n" +
        "    vColor = aColor;\n" +
        "    gl_Position = vec4(ndc, 0.0, 1.0);\n" +
        "}\n";

    private static final String UI_FRAG =
        "#version 150 core\n" +
        "in vec4 vColor;\n" +
        "out vec4 fragColor;\n" +
        "void main() {\n" +
        "    fragColor = vColor;\n" +
        "}\n";

    private BstDlssHudRenderer() {
    }

    private static void g(char ch, String r0, String r1, String r2, String r3, String r4, String r5, String r6) {
        String[] rows = {r0, r1, r2, r3, r4, r5, r6};
        byte[] mask = new byte[7];
        for (int r = 0; r < 7; r++) {
            String s = rows[r];
            int b = 0;
            for (int c = 0; c < 5 && c < s.length(); c++) {
                if (s.charAt(c) == '#') {
                    b |= (1 << (4 - c));
                }
            }
            mask[r] = (byte) b;
        }
        GLYPHS.put(ch, mask);
    }

    private static void initGlyphs() {
        // Digits
        g('0', ".###.", "#..##", "#.#.#", "##..#", "#...#", "#...#", ".###.");
        g('1', "..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('2', ".###.", "#...#", "....#", "..##.", ".#...", "#....", "#####");
        g('3', "#####", "....#", "...#.", "..##.", "....#", "#...#", ".###.");
        g('4', "...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#.");
        g('5', "#####", "#....", "####.", "....#", "....#", "#...#", ".###.");
        g('6', ".###.", "#....", "####.", "#...#", "#...#", "#...#", ".###.");
        g('7', "#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#...");
        g('8', ".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###.");
        g('9', ".###.", "#...#", "#...#", ".####", "....#", "....#", ".###.");

        // Punctuation & Symbols
        g(' ', ".....", ".....", ".....", ".....", ".....", ".....", ".....");
        g('.', ".....", ".....", ".....", ".....", ".....", "..##.", "..##.");
        g(',', ".....", ".....", ".....", ".....", ".....", "..#..", ".#...");
        g(':', ".....", "..##.", "..##.", ".....", "..##.", "..##.", ".....");
        g('-', ".....", ".....", ".....", "#####", ".....", ".....", ".....");
        g('+', ".....", "..#..", "..#..", "#####", "..#..", "..#..", ".....");
        g('/', "....#", "...#.", "...#.", "..#..", ".#...", ".#...", "#....");
        g('|', "..#..", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('(', "...#.", "..#..", ".#...", ".#...", ".#...", "..#..", "...#.");
        g(')', ".#...", "..#..", "...#.", "...#.", "...#.", "..#..", ".#...");
        g('[', ".###.", ".#...", ".#...", ".#...", ".#...", ".#...", ".###.");
        g(']', ".###.", "...#.", "...#.", "...#.", "...#.", "...#.", ".###.");
        g('%', "##..#", "##..#", "...#.", "..#..", ".#...", "#..##", "#..##");
        g('>', "#....", ".#...", "..#..", "...#.", "..#..", ".#...", "#....");
        g('<', "....#", "...#.", "..#..", ".#...", "..#..", "...#.", "....#");
        g('=', ".....", ".....", "#####", ".....", "#####", ".....", ".....");
        g('!', "..#..", "..#..", "..#..", "..#..", "..#..", ".....", "..#..");
        g('?', ".###.", "#...#", "....#", "..##.", "..#..", ".....", "..#..");
        g('_', ".....", ".....", ".....", ".....", ".....", ".....", "#####");
        g('&', ".##..", "#..#.", ".##..", "#..#.", "#..##", "#...#", ".###.");
        g('*', ".....", "#.#.#", ".###.", "#####", ".###.", "#.#.#", ".....");
        g(';', ".....", "..##.", "..##.", ".....", "..#..", ".#...", ".....");
        g('\'', "..##.", "..##.", ".#...", ".....", ".....", ".....", ".....");
        g('\"', ".#.#.", ".#.#.", ".....", ".....", ".....", ".....", ".....");
        g('#', ".#.#.", "#####", ".#.#.", ".#.#.", "#####", ".#.#.", ".....");

        // Latin A-Z
        g('A', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('B', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('C', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('D', "####.", "#...#", "#...#", "#...#", "#...#", "#...#", "####.");
        g('E', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('F', "#####", "#....", "#....", "####.", "#....", "#....", "#....");
        g('G', ".###.", "#...#", "#....", "#.###", "#...#", "#...#", ".###.");
        g('H', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('I', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('J', "..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##..");
        g('K', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('L', "#....", "#....", "#....", "#....", "#....", "#....", "#####");
        g('M', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('N', "#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#");
        g('O', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('P', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('Q', ".###.", "#...#", "#...#", "#...#", "#.#.#", "#..#.", ".##.#");
        g('R', "####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#");
        g('S', ".####", "#....", "#....", ".###.", "....#", "....#", "####.");
        g('T', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('U', "#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('V', "#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#..");
        g('W', "#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#");
        g('X', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Y', "#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#..");
        g('Z', "#####", "....#", "...#.", "..#..", ".#...", "#....", "#####");

        // Russian Cyrillic А-Я
        g('А', ".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('Б', "#####", "#....", "#....", "####.", "#...#", "#...#", "####.");
        g('В', "####.", "#...#", "#...#", "####.", "#...#", "#...#", "####.");
        g('Г', "#####", "#....", "#....", "#....", "#....", "#....", "#....");
        g('Д', "..##.", ".#..#", ".#..#", ".#..#", ".#..#", "#####", "#...#");
        g('Е', "#####", "#....", "#....", "####.", "#....", "#....", "#####");
        g('Ё', ".#.#.", "#####", "#....", "####.", "#....", "#....", "#####");
        g('Ж', "#.#.#", "#.#.#", ".###.", "..#..", ".###.", "#.#.#", "#.#.#");
        g('З', ".###.", "#...#", "....#", "..##.", "....#", "#...#", ".###.");
        g('И', "#...#", "#..##", "#.#.#", "##..#", "#...#", "#...#", "#...#");
        g('Й', ".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", "#...#");
        g('К', "#...#", "#..#.", "#.#..", "##...", "#.#..", "#..#.", "#...#");
        g('Л', "..###", ".#..#", ".#..#", ".#..#", ".#..#", ".#..#", "#...#");
        g('М', "#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#");
        g('Н', "#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#");
        g('О', ".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('П', "#####", "#...#", "#...#", "#...#", "#...#", "#...#", "#...#");
        g('Р', "####.", "#...#", "#...#", "####.", "#....", "#....", "#....");
        g('С', ".###.", "#...#", "#....", "#....", "#....", "#...#", ".###.");
        g('Т', "#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#..");
        g('У', "#...#", "#...#", "#...#", ".####", "....#", "#...#", ".###.");
        g('Ф', ".###.", "#.#.#", "#.#.#", "#.#.#", ".###.", "..#..", "..#..");
        g('Х', "#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#");
        g('Ц', "#..#.", "#..#.", "#..#.", "#..#.", "#..#.", "#####", "....#");
        g('Ч', "#...#", "#...#", "#...#", ".####", "....#", "....#", "....#");
        g('Ш', "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#####");
        g('Щ', "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#.#.#", "#####", "....#");
        g('Ъ', "##...", ".#...", ".#...", ".###.", ".#..#", ".#..#", ".###.");
        g('Ы', "#...#", "#...#", "#...#", "###.#", "#.#.#", "#.#.#", "###.#");
        g('Ь', "#....", "#....", "#....", "####.", "#...#", "#...#", "####.");
        g('Э', ".###.", "#...#", "....#", ".###.", "....#", "#...#", ".###.");
        g('Я', ".####", "#...#", "#...#", ".####", "..#.#", ".#..#", "#...#");

        // Ukrainian Cyrillic Extensions
        g('І', ".###.", "..#..", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('Ї', "#.#.#", ".....", "..#..", "..#..", "..#..", "..#..", ".###.");
        g('Є', ".###.", "#...#", "####.", "#....", "#...#", "#...#", ".###.");
        g('Ґ', "#####", ".#...", "#....", "#....", "#....", "#....", "#....");

        // German, Spanish & French Latin Extensions
        g('Ä', "#.#.#", ".....", ".###.", "#...#", "#####", "#...#", "#...#");
        g('Ö', "#.#.#", ".....", ".###.", "#...#", "#...#", "#...#", ".###.");
        g('Ü', "#.#.#", ".....", "#...#", "#...#", "#...#", "#...#", ".###.");
        g('ß', ".###.", "#...#", "####.", "#...#", "####.", "#...#", "####.");
        g('Ñ', "##.##", ".....", "#...#", "##..#", "#.#.#", "#..##", "#...#");
        g('É', "...#.", "..#..", "#####", "#....", "####.", "#....", "#####");
        g('È', ".#...", "..#..", "#####", "#....", "####.", "#....", "#####");
        g('Á', "...#.", "..#..", ".###.", "#...#", "#####", "#...#", "#...#");
        g('Í', "...#.", "..#..", ".###.", "..#..", "..#..", "..#..", ".###.");
        g('Ó', "...#.", "..#..", ".###.", "#...#", "#...#", "#...#", ".###.");
        g('Ú', "...#.", "..#..", "#...#", "#...#", "#...#", "#...#", ".###.");
    }

    private static void ensureInit() {
        if (initialized) return;
        initialized = true;

        int vs = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
        GL20.glShaderSource(vs, UI_VERT);
        GL20.glCompileShader(vs);

        int fs = GL20.glCreateShader(GL20.GL_FRAGMENT_SHADER);
        GL20.glShaderSource(fs, UI_FRAG);
        GL20.glCompileShader(fs);

        uiProg = GL20.glCreateProgram();
        GL20.glBindAttribLocation(uiProg, 0, "aPos");
        GL20.glBindAttribLocation(uiProg, 1, "aColor");
        GL30.glBindFragDataLocation(uiProg, 0, "fragColor");
        GL20.glAttachShader(uiProg, vs);
        GL20.glAttachShader(uiProg, fs);
        GL20.glLinkProgram(uiProg);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        uScreenSize = GL20.glGetUniformLocation(uiProg, "uScreenSize");

        vao = GL30.glGenVertexArrays();
        vbo = GL15.glGenBuffers();
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, (long) MAX_VERTS * 6 * 4, GL15.GL_STREAM_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 24, 0);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 24, 8);
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
    }

    public static boolean isMenuOpen() {
        return menuOpen;
    }

    public static void toggleMenu(long glfwWindow) {
        menuOpen = !menuOpen;
        if (glfwWindow != 0L) {
            if (menuOpen) {
                savedCursorMode = GLFW.glfwGetInputMode(glfwWindow, GLFW.GLFW_CURSOR);
                GLFW.glfwSetInputMode(glfwWindow, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
            } else {
                GLFW.glfwSetInputMode(glfwWindow, GLFW.GLFW_CURSOR, savedCursorMode);
            }
        }
    }

    public static void showToast(String message) {
        toastMessage = message;
        toastExpireTimeMs = System.currentTimeMillis() + 3000L;
    }

    public static void renderOverlayAndMenu(
        long glfwWindow,
        int screenW, int screenH,
        int renderW, int renderH,
        int fps, float frameTimeMs,
        float dlssEvalMs,
        boolean usingHardwareNgx
    ) {
        if (screenW <= 0 || screenH <= 0) return;
        BstDlssConfig cfg = BstDlssConfig.get();
        long now = System.currentTimeMillis();
        boolean hasToast = (now < toastExpireTimeMs && toastMessage != null && !toastMessage.isEmpty());
        boolean screenOpen = McRuntimeBridge.isScreenOpen();

        if (!cfg.isShowTelemetryOverlay() && !menuOpen && !hasToast && !screenOpen) {
            return;
        }

        double[] mxArr = new double[1];
        double[] myArr = new double[1];
        boolean mouseClicked = false;
        if (glfwWindow != 0L) {
            GLFW.glfwGetCursorPos(glfwWindow, mxArr, myArr);
            boolean down = GLFW.glfwGetMouseButton(glfwWindow, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            mouseClicked = (down && !prevMouseDown);
            prevMouseDown = down;
        }
        float mx = (float) mxArr[0];
        float my = (float) myArr[0];

        ensureInit();
        VERT_BUF.clear();
        vertCount = 0;

        String gpuName = BstDlssNative.getGpuNameSafe();
        String dlssVer = BstDlssNative.getDlssVersionSafe();
        int rtxSeries = BstDlssNative.getRtxSeriesSafe();
        String rtxTag = rtxSeries > 0 ? ("RTX " + rtxSeries + "XX") : "GPU";

        // 1. Telemetry HUD Box (top-left) - only rendered when HUD is enabled
        if (cfg.isShowTelemetryOverlay()) {
            float bx = 10.0f;
            float by = 10.0f;
            float bw = 490.0f;
            float bh = 106.0f;

            drawRect(bx, by, bw, bh, 0.04f, 0.06f, 0.09f, 0.86f);
            drawRect(bx, by, 4.0f, bh, 0.46f, 0.80f, 0.05f, 1.0f);

            drawText(I18n.format("hud.title", dlssVer.toUpperCase(Locale.ROOT), gpuName.toUpperCase(Locale.ROOT)),
                bx + 12, by + 10, 1.9f, 0.50f, 0.95f, 0.15f, 1.0f);

            String modeLine = I18n.format("hud.mode",
                cfg.getQualityMode().getDisplayName().toUpperCase(Locale.ROOT),
                cfg.getDlssPreset().name());
            drawText(modeLine, bx + 12, by + 30, 1.6f, 0.95f, 0.98f, 1.0f, 1.0f);

            String resLine = I18n.format("hud.render",
                renderW, renderH, screenW, screenH,
                usingHardwareNgx ? I18n.get("hud.core.ngx") : I18n.get("hud.core.easu"));
            drawText(resLine, bx + 12, by + 48, 1.6f, 0.78f, 0.88f, 0.98f, 1.0f);

            String perfLine = I18n.format("hud.perf",
                fps, frameTimeMs, dlssEvalMs, cfg.isTurboFpsUncap() ? I18n.get("status.on") : I18n.get("status.off"));
            drawText(perfLine, bx + 12, by + 66, 1.6f, 0.65f, 0.98f, 0.65f, 1.0f);

            String archLine = I18n.format("hud.arch",
                rtxTag, Math.round(cfg.getSharpness() * 100.0f));
            drawText(archLine, bx + 12, by + 84, 1.5f, 0.75f, 0.80f, 0.88f, 1.0f);
        } else if (screenOpen && !menuOpen) {
            // Discreet Corner Tab Button in Minecraft Menu (Pause Menu / Title Screen)
            float tabX = 10.0f;
            float tabY = 10.0f;
            float tabW = 96.0f;
            float tabH = 24.0f;
            boolean tabHovered = (mx >= tabX && mx <= tabX + tabW && my >= tabY && my <= tabY + tabH);
            if (tabHovered) {
                drawRect(tabX, tabY, tabW, tabH, 0.16f, 0.28f, 0.10f, 0.95f);
                drawRect(tabX, tabY, 3.5f, tabH, 0.50f, 0.95f, 0.15f, 1.0f);
                if (mouseClicked) {
                    toggleMenu(glfwWindow);
                }
            } else {
                drawRect(tabX, tabY, tabW, tabH, 0.06f, 0.09f, 0.14f, 0.88f);
                drawRect(tabX, tabY, 3.5f, tabH, 0.40f, 0.75f, 0.10f, 0.95f);
            }
            drawText(I18n.get("tab.icon"), tabX + 10.0f, tabY + 6.0f, 1.4f, 0.88f, 0.98f, 0.88f, 1.0f);
        }

        // 2. Toast notification (top-center)
        if (hasToast) {
            float tw = Math.max(340.0f, toastMessage.length() * 12.0f + 32.0f);
            float th = 38.0f;
            float tx = (screenW - tw) * 0.5f;
            float ty = 20.0f;
            drawRect(tx, ty, tw, th, 0.05f, 0.09f, 0.05f, 0.92f);
            drawRect(tx, ty, tw, 3.0f, 0.46f, 0.85f, 0.05f, 1.0f);
            drawText(toastMessage.toUpperCase(Locale.ROOT), tx + 16.0f, ty + 12.0f, 2.0f, 0.65f, 1.0f, 0.35f, 1.0f);
        }

        // 3. Interactive BST DLSS RTX Control Panel (F8)
        if (menuOpen) {
            renderControlPanel(glfwWindow, screenW, screenH, renderW, renderH, usingHardwareNgx, gpuName, dlssVer, rtxSeries, mx, my, mouseClicked);
        }

        flushUiBatch(screenW, screenH);
    }

    private static void renderControlPanel(
        long glfwWindow,
        int screenW, int screenH,
        int renderW, int renderH,
        boolean usingHardwareNgx,
        String gpuName, String dlssVer, int rtxSeries,
        float mx, float my, boolean mouseClicked
    ) {
        BstDlssConfig cfg = BstDlssConfig.get();

        float pw = Math.min(screenW - 24.0f, 820.0f);
        float ph = Math.min(screenH - 24.0f, 410.0f);
        float px = (screenW - pw) * 0.5f;
        float py = (screenH - ph) * 0.5f;

        drawRect(0, 0, screenW, screenH, 0.0f, 0.0f, 0.0f, 0.52f);
        drawRect(px, py, pw, ph, 0.06f, 0.08f, 0.12f, 0.96f);
        drawRect(px, py, pw, 4.0f, 0.46f, 0.85f, 0.05f, 1.0f);

        drawText(I18n.format("menu.title", dlssVer.toUpperCase(Locale.ROOT)),
            px + 18, py + 14, 1.85f, 0.50f, 0.95f, 0.15f, 1.0f);
        drawText(I18n.format("menu.gpu", gpuName.toUpperCase(Locale.ROOT), (rtxSeries > 0 ? String.valueOf(rtxSeries) : "20"), dlssVer.toUpperCase(Locale.ROOT)),
            px + 18, py + 36, 1.5f, 0.82f, 0.88f, 0.95f, 1.0f);
        drawText(I18n.format("menu.core_status",
                (usingHardwareNgx ? I18n.get("menu.core.hw_desc") : I18n.get("menu.core.fallback_desc")),
                renderW, renderH, screenW, screenH),
            px + 18, py + 54, 1.45f, 0.65f, 0.95f, 0.65f, 1.0f);

        float colW = (pw - 48.0f) * 0.5f;
        float leftX = px + 18.0f;
        float rightX = px + 30.0f + colW;
        float btnH = 34.0f;
        float gapY = 42.0f;
        float startY = py + 80.0f;

        // --- LEFT COLUMN: DLSS & IMAGE SHARPNESS ---
        if (drawButton(leftX, startY, colW, btnH,
            I18n.format("menu.btn.mode", cfg.getQualityMode().getDisplayName().toUpperCase(Locale.ROOT)), mx, my, mouseClicked)) {
            cfg.setQualityMode(cfg.getQualityMode().next());
        }
        if (drawButton(leftX, startY + gapY, colW, btnH,
            I18n.format("menu.btn.preset", cfg.getDlssPreset().getDisplayName().toUpperCase(Locale.ROOT)), mx, my, mouseClicked)) {
            cfg.setDlssPreset(cfg.getDlssPreset().next());
        }
        int sharpPct = Math.round(cfg.getSharpness() * 100.0f);
        if (drawButton(leftX, startY + gapY * 2, colW, btnH,
            I18n.format("menu.btn.sharpness", sharpPct), mx, my, mouseClicked)) {
            float nextSharp = cfg.getSharpness() + 0.10f;
            if (nextSharp > 0.65f) nextSharp = 0.0f;
            cfg.setSharpness(Math.round(nextSharp * 100.0f) / 100.0f);
        }
        if (drawButton(leftX, startY + gapY * 3, colW, btnH,
            I18n.format("menu.btn.mipmapping", cfg.isPixelArtCrispFilter() ? I18n.get("menu.mipmapping.crisp") : I18n.get("menu.mipmapping.standard")), mx, my, mouseClicked)) {
            cfg.setPixelArtCrispFilter(!cfg.isPixelArtCrispFilter());
        }
        if (drawButton(leftX, startY + gapY * 4, colW, btnH,
            I18n.format("menu.btn.engine", cfg.isPreferHardwareNgx() ? I18n.get("menu.engine.tensor") : I18n.get("menu.engine.easu")), mx, my, mouseClicked)) {
            cfg.setPreferHardwareNgx(!cfg.isPreferHardwareNgx());
        }

        // --- RIGHT COLUMN: TURBO FPS BOOST SUITE (NON-CONFLICTING) ---
        if (drawButton(rightX, startY, colW, btnH,
            I18n.format("menu.btn.fps_uncap", cfg.isTurboFpsUncap() ? I18n.get("menu.status.uncapped") : I18n.get("status.off")), mx, my, mouseClicked)) {
            cfg.setTurboFpsUncap(!cfg.isTurboFpsUncap());
        }
        if (drawButton(rightX, startY + gapY, colW, btnH,
            I18n.format("menu.btn.entity_boost", cfg.isSmartEntityBoost() ? I18n.get("menu.status.max_fps") : I18n.get("status.off")), mx, my, mouseClicked)) {
            cfg.setSmartEntityBoost(!cfg.isSmartEntityBoost());
        }
        if (drawButton(rightX, startY + gapY * 2, colW, btnH,
            I18n.format("menu.btn.chunk_boost", cfg.isChunkSimulationBoost() ? I18n.get("menu.status.turbo") : I18n.get("status.off")), mx, my, mouseClicked)) {
            cfg.setChunkSimulationBoost(!cfg.isChunkSimulationBoost());
        }
        if (drawButton(rightX, startY + gapY * 3, colW, btnH,
            I18n.format("menu.btn.frame_gen", cfg.isFrameGenerationEnabled() ? I18n.get("status.on") : I18n.get("status.off")), mx, my, mouseClicked)) {
            cfg.setFrameGenerationEnabled(!cfg.isFrameGenerationEnabled());
        }
        if (drawButton(rightX, startY + gapY * 4, colW, btnH,
            I18n.format("menu.btn.reflex", cfg.isReflexLowLatency() ? I18n.get("menu.status.low_latency") : I18n.get("status.off")), mx, my, mouseClicked)) {
            cfg.setReflexLowLatency(!cfg.isReflexLowLatency());
        }

        // Bottom Row: HUD Toggle & Close
        float botY = startY + gapY * 5 + 4.0f;
        if (drawButton(leftX, botY, colW, btnH,
            I18n.format("menu.btn.hud_overlay", cfg.isShowTelemetryOverlay() ? I18n.get("status.shown") : I18n.get("status.hidden")), mx, my, mouseClicked)) {
            cfg.setShowTelemetryOverlay(!cfg.isShowTelemetryOverlay());
        }
        if (drawButton(rightX, botY, colW, btnH,
            I18n.get("menu.btn.save_close"), mx, my, mouseClicked)) {
            toggleMenu(glfwWindow);
        }

        drawText(I18n.get("menu.footer"),
            px + 18, py + ph - 20.0f, 1.45f, 0.70f, 0.78f, 0.86f, 1.0f);
    }

    private static boolean drawButton(
        float x, float y, float w, float h,
        String label, float mx, float my, boolean clicked
    ) {
        boolean hovered = (mx >= x && mx <= x + w && my >= y && my <= y + h);
        if (hovered) {
            drawRect(x, y, w, h, 0.22f, 0.36f, 0.12f, 0.95f);
            drawRect(x, y, 4.0f, h, 0.56f, 0.92f, 0.15f, 1.0f);
        } else {
            drawRect(x, y, w, h, 0.13f, 0.16f, 0.22f, 0.92f);
            drawRect(x, y, 3.0f, h, 0.35f, 0.55f, 0.10f, 1.0f);
        }
        drawText(label, x + 14.0f, y + (h - 12.0f) * 0.5f, 1.70f, 0.95f, 0.98f, 1.0f, 1.0f);
        return hovered && clicked;
    }

    private static void drawRect(float x, float y, float w, float h, float r, float g, float b, float a) {
        if (vertCount + 6 >= MAX_VERTS) return;
        putVert(x,     y,     r, g, b, a);
        putVert(x + w, y,     r, g, b, a);
        putVert(x + w, y + h, r, g, b, a);
        putVert(x,     y,     r, g, b, a);
        putVert(x + w, y + h, r, g, b, a);
        putVert(x,     y + h, r, g, b, a);
    }

    private static void putVert(float x, float y, float r, float g, float b, float a) {
        VERT_BUF.put(x).put(y).put(r).put(g).put(b).put(a);
        vertCount++;
    }

    private static void drawText(String text, float x, float y, float scale, float r, float g, float b, float a) {
        if (text == null) return;
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toUpperCase(text.charAt(i));
            byte[] rows = GLYPHS.get(c);
            if (rows != null) {
                for (int row = 0; row < 7; row++) {
                    int rowBits = rows[row] & 0xFF;
                    if (rowBits == 0) continue;
                    for (int col = 0; col < 5; col++) {
                        if (((rowBits >>> (4 - col)) & 1) != 0) {
                            drawRect(cx + col * scale, y + row * scale, scale, scale, r, g, b, a);
                        }
                    }
                }
            }
            cx += 6.0f * scale;
        }
    }

    private static void flushUiBatch(int screenW, int screenH) {
        if (vertCount == 0) return;
        VERT_BUF.flip();

        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cullWasEnabled  = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevProgram         = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao             = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuf        = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);

        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL20.glUseProgram(uiProg);
        GL20.glUniform2f(uScreenSize, (float) screenW, (float) screenH);

        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, VERT_BUF);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, vertCount);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuf);
        GL30.glBindVertexArray(prevVao);
        GL20.glUseProgram(prevProgram);

        if (!blendWasEnabled) GL11.glDisable(GL11.GL_BLEND);
        if (depthWasEnabled)  GL11.glEnable(GL11.GL_DEPTH_TEST);
        if (cullWasEnabled)   GL11.glEnable(GL11.GL_CULL_FACE);
    }
}
