package com.voxelcore.ui;

import java.util.Arrays;

/**
 * Minecraft-independent frame-time and tick-time statistics over a sliding window.
 * Frame/tick recording is O(1) and allocation-free; percentiles are computed only when
 * {@link #refreshIfDue(long)} runs (at most twice per second), never per frame.
 */
public final class PerfMonitor {

    private static final int WINDOW = 240;
    private static final long REFRESH_NANOS = 500_000_000L;

    private final long[] frames = new long[WINDOW];
    private final long[] ticks = new long[WINDOW];
    private final long[] scratch = new long[WINDOW];
    private int frameCount, framePos, tickCount, tickPos;
    private long lastFrame = -1, tickStart = -1, lastRefresh = -1;
    private double frameAvgMs, frameP99Ms, tickAvgMs;

    public void onFrame(long nowNanos) {
        if (lastFrame >= 0) {
            frames[framePos] = nowNanos - lastFrame;
            framePos = (framePos + 1) % WINDOW;
            frameCount = Math.min(WINDOW, frameCount + 1);
        }
        lastFrame = nowNanos;
    }

    public void onTickStart(long nowNanos) {
        tickStart = nowNanos;
    }

    public void onTickEnd(long nowNanos) {
        if (tickStart >= 0) {
            ticks[tickPos] = nowNanos - tickStart;
            tickPos = (tickPos + 1) % WINDOW;
            tickCount = Math.min(WINDOW, tickCount + 1);
            tickStart = -1;
        }
    }

    public void refreshIfDue(long nowNanos) {
        if (lastRefresh >= 0 && nowNanos - lastRefresh < REFRESH_NANOS) {
            return;
        }
        lastRefresh = nowNanos;
        frameAvgMs = average(frames, frameCount);
        frameP99Ms = percentile(frames, frameCount, 0.99);
        tickAvgMs = average(ticks, tickCount);
    }

    public double frameAvgMs() { return frameAvgMs; }

    public double frameP99Ms() { return frameP99Ms; }

    public double tickAvgMs() { return tickAvgMs; }

    static double average(long[] a, int n) {
        if (n == 0) return 0;
        long sum = 0;
        for (int i = 0; i < n; i++) sum += a[i];
        return sum / (double) n / 1_000_000.0;
    }

    private double percentile(long[] a, int n, double p) {
        if (n == 0) return 0;
        System.arraycopy(a, 0, scratch, 0, n);
        Arrays.sort(scratch, 0, n);
        int idx = (int) Math.ceil(p * n) - 1;
        return scratch[Math.max(0, Math.min(n - 1, idx))] / 1_000_000.0;
    }
}
