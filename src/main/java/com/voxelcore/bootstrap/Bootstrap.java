package com.voxelcore.bootstrap;

import com.voxelcore.compat.SodiumCompat;
import com.voxelcore.config.VoxelConfig;
import com.voxelcore.core.EventBus;
import com.voxelcore.core.ModuleRegistry;
import com.voxelcore.native_bridge.NativeBridge;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Path;

/**
 * Owns VoxelCore's startup sequence: environment detection, config load, module
 * registration order, and the one-line summary logged on every launch. Registration order
 * here *is* the dependency order (see ModuleRegistry's javadoc) — Core-adjacent services
 * first, optional/compat-dependent things after.
 *
 * Deliberately a singleton reached via {@link #get()} rather than injected everywhere: at
 * Stage 1 there is exactly one bootstrap per JVM (one Minecraft client instance), and a
 * singleton keeps VoxelCore.java and VoxelCoreClient.java simple. Revisit if VoxelCore ever
 * needs to run in a test harness that spins up multiple instances.
 */
public final class Bootstrap {

    private static final Logger LOGGER = System.getLogger("VoxelCore/Bootstrap");
    private static Bootstrap instance;

    private final ModuleRegistry moduleRegistry = new ModuleRegistry();
    private final EventBus eventBus = new EventBus();
    private VoxelConfig config;
    private SodiumCompat sodiumCompat;

    private Bootstrap() { }

    public static synchronized Bootstrap get() {
        if (instance == null) {
            instance = new Bootstrap();
        }
        return instance;
    }

    /** Called once from {@link com.voxelcore.VoxelCore#onInitialize()}. Common (client+server-ish) setup. */
    public void initializeCommon() {
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("voxelcore");
        this.config = VoxelConfig.loadOrDefault(configDir);
        NativeBridge.setEnabled(config.isNativeEnabled());
        LOGGER.log(Level.INFO, "VoxelCore config loaded from " + configDir);
    }

    /** Called once from {@link com.voxelcore.VoxelCoreClient#onInitializeClient()}. Client-only setup. */
    public void initializeClient() {
        this.sodiumCompat = SodiumCompat.detect();

        logEnvironmentSummary();

        // Stage 1: no concrete VoxelModule implementations registered yet beyond this
        // diagnostic placeholder — Render/Chunk/Memory modules land in later stages per the
        // roadmap. The registry and lifecycle are real and exercised now so later stages
        // only need to register(), not build the plumbing.
        moduleRegistry.initializeAll();
    }

    private void logEnvironmentSummary() {
        boolean nativeActive = NativeBridge.isNativeActive();
        String nativeState = !NativeBridge.isEnabled() ? "disabled-by-config"
                : nativeActive ? "active (v" + NativeBridge.nativeVersion() + ")" : "unavailable (Java fallback)";
        LOGGER.log(Level.INFO, String.format(
                "VoxelCore %s starting | MC 1.20.1 | Java %s | %s | native=%s | profile=%s",
                modVersion(),
                System.getProperty("java.version"),
                sodiumCompat.statusLabel(),
                nativeState,
                config.getProfile()));
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("voxelcore")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }

    public ModuleRegistry moduleRegistry() {
        return moduleRegistry;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public VoxelConfig config() {
        if (config == null) {
            throw new IllegalStateException("Bootstrap.initializeCommon() has not run yet");
        }
        return config;
    }

    public SodiumCompat sodiumCompat() {
        if (sodiumCompat == null) {
            throw new IllegalStateException("Bootstrap.initializeClient() has not run yet");
        }
        return sodiumCompat;
    }

    public void shutdown() {
        moduleRegistry.shutdownAll();
        if (config != null) {
            config.save();
        }
    }
}
