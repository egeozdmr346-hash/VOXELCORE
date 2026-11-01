package com.voxelcore.ui;

import com.voxelcore.bootstrap.Bootstrap;
import com.voxelcore.config.PerformanceProfile;
import com.voxelcore.config.VoxelConfig;
import com.voxelcore.native_bridge.NativeBridge;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

/**
 * Stage 1 settings screen: proves the UI entrypoint works (opens, closes, writes config)
 * ahead of the richer Stage 2 UI (module toggles, live FPS/memory overlays). Deliberately a
 * plain vanilla {@link Screen} rather than Cloth Config or a custom widget framework — no
 * extra dependency needed to answer "can VoxelCore open a working settings screen".
 */
public final class VoxelCoreScreen extends Screen {

    private final Screen parent;
    private VoxelConfig config;

    public VoxelCoreScreen(Screen parent) {
        super(Text.translatable("screen.voxelcore.settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.config = Bootstrap.get().config();

        int centerX = this.width / 2;
        int y = 28;

        this.addDrawableChild(CyclingButtonWidget.<PerformanceProfile>builder(
                        profile -> Text.literal(profile.name()))
                .values(PerformanceProfile.values())
                .initially(config.getProfile())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.profile"),
                        (button, value) -> {
                            config.setProfile(value);
                            NativeBridge.applyProfile(value);
                        }));

        y += 22;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isNativeEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.native_enabled"),
                        (button, value) -> {
                            config.setNativeEnabled(value);
                            NativeBridge.setEnabled(value);
                        }));

        y += 22;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isAdaptiveEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.adaptive"),
                        (button, value) -> {
                            config.setAdaptiveEnabled(value);
                            NativeBridge.setAdaptive(value);
                        }));

        y += 22;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isFpsOverlayEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.fps_overlay"),
                        (button, value) -> config.setFpsOverlayEnabled(value)));

        y += 22;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isMemoryOverlayEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.memory_overlay"),
                        (button, value) -> config.setMemoryOverlayEnabled(value)));

        y += 28;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
                .dimensions(centerX - 100, y, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context); // 1.20.1 Yarn signature: renderBackground(DrawContext)
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFF);

        PerfMonitor perf = HudOverlay.perf();
        String[] lines = {
                "Native: " + NativeBridge.statusText() + " | v" + NativeBridge.nativeVersion()
                        + " (expects " + NativeBridge.EXPECTED_NATIVE_VERSION + ")",
                "CPU: " + NativeBridge.cpuCapabilityText(),
                "Calls: " + NativeBridge.nativeCallCount() + " native / " + NativeBridge.javaCallCount()
                        + " Java fallback / " + NativeBridge.nativeFailureCount() + " native failures",
                String.format("Frame avg %.1f ms, p99 %.1f ms | Tick avg %.2f ms",
                        perf.frameAvgMs(), perf.frameP99Ms(), perf.tickAvgMs()),
                OverlayStats.memoryText(),
                "Engine functions are not called by gameplay code yet (benchmark/API only)."
        };
        int ly = 188;
        for (String line : lines) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line),
                    this.width / 2, ly, 0xAAAAAA);
            ly += 10;
        }
    }

    @Override
    public void close() {
        if (this.config != null) {
            this.config.save();
        }
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
