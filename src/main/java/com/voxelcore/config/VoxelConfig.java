package com.voxelcore.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Stage-1 config store. Deliberately backed by {@link Properties} (no external JSON
 * dependency) so VoxelCore's core module has zero third-party jars. If a richer schema is
 * needed later (nested objects, arrays), Fabric Loader already bundles Gson transitively,
 * and this class can be swapped out behind the same public getters without touching callers.
 *
 * Not thread-safe by design: all writes are expected from the client thread (UI screen),
 * reads from anywhere are safe because {@link Properties} itself is a synchronized Hashtable.
 */
public final class VoxelConfig {

    private static final Logger LOGGER = System.getLogger("VoxelCore/Config");

    private static final String KEY_PROFILE = "performance.profile";
    private static final String KEY_RENDER_DISTANCE_OVERRIDE = "render.distanceOverride";
    private static final String KEY_SHOW_FPS_OVERLAY = "ui.showFpsOverlay";
    private static final String KEY_SHOW_MEMORY_OVERLAY = "ui.showMemoryOverlay";
    private static final String KEY_NATIVE_ENABLED = "native.enabled";
    private static final String KEY_NATIVE_ADAPTIVE = "native.adaptive";

    private final Path path;
    private final Properties props = new Properties();

    public VoxelConfig(Path path) {
        this.path = path;
    }

    public static VoxelConfig loadOrDefault(Path configDir) {
        Path file = configDir.resolve("voxelcore.properties");
        VoxelConfig config = new VoxelConfig(file);
        config.load();
        return config;
    }

    public void load() {
        if (!Files.exists(path)) {
            applyDefaults();
            save();
            return;
        }
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            LOGGER.log(Level.INFO, "Loaded config from " + path);
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to read " + path + " — falling back to defaults", e);
            applyDefaults();
        }
    }

    public void save() {
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "VoxelCore configuration — generated, safe to hand-edit");
            }
        } catch (IOException e) {
            LOGGER.log(Level.ERROR, "Failed to write " + path, e);
        }
    }

    private void applyDefaults() {
        props.setProperty(KEY_PROFILE, PerformanceProfile.BALANCED.name());
        props.setProperty(KEY_RENDER_DISTANCE_OVERRIDE, "-1"); // -1 = use vanilla setting
        props.setProperty(KEY_SHOW_FPS_OVERLAY, "true");
        props.setProperty(KEY_SHOW_MEMORY_OVERLAY, "false");
        props.setProperty(KEY_NATIVE_ENABLED, "true");
        props.setProperty(KEY_NATIVE_ADAPTIVE, "true");
    }

    public PerformanceProfile getProfile() {
        try {
            return PerformanceProfile.valueOf(props.getProperty(KEY_PROFILE, "BALANCED"));
        } catch (IllegalArgumentException e) {
            return PerformanceProfile.BALANCED;
        }
    }

    public void setProfile(PerformanceProfile profile) {
        props.setProperty(KEY_PROFILE, profile.name());
    }

    public int getRenderDistanceOverride() {
        return parseIntOr(props.getProperty(KEY_RENDER_DISTANCE_OVERRIDE), -1);
    }

    public void setRenderDistanceOverride(int chunks) {
        props.setProperty(KEY_RENDER_DISTANCE_OVERRIDE, Integer.toString(chunks));
    }

    public boolean isFpsOverlayEnabled() {
        return parseBoolOr(props.getProperty(KEY_SHOW_FPS_OVERLAY), true);
    }

    public void setFpsOverlayEnabled(boolean enabled) {
        props.setProperty(KEY_SHOW_FPS_OVERLAY, Boolean.toString(enabled));
    }

    public boolean isMemoryOverlayEnabled() {
        return parseBoolOr(props.getProperty(KEY_SHOW_MEMORY_OVERLAY), false);
    }

    public void setMemoryOverlayEnabled(boolean enabled) {
        props.setProperty(KEY_SHOW_MEMORY_OVERLAY, Boolean.toString(enabled));
    }

    public boolean isNativeEnabled() {
        return parseBoolOr(props.getProperty(KEY_NATIVE_ENABLED), true);
    }

    public void setNativeEnabled(boolean enabled) {
        props.setProperty(KEY_NATIVE_ENABLED, Boolean.toString(enabled));
    }

    public boolean isAdaptiveEnabled() {
        return parseBoolOr(props.getProperty(KEY_NATIVE_ADAPTIVE), true);
    }

    public void setAdaptiveEnabled(boolean enabled) {
        props.setProperty(KEY_NATIVE_ADAPTIVE, Boolean.toString(enabled));
    }

    private static int parseIntOr(String value, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean parseBoolOr(String value, boolean fallback) {
        if (value == null) return fallback;
        return Boolean.parseBoolean(value.trim());
    }
}
