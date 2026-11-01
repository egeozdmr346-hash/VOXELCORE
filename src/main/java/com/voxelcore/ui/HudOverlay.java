package com.voxelcore.ui;

import com.voxelcore.bootstrap.Bootstrap;
import com.voxelcore.config.VoxelConfig;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws the FPS and memory overlays in the top-left corner. Reads the config every frame,
 * so toggling in the settings screen takes effect immediately.
 */
public final class HudOverlay {

    private static final OverlayStats STATS = new OverlayStats();
    private static final PerfMonitor PERF = new PerfMonitor();

    public static PerfMonitor perf() {
        return PERF;
    }

    private HudOverlay() { }

    public static void register() {
        HudRenderCallback.EVENT.register(HudOverlay::render);
    }

    private static void render(DrawContext context, float tickDelta) {
        long now = System.nanoTime();
        STATS.onFrame(now);
        PERF.onFrame(now);
        PERF.refreshIfDue(now);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden) {
            return;
        }
        VoxelConfig config = Bootstrap.get().config();

        int y = 4;
        if (config.isFpsOverlayEnabled()) {
            context.drawTextWithShadow(client.textRenderer, String.format("%s (avg %.1f ms, p99 %.1f ms)",
                    STATS.fpsText(), PERF.frameAvgMs(), PERF.frameP99Ms()), 4, y, 0xFFFFFF);
            y += 10;
        }
        if (config.isMemoryOverlayEnabled()) {
            context.drawTextWithShadow(client.textRenderer, OverlayStats.memoryText(), 4, y, 0xFFFFFF);
        }
    }
}
