package com.voxelcore.ui;

public final class PerfMonitorTest {
    static int failures = 0;

    public static void main(String[] args) {
        PerfMonitor m = new PerfMonitor();
        long t = 0;
        for (int i = 0; i < 200; i++) {
            m.onFrame(t);
            t += (i % 20 == 19) ? 50_000_000L : 10_000_000L; // 10 ms frames, 5% at 50 ms
        }
        m.refreshIfDue(t);
        check("avg frame ~12 ms (got " + m.frameAvgMs() + ")", m.frameAvgMs() > 11.0 && m.frameAvgMs() < 13.5);
        check("p99 captures the slow frames (got " + m.frameP99Ms() + ")", m.frameP99Ms() >= 49.9);
        m.onTickStart(0);
        m.onTickEnd(2_000_000L);
        m.refreshIfDue(t + 1_000_000_000L);
        check("tick avg 2 ms (got " + m.tickAvgMs() + ")", Math.abs(m.tickAvgMs() - 2.0) < 0.01);
        check("empty monitor is zero", new PerfMonitor().frameAvgMs() == 0);
        if (failures > 0) System.exit(1);
        System.out.println("ALL PERF MONITOR TESTS PASSED");
    }

    static void check(String l, boolean c) {
        System.out.println((c ? "  [PASS] " : "  [FAIL] ") + l);
        if (!c) failures++;
    }
}
