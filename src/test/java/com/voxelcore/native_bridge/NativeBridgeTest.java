package com.voxelcore.native_bridge;

import java.util.Random;

/**
 * End-to-end test of the Java -> JNI -> C++ round trip: loads the real compiled native
 * library from the classpath resource (src/main/resources/natives/...), calls into it,
 * and cross-checks the result against the pure-Java fallback on random data. Also verifies
 * the fallback path directly, since that's what runs on platforms/arches with no native build.
 */
public final class NativeBridgeTest {

    static int failures = 0;

    public static void main(String[] args) {
        System.out.println("os.name=" + System.getProperty("os.name")
                + " os.arch=" + System.getProperty("os.arch"));

        boolean nativeActive = NativeBridge.isNativeActive();
        System.out.println("Native active: " + nativeActive);
        if (!nativeActive) {
            System.out.println("  [SKIP] native library not bundled for this platform (optional) - testing Java fallback only");
        }

        System.out.println("Native version: " + NativeBridge.nativeVersion());
        if (nativeActive) {
            String expected = System.getProperty("voxelcore.expectedNativeVersion");
            check("native version is non-empty", !NativeBridge.nativeVersion().isEmpty());
            if (expected != null) {
                check("native version equals " + expected, expected.equals(NativeBridge.nativeVersion()));
            }
        }

        Random rnd = new Random(42);
        for (int trial = 0; trial < 20; trial++) {
            int len = rnd.nextInt(5000);
            short[] data = new short[len];
            long expected = 0;
            for (int i = 0; i < len; i++) {
                short v = (short) rnd.nextInt(5); // dense zeros, like a mostly-air chunk section
                data[i] = v;
                if (v != 0) expected++;
            }
            long nativeResult = NativeBridge.countNonZeroShorts(data);
            long javaResult = NativeBridge.javaCountNonZeroShorts(data);
            check("trial " + trial + " result==expected (len=" + len + ")", nativeResult == expected);
            check("trial " + trial + " native==javaFallback (len=" + len + ")", nativeResult == javaResult);
        }

        // Edge cases
        check("empty array -> 0", NativeBridge.countNonZeroShorts(new short[0]) == 0);
        check("all-zero array -> 0", NativeBridge.countNonZeroShorts(new short[1000]) == 0);
        short[] allOnes = new short[500];
        java.util.Arrays.fill(allOnes, (short) 7);
        check("all-nonzero array -> length", NativeBridge.countNonZeroShorts(allOnes) == 500);

        // "Native Enabled" = false must never issue a JNI call and must report inactive.
        NativeBridge.setEnabled(false);
        long callsBefore = NativeBridge.nativeCallCount();
        check("disabled -> isNativeActive false", !NativeBridge.isNativeActive());
        check("disabled -> version unavailable", "unavailable".equals(NativeBridge.nativeVersion()));
        check("disabled -> Java fallback result correct", NativeBridge.countNonZeroShorts(allOnes) == 500);
        check("disabled -> zero JNI calls", NativeBridge.nativeCallCount() == callsBefore);
        NativeBridge.setEnabled(true);
        if (nativeActive) {
            NativeBridge.countNonZeroShorts(allOnes);
            check("re-enabled -> JNI call issued", NativeBridge.nativeCallCount() > callsBefore);
        }

        if (failures == 0) {
            System.out.println("ALL NATIVE BRIDGE TESTS PASSED");
        } else {
            System.out.println(failures + " NATIVE BRIDGE TEST(S) FAILED");
            System.exit(1);
        }
    }

    static void check(String label, boolean condition) {
        if (condition) {
            System.out.println("  [PASS] " + label);
        } else {
            System.out.println("  [FAIL] " + label);
            failures++;
        }
    }
}
