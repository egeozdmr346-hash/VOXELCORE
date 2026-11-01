package com.voxelcore.core;

/**
 * Contract every VoxelCore subsystem implements. Modules are independent:
 * one module's failure to initialize must never prevent another from running.
 */
public interface VoxelModule {

    /** Unique, stable identifier used for registry lookup and logging. Never changes across versions. */
    String id();

    /** Human-readable name shown in the UI module list. */
    String displayName();

    /**
     * Called once, in registration order, during {@link ModuleRegistry#initializeAll()}.
     * Must not throw for control flow — use the return value or log + return instead,
     * so one broken module cannot abort the whole bootstrap sequence.
     */
    void onInitialize();

    /** Called once during client shutdown or mod unload (dev/hot-reload). Must be idempotent. */
    void onShutdown();

    /**
     * Whether this module is currently enabled. Checked by {@link ModuleRegistry} before
     * dispatching lifecycle calls that modules may want to skip when toggled off from the UI.
     */
    default boolean isEnabled() {
        return true;
    }
}
