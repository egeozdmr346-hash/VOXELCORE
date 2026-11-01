package com.voxelcore.ui;

/**
 * Minecraft-independent overlay data: frame-rate counter and JVM heap formatting.
 * Kept separate from rendering so it can be unit-tested without a game.
 */
public final class OverlayStats {

    private static final long WINDOW_NANOS = 500_000_000L;

    private long windowStart = -1;
    private int frames = 0;
    private int fps = 0;

    /** Call once per rendered frame with a monotonic timestamp (System.nanoTime()). */
    public void onFrame(long nowNanos) {
        if (windowStart < 0) {
            windowStart = nowNanos;
        }
        frames++;
        long elapsed = nowNanos - windowStart;
        if (elapsed >= WINDOW_NANOS) {
            fps = (int) Math.round(frames * 1_000_000_000.0 / elapsed);
            frames = 0;
            windowStart = nowNanos;
        }
    }

    public int fps() {
        return fps;
    }

    public String fpsText() {
        return fps + " FPS";
    }

    public static String memoryText(long usedBytes, long maxBytes) {
        return "Mem: " + (usedBytes >> 20) + " / " + (maxBytes >> 20) + " MB";
    }

    public static String memoryText() {
        Runtime rt = Runtime.getRuntime();
        return memoryText(rt.totalMemory() - rt.freeMemory(), rt.maxMemory());
    }
}
