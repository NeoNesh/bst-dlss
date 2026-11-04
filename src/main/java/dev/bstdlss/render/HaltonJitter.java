package dev.bstdlss.render;

/**
 * Low-discrepancy Halton(2, 3) sub-pixel camera jitter generator for NVIDIA DLSS Super Resolution.
 * Produces jitter offsets in [-0.5, +0.5] render-pixel coordinates and NDC projection matrix offsets.
 */
public final class HaltonJitter {
    private static final int SEQUENCE_LENGTH = 64;
    private static final float[] JITTER_X = new float[SEQUENCE_LENGTH];
    private static final float[] JITTER_Y = new float[SEQUENCE_LENGTH];

    static {
        for (int i = 0; i < SEQUENCE_LENGTH; i++) {
            JITTER_X[i] = halton(i + 1, 2) - 0.5f;
            JITTER_Y[i] = halton(i + 1, 3) - 0.5f;
        }
    }

    private HaltonJitter() {
    }

    private static float halton(int index, int base) {
        float f = 1.0f;
        float r = 0.0f;
        int current = index;
        while (current > 0) {
            f /= (float) base;
            r += f * (float) (current % base);
            current /= base;
        }
        return r;
    }

    /**
     * Recommended phase count based on scale factor:
     * basePhases * (outWidth / renderWidth)^2
     */
    public static int getPhaseCount(int renderWidth, int outWidth) {
        if (renderWidth <= 0 || outWidth <= 0) return 8;
        float ratio = (float) outWidth / (float) renderWidth;
        int phases = Math.round(8.0f * ratio * ratio);
        return Math.max(8, Math.min(SEQUENCE_LENGTH, phases));
    }

    public static float getJitterX(long frameIndex, int phaseCount) {
        int idx = (int) (Math.abs(frameIndex) % Math.max(1, Math.min(SEQUENCE_LENGTH, phaseCount)));
        return JITTER_X[idx];
    }

    public static float getJitterY(long frameIndex, int phaseCount) {
        int idx = (int) (Math.abs(frameIndex) % Math.max(1, Math.min(SEQUENCE_LENGTH, phaseCount)));
        return JITTER_Y[idx];
    }
}
