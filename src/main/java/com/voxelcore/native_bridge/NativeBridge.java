package com.voxelcore.native_bridge;

import com.voxelcore.config.PerformanceProfile;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Public entry point for native-accelerated batch operations. Every method has a pure-Java
 * equivalent ({@link JavaImpl}) with identical results, and picks native only when
 * (a) "Native Enabled" is on, (b) the library loaded and reports the expected version, and
 * (c) the batch is large enough to pay for the JNI transition (profile-dependent threshold).
 * Any native failure (-1 return, missing symbol) falls back to Java for that call.
 */
public final class NativeBridge {

    private static final Logger LOGGER = System.getLogger("VoxelCore/NativeBridge");

    /** Native library version this Java code was written against. Must equal nativeGetVersion(). */
    public static final String EXPECTED_NATIVE_VERSION = "0.2.0";

    // Thresholds (elements per call) above which native is used. countNonZero: the C++ AVX2/SSE2 kernel
    // measured ~19x faster than the JIT-compiled Java loop in isolation (see README), so it is routed
    // to native from 4096 shorts up (the JNI cost itself is only measurable in CI/on Windows).
    // frustumCull/distanceSq: the C++ kernels measured within ~1.2x of Java, so native is NOT
    // used for them unless the profile is EXTREME.
    private static final int BASE_COUNT = 4096;   // shorts
    private static final int NEVER = Integer.MAX_VALUE;

    private static volatile boolean enabled = true;
    private static volatile boolean adaptive = true;
    private static volatile int thrCount = BASE_COUNT;
    private static volatile int thrCull = NEVER;
    private static volatile int thrDist = NEVER;
    private static volatile int compat = 0; // 0 = unchecked, 1 = ok, -1 = unusable
    private static volatile String compatDetail = "not checked";

    private static final AtomicLong NATIVE_CALLS = new AtomicLong();
    private static final AtomicLong JAVA_CALLS = new AtomicLong();
    private static final AtomicLong NATIVE_FAILURES = new AtomicLong();

    private NativeBridge() { }

    // ---------------------------------------------------------------- settings

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Adaptive ON = native picks the best SIMD level; OFF = native runs scalar code. */
    public static void setAdaptive(boolean value) {
        adaptive = value;
        if (compat == 1) {
            try {
                nativeSetSimdMode(value ? -1 : 0);
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
    }

    public static boolean isAdaptive() {
        return adaptive;
    }

    public static void applyProfile(PerformanceProfile profile) {
        switch (profile) {
            case EXTREME -> { thrCount = 0; thrCull = 0; thrDist = 0; }
            case PERFORMANCE -> { thrCount = BASE_COUNT / 4; thrCull = NEVER; thrDist = NEVER; }
            default -> { thrCount = BASE_COUNT; thrCull = NEVER; thrDist = NEVER; }
        }
    }

    // ---------------------------------------------------------------- state

    static boolean versionCompatible(String nativeVersion) {
        return EXPECTED_NATIVE_VERSION.equals(nativeVersion);
    }

    private static void markUnusable(String why) {
        compat = -1;
        compatDetail = why;
        LOGGER.log(Level.WARNING, "Native engine disabled: " + why + " (using Java implementations)");
    }

    private static boolean ready() {
        int c = compat;
        if (c != 0) {
            return c == 1;
        }
        synchronized (NativeBridge.class) {
            if (compat != 0) {
                return compat == 1;
            }
            if (!NativeLoader.isAvailable()) {
                compat = -1;
                compatDetail = "library not loaded";
                return false;
            }
            try {
                NATIVE_CALLS.incrementAndGet();
                String v = nativeGetVersion();
                if (!versionCompatible(v)) {
                    markUnusable("version mismatch: native=" + v + ", expected=" + EXPECTED_NATIVE_VERSION);
                    return false;
                }
                nativeSetSimdMode(adaptive ? -1 : 0);
                compatDetail = "ok";
                compat = 1;
                return true;
            } catch (Throwable t) {
                markUnusable("native check failed: " + t);
                return false;
            }
        }
    }

    /** True if native is enabled in settings AND loaded AND version-compatible. */
    public static boolean isNativeActive() {
        return enabled && ready();
    }

    public static boolean isLibraryUsable() {
        return ready();
    }

    public static String statusText() {
        if (!enabled) {
            return "disabled in settings (Java)";
        }
        if (ready()) {
            return "active";
        }
        return "unavailable: " + compatDetail + " (Java fallback)";
    }

    public static String nativeVersion() {
        if (isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                return nativeGetVersion();
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
        return "unavailable";
    }

    /** e.g. "SSE2 SSE4.1 AVX AVX2 | active: AVX2", or why native info is unavailable. */
    public static String cpuCapabilityText() {
        if (!isNativeActive()) {
            return "n/a (native not active)";
        }
        try {
            NATIVE_CALLS.incrementAndGet();
            int f = nativeCpuFeatures();
            int lvl = nativeActiveSimd();
            StringBuilder sb = new StringBuilder();
            if ((f & 1) != 0) sb.append("SSE2 ");
            if ((f & 2) != 0) sb.append("SSE4.1 ");
            if ((f & 4) != 0) sb.append("AVX ");
            if ((f & 8) != 0) sb.append("AVX2 ");
            if (sb.length() == 0) sb.append("no x86 SIMD ");
            return sb + "| active: " + (lvl == 2 ? "AVX2" : lvl == 1 ? "SSE2" : "scalar");
        } catch (LinkageError e) {
            markUnusable("native symbol missing: " + e.getMessage());
            return "n/a";
        }
    }

    public static long nativeCallCount() {
        return NATIVE_CALLS.get();
    }

    public static long javaCallCount() {
        return JAVA_CALLS.get();
    }

    public static long nativeFailureCount() {
        return NATIVE_FAILURES.get();
    }

    /** Cheap trivial JNI call; the benchmark uses it to measure pure transition cost. */
    public static int jniProbe() {
        return nativeActiveSimd();
    }

    // ---------------------------------------------------------------- operations

    public static long countNonZeroShorts(short[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length >= thrCount && isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                long r = nativeCountNonZeroShorts(data);
                if (r >= 0) {
                    return r;
                }
                NATIVE_FAILURES.incrementAndGet();
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
        JAVA_CALLS.incrementAndGet();
        return JavaImpl.countNonZero(data);
    }

    static long javaCountNonZeroShorts(short[] data) {
        return JavaImpl.countNonZero(data);
    }

    /** out[0] = non-zero count, out[1] = distinct values. Caller supplies out (no allocation). */
    public static void sectionStats(short[] data, int[] out) {
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(out, "out");
        if (out.length < 2) {
            throw new IllegalArgumentException("out must have length >= 2");
        }
        if (data.length >= thrCount && isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                if (nativeSectionStats(data, out) == 0) {
                    return;
                }
                NATIVE_FAILURES.incrementAndGet();
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
        JAVA_CALLS.incrementAndGet();
        JavaImpl.sectionStats(data, out);
    }

    /**
     * Classifies axis-aligned boxes against 6 frustum planes (see cull.hpp for layout).
     * out[i] = 1 visible/intersecting, 0 outside. Returns the visible count.
     */
    public static int frustumCull(float[] planes, float[] boxes, int count, byte[] out) {
        Objects.requireNonNull(planes, "planes");
        Objects.requireNonNull(boxes, "boxes");
        Objects.requireNonNull(out, "out");
        if (count < 0 || planes.length < 24 || (long) boxes.length < (long) count * 6 || out.length < count) {
            throw new IllegalArgumentException("invalid array sizes for frustumCull");
        }
        if (count >= thrCull && isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                int r = nativeFrustumCull(planes, boxes, count, out);
                if (r >= 0) {
                    return r;
                }
                NATIVE_FAILURES.incrementAndGet();
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
        JAVA_CALLS.incrementAndGet();
        return JavaImpl.frustumCull(planes, boxes, count, out);
    }

    public static void distanceSquared(float[] pts, int count, float cx, float cy, float cz, float[] out) {
        Objects.requireNonNull(pts, "pts");
        Objects.requireNonNull(out, "out");
        if (count < 0 || (long) pts.length < (long) count * 3 || out.length < count) {
            throw new IllegalArgumentException("invalid array sizes for distanceSquared");
        }
        if (count >= thrDist && isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                if (nativeDistanceSq(pts, count, cx, cy, cz, out) == 0) {
                    return;
                }
                NATIVE_FAILURES.incrementAndGet();
            } catch (LinkageError e) {
                markUnusable("native symbol missing: " + e.getMessage());
            }
        }
        JAVA_CALLS.incrementAndGet();
        JavaImpl.distanceSq(pts, count, cx, cy, cz, out);
    }

    // ---------------------------------------------------------------- JNI declarations
    // Names/signatures must match native/src/voxelcore_native.cpp (checked against the
    // javac-generated header at build time and against DLL exports in CI).

    private static native long nativeCountNonZeroShorts(short[] data);

    private static native int nativeSectionStats(short[] data, int[] out);

    private static native int nativeFrustumCull(float[] planes, float[] boxes, int count, byte[] out);

    private static native int nativeDistanceSq(float[] pts, int count, float cx, float cy, float cz, float[] out);

    private static native int nativeCpuFeatures();

    private static native int nativeActiveSimd();

    private static native int nativeSetSimdMode(int mode);

    private static native String nativeGetVersion();
}
