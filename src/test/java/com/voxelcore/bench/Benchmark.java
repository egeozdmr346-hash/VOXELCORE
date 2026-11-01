package com.voxelcore.bench;

import com.voxelcore.config.PerformanceProfile;
import com.voxelcore.native_bridge.JavaImpl;
import com.voxelcore.native_bridge.NativeBridge;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

/**
 * Java-side benchmark: Java reference vs native (end-to-end incl. JNI transition) vs the
 * NativeBridge API with native OFF. Also verifies Java and native results are identical.
 * Usage: Benchmark [outputFile]. Exit code 1 if results differ. Numbers are per-op medians of
 * batched samples (a sample runs K calls so nanoTime overhead does not dominate small ops).
 */
public final class Benchmark {

    interface Op { long run(); }

    static volatile long sink;
    static final StringBuilder REPORT = new StringBuilder();

    public static void main(String[] args) throws Exception {
        Random rnd = new Random(99);
        boolean nativeUp = NativeBridge.isNativeActive();
        say("# VoxelCore " + NativeBridge.EXPECTED_NATIVE_VERSION + " Java benchmark");
        say("java=" + System.getProperty("java.version") + " os=" + System.getProperty("os.name")
                + " arch=" + System.getProperty("os.arch") + " cpus=" + Runtime.getRuntime().availableProcessors());
        say("native status: " + NativeBridge.statusText() + " | cpu: " + NativeBridge.cpuCapabilityText());
        if (!nativeUp) {
            say("NOTE: native engine not active - only Java rows are measured; no native comparison possible.");
        }

        // ---- data sets
        short[] section = new short[4096];
        short[] column = new short[98304];
        fillShorts(section, rnd);
        fillShorts(column, rnd);
        int boxN = 10_000;
        float[] planes = {1, 0, 0, 100, -1, 0, 0, 100, 0, 1, 0, 100, 0, -1, 0, 100, 0, 0, 1, 100, 0, 0, -1, 100};
        float[] boxes = new float[boxN * 6];
        for (int i = 0; i < boxN; i++) {
            float x = rnd.nextInt(400) - 200, y = rnd.nextInt(400) - 200, z = rnd.nextInt(400) - 200;
            boxes[i * 6] = x; boxes[i * 6 + 1] = y; boxes[i * 6 + 2] = z;
            boxes[i * 6 + 3] = x + 1; boxes[i * 6 + 4] = y + 1; boxes[i * 6 + 5] = z + 1;
        }
        byte[] cullOutJ = new byte[boxN], cullOutN = new byte[boxN];
        int ptN = 100_000;
        float[] pts = new float[ptN * 3];
        for (int i = 0; i < pts.length; i++) pts[i] = rnd.nextInt(1000);
        float[] distJ = new float[ptN], distN = new float[ptN];
        int[] statsJ = new int[2], statsN = new int[2];

        // ---- correctness (Java vs native-forced)
        NativeBridge.applyProfile(PerformanceProfile.EXTREME);
        NativeBridge.setEnabled(true);
        boolean ok = true;
        ok &= JavaImpl.countNonZero(column) == NativeBridge.countNonZeroShorts(column);
        JavaImpl.sectionStats(section, statsJ);
        NativeBridge.sectionStats(section, statsN);
        ok &= Arrays.equals(statsJ, statsN);
        int vj = JavaImpl.frustumCull(planes, boxes, boxN, cullOutJ);
        int vn = NativeBridge.frustumCull(planes, boxes, boxN, cullOutN);
        ok &= vj == vn && Arrays.equals(cullOutJ, cullOutN);
        JavaImpl.distanceSq(pts, ptN, 1, 2, 3, distJ);
        NativeBridge.distanceSquared(pts, ptN, 1, 2, 3, distN);
        ok &= Arrays.equals(distJ, distN);
        say("result equality Java vs " + (nativeUp ? "native" : "bridge(Java path)") + ": " + (ok ? "IDENTICAL" : "MISMATCH"));
        if (!ok) {
            flush(args);
            System.exit(1);
        }

        say("");
        say("| operation | implementation | median ns/op | p95 ns/op | p99 ns/op | ops/s (median) | alloc B/op |");
        say("|---|---|---:|---:|---:|---:|---:|");

        if (nativeUp) {
            Result probe = measure(() -> NativeBridge.jniProbe(), 1);
            say(String.format("| JNI transition (trivial call) | native | %.0f | %.0f | %.0f | %.0f | %.1f |",
                    probe.median, probe.p95, probe.p99, 1e9 / probe.median, probe.allocPerOp));
        }

        row("countNonZero 4096 shorts (1 section)", 4096,
                () -> JavaImpl.countNonZero(section), () -> NativeBridge.countNonZeroShorts(section), nativeUp);
        row("countNonZero 98304 shorts (24 sections)", 98304,
                () -> JavaImpl.countNonZero(column), () -> NativeBridge.countNonZeroShorts(column), nativeUp);
        row("sectionStats 4096 shorts", 4096,
                () -> { JavaImpl.sectionStats(section, statsJ); return statsJ[0]; },
                () -> { NativeBridge.sectionStats(section, statsN); return statsN[0]; }, nativeUp);
        row("frustumCull 10000 boxes", boxN,
                () -> JavaImpl.frustumCull(planes, boxes, boxN, cullOutJ),
                () -> NativeBridge.frustumCull(planes, boxes, boxN, cullOutN), nativeUp);
        row("distanceSq 100000 points", ptN,
                () -> { JavaImpl.distanceSq(pts, ptN, 1, 2, 3, distJ); return (long) distJ[0]; },
                () -> { NativeBridge.distanceSquared(pts, ptN, 1, 2, 3, distN); return (long) distN[0]; }, nativeUp);

        say("");
        say("Notes: 'Java' = JavaImpl called directly. 'native (EXTREME)' = NativeBridge with native forced on for every");
        say("size, i.e. includes argument validation + JNI transition + C++ kernel. 'bridge, native OFF' = same API with");
        say("native disabled (Java path plus validation). No Minecraft FPS conclusions can be drawn from these numbers.");
        flush(args);
    }

    static void row(String name, int elems, Op java, Op nativeOp, boolean nativeUp) {
        int k = Math.max(1, 400_000 / Math.max(1, elems));
        Result j = measure(java, k);
        say(fmt(name, "Java (JavaImpl)", j));
        NativeBridge.setEnabled(false);
        Result off = measure(nativeOp, k);
        say(fmt(name, "bridge, native OFF", off));
        if (nativeUp) {
            NativeBridge.setEnabled(true);
            NativeBridge.applyProfile(PerformanceProfile.EXTREME);
            Result n = measure(nativeOp, k);
            say(fmt(name, "native (EXTREME)", n));
            say(String.format("| %s | speedup native vs Java | %.2fx | | | | |", name, j.median / n.median));
        }
        NativeBridge.setEnabled(true);
    }

    static String fmt(String op, String impl, Result r) {
        return String.format("| %s | %s | %.0f | %.0f | %.0f | %.0f | %.1f |",
                op, impl, r.median, r.p95, r.p99, 1e9 / r.median, r.allocPerOp);
    }

    record Result(double median, double p95, double p99, double allocPerOp) { }

    static Result measure(Op op, int k) {
        for (int i = 0; i < 300; i++) {            // warm-up (JIT)
            for (int j = 0; j < k; j++) sink += op.run();
        }
        com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        int samples = 200;
        double[] t = new double[samples];
        long a0 = mx.getThreadAllocatedBytes(tid);
        for (int s = 0; s < samples; s++) {
            long st = System.nanoTime();
            for (int j = 0; j < k; j++) sink += op.run();
            t[s] = (System.nanoTime() - st) / (double) k;
        }
        long a1 = mx.getThreadAllocatedBytes(tid);
        Arrays.sort(t);
        return new Result(t[samples / 2], t[(int) (samples * 0.95) - 1], t[(int) (samples * 0.99) - 1],
                (a1 - a0) / (double) ((long) samples * k));
    }

    static void fillShorts(short[] a, Random rnd) {
        for (int i = 0; i < a.length; i++) a[i] = rnd.nextInt(100) < 30 ? (short) (rnd.nextInt(500) + 1) : 0;
    }

    static void say(String s) {
        System.out.println(s);
        REPORT.append(s).append('\n');
    }

    static void flush(String[] args) throws Exception {
        if (args.length > 0) {
            Path p = Path.of(args[0]);
            if (p.getParent() != null) Files.createDirectories(p.getParent());
            Files.writeString(p, REPORT.toString());
        }
    }
}
