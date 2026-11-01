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
        int y = this.height / 2 - 60;

        this.addDrawableChild(CyclingButtonWidget.<PerformanceProfile>builder(
                        profile -> Text.literal(profile.name()))
                .values(PerformanceProfile.values())
                .initially(config.getProfile())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.profile"),
                        (button, value) -> config.setProfile(value)));

        y += 24;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isFpsOverlayEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.fps_overlay"),
                        (button, value) -> config.setFpsOverlayEnabled(value)));

        y += 24;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isMemoryOverlayEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.memory_overlay"),
                        (button, value) -> config.setMemoryOverlayEnabled(value)));

        y += 24;
        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(config.isNativeEnabled())
                .build(centerX - 100, y, 200, 20,
                        Text.translatable("option.voxelcore.native_enabled"),
                        (button, value) -> {
                            config.setNativeEnabled(value);
                            NativeBridge.setEnabled(value);
                        }));

        y += 32;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), button -> this.close())
                .dimensions(centerX - 100, y, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context); // 1.20.1 Yarn signature: renderBackground(DrawContext)
        super.render(context, mouseX, mouseY, delta);

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);

        String sodiumLine = "Sodium: " + Bootstrap.get().sodiumCompat().statusLabel();
        String nativeLine = "Native: " + (!NativeBridge.isEnabled()
                ? "disabled in settings (Java)"
                : NativeBridge.isNativeActive()
                        ? "active (v" + NativeBridge.nativeVersion() + ")"
                        : "library unavailable (Java fallback)");
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(sodiumLine),
                this.width / 2, this.height - 40, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(nativeLine),
                this.width / 2, this.height - 28, 0xAAAAAA);
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
