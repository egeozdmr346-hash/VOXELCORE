package com.voxelcore.native_bridge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Loads the VoxelCore native library (voxelcore_native.{dll,so,dylib}) from the mod jar's
 * resources into a temp file, then System.load()'s it. Must never throw past this class:
 * callers (NativeBridge) check {@link #isAvailable()} and fall back to pure-Java
 * implementations when native loading fails for any reason. A missing or broken native
 * library must degrade the mod, never crash it.
 */
public final class NativeLoader {

    private static final Logger LOGGER = System.getLogger("VoxelCore/NativeLoader");

    private static volatile boolean attempted = false;
    private static volatile boolean available = false;

    private NativeLoader() { }

    public static synchronized boolean isAvailable() {
        if (!attempted) {
            attempted = true;
            available = tryLoad();
        }
        return available;
    }

    private static boolean tryLoad() {
        String resourceName = resourceNameForPlatform();
        if (resourceName == null) {
            LOGGER.log(Level.WARNING, "Unsupported platform for VoxelCore native library ("
                    + System.getProperty("os.name") + "/" + System.getProperty("os.arch")
                    + ") — falling back to pure-Java implementations.");
            return false;
        }

        String resourcePath = "/natives/" + resourceName;
        try (InputStream in = NativeLoader.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                LOGGER.log(Level.WARNING, "Native library resource " + resourcePath
                        + " not bundled in this build — falling back to pure-Java implementations.");
                return false;
            }

            Path tempDir = Files.createTempDirectory("voxelcore-native");
            Path tempFile = tempDir.resolve(resourceName);
            try (OutputStream out = Files.newOutputStream(tempFile)) {
                in.transferTo(out);
            }
            tempFile.toFile().deleteOnExit();
            tempDir.toFile().deleteOnExit();

            System.load(tempFile.toAbsolutePath().toString());
            LOGGER.log(Level.INFO, "Loaded native library: " + resourceName);
            return true;
        } catch (IOException | LinkageError | SecurityException e) {
            LOGGER.log(Level.WARNING,
                    "Failed to load VoxelCore native library — falling back to pure-Java implementations.", e);
            return false;
        }
    }

    private static String resourceNameForPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean is64 = arch.equals("amd64") || arch.equals("x86_64");

        if (os.contains("win")) {
            return is64 ? "voxelcore_native_windows_x64.dll" : null;
        } else if (os.contains("mac")) {
            return "voxelcore_native_macos.dylib"; // single name for x64/arm64; wrong-arch load fails safely
        } else if (os.contains("nux")) {
            return is64 ? "voxelcore_native_linux_x64.so" : null;
        }
        return null;
    }
}
