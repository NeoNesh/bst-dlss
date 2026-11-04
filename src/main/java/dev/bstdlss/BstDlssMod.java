package dev.bstdlss;

import dev.bstdlss.bridge.McRuntimeBridge;
import dev.bstdlss.nativebridge.BstDlssNative;
import dev.bstdlss.render.BstDlssPipeline;
import net.minecraftforge.fml.common.Mod;

/**
 * Main Forge Mod Entrypoint for BST DLSS (NVIDIA RTX 20xx - 50xx Hardware Upscaler).
 * Compatible across Minecraft Forge 1.18.2 through 26.3 with zero external mod dependencies.
 */
@Mod(BstDlssMod.MOD_ID)
public class BstDlssMod {
    public static final String MOD_ID = "bstdlss";
    public static final String MOD_NAME = "BST DLSS - RTX Hardware Upscaler";
    public static final String VERSION = "1.0.0";

    public BstDlssMod() {
        initializeMod();
    }

    private static synchronized void initializeMod() {
        try {
            McRuntimeBridge.registerForgeEvents(
                BstDlssPipeline::onFrameStart,
                BstDlssPipeline::onWorldViewportLock,
                BstDlssPipeline::onWorldRenderComplete,
                BstDlssPipeline::onFrameEnd
            );
        } catch (Throwable ignored) {
        }
    }
}
