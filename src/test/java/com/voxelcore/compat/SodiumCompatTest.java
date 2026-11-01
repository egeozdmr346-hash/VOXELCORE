package com.voxelcore.compat;

public final class SodiumCompatTest {
    static int failures = 0;

    public static void main(String[] args) {
        check("0.5.11 is target", SodiumCompat.isTargetVersion("0.5.11"));
        check("0.5.10 is not target", !SodiumCompat.isTargetVersion("0.5.10"));
        String[] labels = {
                SodiumCompat.statusLabelFor(false, null, false),
                SodiumCompat.statusLabelFor(true, "0.5.11", true),
                SodiumCompat.statusLabelFor(true, "0.5.9", false)};
        for (String l : labels) {
            System.out.println("  label: " + l);
            check("no misleading wording: " + l, !l.toLowerCase().contains("hooks enabled")
                    && !l.toLowerCase().contains("validated") && !l.toLowerCase().contains("extension"));
        }
        check("states no integration", labels[1].contains("no integration yet"));
        if (failures > 0) System.exit(1);
        System.out.println("ALL SODIUM COMPAT TESTS PASSED");
    }

    static void check(String l, boolean c) {
        System.out.println((c ? "  [PASS] " : "  [FAIL] ") + l);
        if (!c) failures++;
    }
}
