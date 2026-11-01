package com.voxelcore.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Optional;

/**
 * Detects Sodium at runtime and reports its version. Sodium is a *soft* dependency (see
 * build.gradle: modCompileOnly, never modImplementation).
 *
 * There is NO Sodium integration yet: no mixins, no hooks. This class only detects the mod
 * and checks whether its version is the one VoxelCore targets. "Target version" means the
 * version the project is built against; it is not a claim that runtime testing was done.
 */
public final class SodiumCompat {

    private static final Logger LOGGER = System.getLogger("VoxelCore/SodiumCompat");

    /** Sodium versions VoxelCore is built against (future integration target). */
    private static final String[] VALIDATED_VERSIONS = {
            "0.5.11"
    };

    private final boolean present;
    private final String version; // null if not present
    private final boolean validated;

    private SodiumCompat(boolean present, String version, boolean validated) {
        this.present = present;
        this.version = version;
        this.validated = validated;
    }

    public static SodiumCompat detect() {
        Optional<ModContainer> container = FabricLoader.getInstance().getModContainer("sodium");
        if (container.isEmpty()) {
            LOGGER.log(Level.INFO, "Sodium not detected. VoxelCore has no Sodium integration yet, so nothing changes.");
            return new SodiumCompat(false, null, false);
        }

        String version = container.get().getMetadata().getVersion().getFriendlyString();
        boolean validated = isValidated(version);

        if (validated) {
            LOGGER.log(Level.INFO, "Sodium " + version + " detected (matches target version). "
                    + "Sodium integration is not implemented yet; VoxelCore does not hook into Sodium.");
        } else {
            LOGGER.log(Level.INFO, "Sodium " + version + " detected (target version is "
                    + String.join(", ", VALIDATED_VERSIONS)
                    + "). Sodium integration is not implemented yet; VoxelCore does not hook into Sodium.");
        }

        return new SodiumCompat(true, version, validated);
    }

    private static boolean isValidated(String version) {
        for (String v : VALIDATED_VERSIONS) {
            if (v.equals(version)) {
                return true;
            }
        }
        return false;
    }

    public boolean isPresent() {
        return present;
    }

    public boolean isValidated() {
        return validated;
    }

    public Optional<String> getVersion() {
        return Optional.ofNullable(version);
    }

    /** Short status string for the settings screen. */
    public String statusLabel() {
        return statusLabelFor(present, version, validated);
    }

    static String statusLabelFor(boolean present, String version, boolean targetVersion) {
        if (!present) {
            return "Not installed";
        }
        return "Sodium " + version + (targetVersion ? " detected (target version)" : " detected (non-target version)")
                + ", no integration yet";
    }

    static boolean isTargetVersion(String version) {
        return isValidated(version);
    }
}
