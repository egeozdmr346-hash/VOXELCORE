package com.voxelcore.native_bridge;

/**
 * Public entry point for native-accelerated operations. Every method here has a pure-Java
 * fallback and picks the native path only when {@link NativeLoader#isAvailable()} is true —
 * per the project rule "native modül hatalarında güvenli geri dönüş" (safe fallback on
 * native module failure), the mod must work identically, just slower, with no native lib.
 *
 * Stage 1 ships exactly one real native call — countNonZeroShorts — as the proof that the
 * Java <-> JNI <-> C++ round trip works end to end. It stands in for the kind of bulk
 * chunk-section-array scan the Chunk/Memory modules will hand to native code once real
 * profiling (Stage 4) identifies an actual bottleneck worth crossing the JNI boundary for.
 * The JNI call-cost comment in the project brief is taken seriously: this is deliberately a
 * bulk array operation, not a per-block call, because JNI transition overhead would dwarf the
 * work on anything finer-grained.
 */
public final class NativeBridge {

    /** User setting ("Native Enabled"). When false the native library is never even loaded. */
    private static volatile boolean enabled = true;

    /** Number of JNI calls actually issued; lets tests prove JNI is untouched when disabled. */
    private static final java.util.concurrent.atomic.AtomicLong NATIVE_CALLS =
            new java.util.concurrent.atomic.AtomicLong();

    private NativeBridge() { }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** True if native is enabled in settings AND the library loaded; otherwise Java fallback runs. */
    public static boolean isNativeActive() {
        return enabled && NativeLoader.isAvailable();
    }

    static long nativeCallCount() {
        return NATIVE_CALLS.get();
    }

    /**
     * Counts non-zero entries in a short[] — a stand-in for scanning a chunk section's
     * 16x16x16 block-state palette indices for non-air entries. Dispatches to native code
     * when available, otherwise runs the equivalent Java loop.
     */
    public static long countNonZeroShorts(short[] data) {
        if (isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                return nativeCountNonZeroShorts(data);
            } catch (UnsatisfiedLinkError e) {
                // Library claimed to load but the symbol is missing (mismatched build) —
                // fall back rather than crash the caller.
                return javaCountNonZeroShorts(data);
            }
        }
        return javaCountNonZeroShorts(data);
    }

    static long javaCountNonZeroShorts(short[] data) {
        long count = 0;
        for (short s : data) {
            if (s != 0) {
                count++;
            }
        }
        return count;
    }

    private static native long nativeCountNonZeroShorts(short[] data);

    /** Native library version string, or "unavailable" when running in the Java fallback. */
    public static String nativeVersion() {
        if (isNativeActive()) {
            try {
                NATIVE_CALLS.incrementAndGet();
                return nativeGetVersion();
            } catch (UnsatisfiedLinkError e) {
                return "unavailable";
            }
        }
        return "unavailable";
    }

    private static native String nativeGetVersion();
}
