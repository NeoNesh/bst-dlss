package dev.bstdlss.nativebridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

/**
 * JNI Bridge to bst_dlss_native.dll (Direct3D 11 + WGL_NV_DX_interop2 + NVIDIA NGX Runtime).
 * Automatically extracts the native library from the mod JAR and initializes the user's
 * system NVIDIA NGX driver runtime (_nvngx.dll & OTA DLSS models) for RTX 20xx - 50xx GPUs.
 */
public final class BstDlssNative {
    private static boolean libraryLoaded = false;
    private static boolean initialized = false;
    private static boolean hardwareNgxActive = false;
    private static String fallbackReason = "";

    private BstDlssNative() {
    }

    public static synchronized boolean loadAndInit(File gameDir) {
        if (initialized) {
            return hardwareNgxActive;
        }
        initialized = true;

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win") || (!osArch.contains("64") && !osArch.contains("amd64"))) {
            fallbackReason = "Non-Windows x64 platform (" + osName + " / " + osArch + ")";
            return false;
        }

        try {
            File baseDir = new File(gameDir, "bst_dlss");
            File nativesDir = new File(baseDir, "natives");
            File ngxCacheDir = new File(baseDir, "ngx_cache");
            nativesDir.mkdirs();
            ngxCacheDir.mkdirs();

            File extractedDll = new File(nativesDir, "bst_dlss_native.dll");
            try (InputStream is = BstDlssNative.class.getResourceAsStream("/natives/win64/bst_dlss_native.dll")) {
                if (is == null && !extractedDll.exists()) {
                    fallbackReason = "Embedded /natives/win64/bst_dlss_native.dll not found in JAR";
                    return false;
                }
                if (is != null) {
                    try (FileOutputStream fos = new FileOutputStream(extractedDll)) {
                        byte[] buffer = new byte[16384];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    } catch (Throwable writeErr) {
                        if (!extractedDll.exists()) {
                            throw writeErr;
                        }
                    }
                }
            }

            System.load(extractedDll.getAbsolutePath());
            libraryLoaded = true;

            int initCode = nativeInit(ngxCacheDir.getAbsolutePath());
            hardwareNgxActive = (initCode == 1) && nativeIsNgxDlssAvailable();
            if (!hardwareNgxActive) {
                fallbackReason = "NGX init returned " + initCode + " (" + nativeGetDiagnostics() + ")";
            }
            return hardwareNgxActive;
        } catch (Throwable t) {
            fallbackReason = "Native init error: " + t.getClass().getSimpleName() + ": " + t.getMessage();
            return false;
        }
    }

    public static boolean isLibraryLoaded() {
        return libraryLoaded;
    }

    public static boolean isHardwareNgxActive() {
        return libraryLoaded && hardwareNgxActive;
    }

    public static String getGpuNameSafe() {
        if (!libraryLoaded) return "OpenGL GPU";
        try {
            String name = nativeGetGpuName();
            return (name != null && !name.isEmpty()) ? name : "OpenGL GPU";
        } catch (Throwable t) {
            return "OpenGL GPU";
        }
    }

    public static String getDlssVersionSafe() {
        if (!libraryLoaded) return "N/A";
        try {
            String ver = nativeGetDlssVersion();
            return (ver != null && !ver.isEmpty()) ? ver : "v310.6.0.0";
        } catch (Throwable t) {
            return "v310.6.0.0";
        }
    }

    public static int getRtxSeriesSafe() {
        if (!libraryLoaded) return 0;
        try {
            return nativeGetRtxSeries();
        } catch (Throwable t) {
            return 0;
        }
    }

    public static boolean isFrameGenSupportedSafe() {
        if (!libraryLoaded) return false;
        try {
            return nativeIsFrameGenAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    public static String getDiagnosticsSafe() {
        if (!libraryLoaded) return fallbackReason;
        try {
            return nativeGetDiagnostics();
        } catch (Throwable t) {
            return fallbackReason;
        }
    }

    // --- Native JNI declarations ---
    public static native int nativeInit(String cacheDir);
    public static native String nativeGetGpuName();
    public static native String nativeGetDlssVersion();
    public static native int nativeGetRtxSeries();
    public static native boolean nativeIsNgxDlssAvailable();
    public static native boolean nativeIsFrameGenAvailable();
    public static native int[] nativeGetOptimalResolution(int outWidth, int outHeight, int qualityMode);
    public static native int[] nativeCreateSession(int renderWidth, int renderHeight, int outWidth, int outHeight, int qualityMode, int preset);
    public static native long nativeEvaluateDlss(
        float jitterX,
        float jitterY,
        float mvScaleX,
        float mvScaleY,
        float sharpness,
        boolean reset,
        float preExposure,
        float frameTimeDeltaMs
    );
    public static native String nativeGetDiagnostics();
    public static native void nativeShutdown();
}
