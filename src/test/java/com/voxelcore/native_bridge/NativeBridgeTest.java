package com.voxelcore.native_bridge;

import com.voxelcore.config.PerformanceProfile;

import java.util.Arrays;
import java.util.Random;

/** Java vs native equality, edge cases, enabled flag, thresholds, version check. Plain main(). */
public final class NativeBridgeTest {

    static int failures = 0;

    public static void main(String[] args) {
        NativeBridge.applyProfile(PerformanceProfile.EXTREME); // force native for every size when active
        boolean nativeActive = NativeBridge.isNativeActive();
        System.out.println("status: " + NativeBridge.statusText() + " | cpu: " + NativeBridge.cpuCapabilityText());
        if (!nativeActive) {
            System.out.println("  [SKIP] native not active here - native-vs-Java equality is NOT tested in this run");
        } else {
            String expected = System.getProperty("voxelcore.expectedNativeVersion");
            check("native version non-empty", !NativeBridge.nativeVersion().isEmpty());
            if (expected != null) check("native version equals " + expected, expected.equals(NativeBridge.nativeVersion()));
        }

        Random rnd = new Random(42);
        int[] sizes = {0, 1, 7, 8, 9, 15, 16, 17, 100, 4096, 4097, 98304, 300_001};
        for (int len : sizes) {
            short[] d = new short[len];
            for (int i = 0; i < len; i++) d[i] = rnd.nextInt(100) < 40 ? (short) rnd.nextInt() : 0;
            check("countNonZero len=" + len, NativeBridge.countNonZeroShorts(d) == JavaImpl.countNonZero(d));
            int[] a = new int[2], b = new int[2];
            NativeBridge.sectionStats(d, a);
            JavaImpl.sectionStats(d, b);
            check("sectionStats len=" + len, Arrays.equals(a, b));
        }
        short[] edge = {Short.MIN_VALUE, 0, Short.MAX_VALUE, -1};
        check("INT16 extremes", NativeBridge.countNonZeroShorts(edge) == 3);
        int[] st = new int[2];
        NativeBridge.sectionStats(new short[]{0, 5, 5, 7, 0, -1, 7, 7}, st);
        check("sectionStats known values", st[0] == 6 && st[1] == 4);

        // frustum
        float[] planes = {1, 0, 0, 10, -1, 0, 0, 10, 0, 1, 0, 10, 0, -1, 0, 10, 0, 0, 1, 10, 0, 0, -1, 10};
        for (int n : new int[]{0, 1, 5, 1000, 20_000}) {
            float[] boxes = new float[n * 6];
            for (int i = 0; i < n; i++) {
                float x = rnd.nextInt(60) - 30, y = rnd.nextInt(60) - 30, z = rnd.nextInt(60) - 30;
                boxes[i * 6] = x; boxes[i * 6 + 1] = y; boxes[i * 6 + 2] = z;
                boxes[i * 6 + 3] = x + 2; boxes[i * 6 + 4] = y + 2; boxes[i * 6 + 5] = z + 2;
            }
            byte[] o1 = new byte[n], o2 = new byte[n];
            int v1 = NativeBridge.frustumCull(planes, boxes, n, o1);
            int v2 = JavaImpl.frustumCull(planes, boxes, n, o2);
            check("frustum n=" + n, v1 == v2 && Arrays.equals(o1, o2));
        }
        float[] nanBox = {Float.NaN, 0, 0, 1, 1, 1};
        byte[] nanOut = new byte[1];
        NativeBridge.frustumCull(planes, nanBox, 1, nanOut);
        check("NaN box is conservatively visible", nanOut[0] == 1);

        // distance
        for (int n : new int[]{0, 1, 3, 5000}) {
            float[] pts = new float[n * 3];
            for (int i = 0; i < pts.length; i++) pts[i] = rnd.nextInt(1000) - 500 + rnd.nextFloat();
            float[] d1 = new float[n], d2 = new float[n];
            NativeBridge.distanceSquared(pts, n, 1.5f, -2.25f, 3f, d1);
            JavaImpl.distanceSq(pts, n, 1.5f, -2.25f, 3f, d2);
            check("distanceSq n=" + n + " bit-identical", Arrays.equals(d1, d2));
        }

        // invalid input
        check("null data -> NPE", throwsNpe(() -> NativeBridge.countNonZeroShorts(null)));
        check("null out -> NPE", throwsNpe(() -> NativeBridge.sectionStats(new short[4], null)));
        check("short out -> IAE", throwsIae(() -> NativeBridge.sectionStats(new short[4], new int[1])));
        check("short planes -> IAE", throwsIae(() -> NativeBridge.frustumCull(new float[23], new float[6], 1, new byte[1])));
        check("short boxes -> IAE", throwsIae(() -> NativeBridge.frustumCull(planes, new float[5], 1, new byte[1])));
        check("negative count -> IAE", throwsIae(() -> NativeBridge.frustumCull(planes, new float[6], -1, new byte[1])));
        check("short dist out -> IAE", throwsIae(() -> NativeBridge.distanceSquared(new float[6], 2, 0, 0, 0, new float[1])));

        // version check
        check("version match", NativeBridge.versionCompatible(NativeBridge.EXPECTED_NATIVE_VERSION));
        check("version mismatch rejected", !NativeBridge.versionCompatible("0.1.3") && !NativeBridge.versionCompatible(null));

        // disabled => zero JNI calls
        short[] big = new short[10_000];
        Arrays.fill(big, (short) 3);
        NativeBridge.setEnabled(false);
        long before = NativeBridge.nativeCallCount();
        check("disabled -> inactive", !NativeBridge.isNativeActive());
        check("disabled -> result correct", NativeBridge.countNonZeroShorts(big) == 10_000);
        NativeBridge.sectionStats(big, new int[2]);
        NativeBridge.frustumCull(planes, new float[6], 1, new byte[1]);
        NativeBridge.distanceSquared(new float[3], 1, 0, 0, 0, new float[1]);
        check("disabled -> nativeVersion unavailable", "unavailable".equals(NativeBridge.nativeVersion()));
        check("disabled -> cpu text n/a", NativeBridge.cpuCapabilityText().startsWith("n/a"));
        check("disabled -> zero JNI calls", NativeBridge.nativeCallCount() == before);
        NativeBridge.setEnabled(true);

        // thresholds: BALANCED keeps small batches on Java (no JNI)
        NativeBridge.applyProfile(PerformanceProfile.BALANCED);
        before = NativeBridge.nativeCallCount();
        NativeBridge.countNonZeroShorts(new short[100]);
        check("BALANCED small batch -> no JNI call", NativeBridge.nativeCallCount() == before);
        if (nativeActive) {
            NativeBridge.countNonZeroShorts(new short[100_000]);
            check("BALANCED large batch -> JNI call", NativeBridge.nativeCallCount() > before);
            NativeBridge.applyProfile(PerformanceProfile.EXTREME);
            before = NativeBridge.nativeCallCount();
            NativeBridge.countNonZeroShorts(new short[10]);
            check("EXTREME small batch -> JNI call", NativeBridge.nativeCallCount() > before);
            NativeBridge.setAdaptive(false);
            check("adaptive OFF -> scalar", NativeBridge.cpuCapabilityText().endsWith("active: scalar"));
            NativeBridge.setAdaptive(true);
            check("adaptive ON -> not scalar (x86)", !NativeBridge.cpuCapabilityText().endsWith("active: scalar")
                    || !NativeBridge.cpuCapabilityText().contains("SSE2"));
        }

        if (failures == 0) {
            System.out.println("ALL NATIVE BRIDGE TESTS PASSED");
        } else {
            System.out.println(failures + " NATIVE BRIDGE TEST(S) FAILED");
            System.exit(1);
        }
    }

    static boolean throwsNpe(Runnable r) {
        try { r.run(); return false; } catch (NullPointerException e) { return true; }
    }

    static boolean throwsIae(Runnable r) {
        try { r.run(); return false; } catch (IllegalArgumentException e) { return true; }
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
