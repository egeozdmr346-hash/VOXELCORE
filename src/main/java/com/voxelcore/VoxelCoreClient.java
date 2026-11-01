package com.voxelcore;

import com.voxelcore.bootstrap.Bootstrap;
import com.voxelcore.ui.HudOverlay;
import com.voxelcore.ui.VoxelCoreScreen;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class VoxelCoreClient implements ClientModInitializer {

    private static KeyBinding openSettingsKey;

    @Override
    public void onInitializeClient() {
        Bootstrap.get().initializeClient();

        openSettingsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.voxelcore.open_settings",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_V, // Shift+V by convention would need a chord check below
                "category.voxelcore.general"
        ));

        ClientTickEvents.START_CLIENT_TICK.register(c -> HudOverlay.perf().onTickStart(System.nanoTime()));
        ClientTickEvents.END_CLIENT_TICK.register(VoxelCoreClient::onClientTick);
        ClientTickEvents.END_CLIENT_TICK.register(c -> HudOverlay.perf().onTickEnd(System.nanoTime()));
        HudOverlay.register();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> Bootstrap.get().shutdown(), "VoxelCore-Shutdown"));
    }

    private static void onClientTick(MinecraftClient client) {
        boolean shiftHeld = InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(client.getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT);

        while (openSettingsKey.wasPressed()) {
            if (shiftHeld && client.currentScreen == null) {
                client.setScreen(new VoxelCoreScreen(null));
            }
        }
    }
}
