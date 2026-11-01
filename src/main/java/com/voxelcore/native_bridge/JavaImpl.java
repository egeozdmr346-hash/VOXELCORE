package com.voxelcore.native_bridge;

import java.util.Arrays;

/**
 * Pure-Java reference implementation of every native engine function. Used as the fallback
 * and as the oracle in tests/benchmarks. Arguments are assumed validated (see NativeBridge).
 * Float expression order is identical to the C++ code so results are bit-identical.
 */
public final class JavaImpl {

    private static final ThreadLocal<long[]> BITS = ThreadLocal.withInitial(() -> new long[1024]);

    private JavaImpl() { }

    public static long countNonZero(short[] d) {
        long count = 0;
        for (short s : d) {
            if (s != 0) {
                count++;
            }
        }
        return count;
    }

    /** out[0] = non-zero count, out[1] = distinct value count (including 0 if present). */
    public static void sectionStats(short[] d, int[] out) {
        long[] bits = BITS.get();
        Arrays.fill(bits, 0L);
        int nonZero = 0;
        for (short s : d) {
            if (s != 0) {
                nonZero++;
            }
            int v = s & 0xFFFF;
            bits[v >>> 6] |= 1L << (v & 63);
        }
        int distinct = 0;
        for (long w : bits) {
            distinct += Long.bitCount(w);
        }
        out[0] = nonZero;
        out[1] = distinct;
    }

    public static int frustumCull(float[] planes, float[] boxes, int count, byte[] out) {
        int visible = 0;
        for (int i = 0; i < count; i++) {
            int b = i * 6;
            boolean vis = true;
            for (int p = 0; p < 6; p++) {
                int pl = p * 4;
                float x = planes[pl] >= 0.0f ? boxes[b + 3] : boxes[b];
                float y = planes[pl + 1] >= 0.0f ? boxes[b + 4] : boxes[b + 1];
                float z = planes[pl + 2] >= 0.0f ? boxes[b + 5] : boxes[b + 2];
                float d = planes[pl] * x + planes[pl + 1] * y + planes[pl + 2] * z + planes[pl + 3];
                if (d < 0.0f) {
                    vis = false;
                    break;
                }
            }
            out[i] = (byte) (vis ? 1 : 0);
            if (vis) {
                visible++;
            }
        }
        return visible;
    }

    public static void distanceSq(float[] pts, int count, float cx, float cy, float cz, float[] out) {
        for (int i = 0; i < count; i++) {
            float dx = pts[i * 3] - cx;
            float dy = pts[i * 3 + 1] - cy;
            float dz = pts[i * 3 + 2] - cz;
            out[i] = dx * dx + dy * dy + dz * dz;
        }
    }
}
