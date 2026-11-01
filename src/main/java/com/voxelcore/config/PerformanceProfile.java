package com.voxelcore.config;

/**
 * Profiles control ONE thing in 0.2.0: how eagerly the Java side routes batch operations to
 * the native engine (see NativeBridge.applyProfile). BALANCED: countNonZero native from 4096
 * shorts; PERFORMANCE: from 1024; EXTREME: every operation, every size (when native is active).
 * frustumCull/distanceSq use native only in EXTREME (measured ~no gain over Java).
 */
public enum PerformanceProfile {
    BALANCED,
    PERFORMANCE,
    EXTREME
}
