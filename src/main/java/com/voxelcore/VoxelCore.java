package com.voxelcore;

import com.voxelcore.bootstrap.Bootstrap;
import net.fabricmc.api.ModInitializer;

/**
 * Common entrypoint (declared under "main" in fabric.mod.json). VoxelCore is client-only
 * (environment: "client" in fabric.mod.json) but this still runs before VoxelCoreClient and
 * is where any logic that isn't strictly rendering/UI belongs, so a later integrated-server
 * or dedicated-server-adjacent feature has somewhere to live without restructuring.
 */
public final class VoxelCore implements ModInitializer {

    @Override
    public void onInitialize() {
        Bootstrap.get().initializeCommon();
    }
}
