package com.voxelcore.ui;

public final class OverlayStatsTest {
    static int failures = 0;

    public static void main(String[] args) {
        OverlayStats s = new OverlayStats();
        long t = 1_000_000_000L;
        // 60 FPS for 2 seconds: one frame every 1/60 s
        for (int i = 0; i < 120; i++) {
            s.onFrame(t);
            t += 1_000_000_000L / 60;
        }
        check("fps ~60 (got " + s.fps() + ")", Math.abs(s.fps() - 60) <= 2);
        check("fps text", s.fpsText().endsWith(" FPS"));
        check("memory text", OverlayStats.memoryText(512L << 20, 2048L << 20).equals("Mem: 512 / 2048 MB"));
        check("live memory text", OverlayStats.memoryText().startsWith("Mem: "));
        if (failures > 0) System.exit(1);
        System.out.println("ALL OVERLAY TESTS PASSED");
    }

    static void check(String l, boolean c) {
        System.out.println((c ? "  [PASS] " : "  [FAIL] ") + l);
        if (!c) failures++;
    }
}
