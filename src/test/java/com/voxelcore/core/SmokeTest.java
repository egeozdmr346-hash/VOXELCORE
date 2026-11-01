package com.voxelcore.core;

import com.voxelcore.config.PerformanceProfile;
import com.voxelcore.config.VoxelConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Standalone smoke test for the Minecraft-independent core (ModuleRegistry, EventBus,
 * VoxelConfig). Run directly with `java` — no JUnit/Minecraft deps — as a cheap sanity
 * check before wiring these into the real Fabric entrypoints.
 */
public final class SmokeTest {

    static int failures = 0;

    public static void main(String[] args) throws Exception {
        testModuleRegistryInitOrderAndIsolation();
        testDuplicateIdRejected();
        testEventBusDispatch();
        testConfigRoundTrip();
        testLegacyProfile();

        if (failures == 0) {
            System.out.println("ALL SMOKE TESTS PASSED");
        } else {
            System.out.println(failures + " SMOKE TEST(S) FAILED");
            System.exit(1);
        }
    }

    static void testModuleRegistryInitOrderAndIsolation() {
        ModuleRegistry registry = new ModuleRegistry();
        StringBuilder order = new StringBuilder();

        registry.register(new VoxelModule() {
            public String id() { return "a"; }
            public String displayName() { return "A"; }
            public void onInitialize() { order.append("a"); }
            public void onShutdown() { }
        });
        registry.register(new VoxelModule() {
            public String id() { return "broken"; }
            public String displayName() { return "Broken"; }
            public void onInitialize() { throw new RuntimeException("boom"); }
            public void onShutdown() { }
        });
        registry.register(new VoxelModule() {
            public String id() { return "b"; }
            public String displayName() { return "B"; }
            public void onInitialize() { order.append("b"); }
            public void onShutdown() { }
        });

        registry.initializeAll();

        check("init order preserved despite failure in the middle",
                order.toString().equals("ab"));
        check("registry size is 3", registry.size() == 3);

        boolean threw = false;
        try {
            registry.register(new VoxelModule() {
                public String id() { return "late"; }
                public String displayName() { return "Late"; }
                public void onInitialize() { }
                public void onShutdown() { }
            });
        } catch (IllegalStateException e) {
            threw = true;
        }
        check("registering after initializeAll() throws", threw);
    }

    static void testDuplicateIdRejected() {
        ModuleRegistry registry = new ModuleRegistry();
        VoxelModule m = new VoxelModule() {
            public String id() { return "dup"; }
            public String displayName() { return "Dup"; }
            public void onInitialize() { }
            public void onShutdown() { }
        };
        registry.register(m);
        boolean threw = false;
        try {
            registry.register(m);
        } catch (IllegalStateException e) {
            threw = true;
        }
        check("duplicate module id rejected", threw);
    }

    static void testEventBusDispatch() {
        EventBus bus = new EventBus();
        AtomicInteger hits = new AtomicInteger();
        record Ping(int n) { }
        record Pong(int n) { }

        bus.subscribe(Ping.class, p -> hits.addAndGet(p.n()));
        bus.subscribe(Pong.class, p -> hits.addAndGet(-100)); // must never fire

        bus.publish(new Ping(5));
        bus.publish(new Ping(7));

        check("event bus only dispatches to matching type, sums payloads", hits.get() == 12);
    }

    static void testConfigRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("voxelcore-config-test");
        VoxelConfig config = VoxelConfig.loadOrDefault(dir);

        check("default profile is BALANCED", config.getProfile() == PerformanceProfile.BALANCED);

        config.setProfile(PerformanceProfile.PERFORMANCE);
        config.setRenderDistanceOverride(12);
        config.setFpsOverlayEnabled(false);
        config.save();

        config.setAdaptiveEnabled(false);
        config.setNativeEnabled(false);
        config.setMemoryOverlayEnabled(true);
        config.save();

        VoxelConfig reloaded = VoxelConfig.loadOrDefault(dir);
        check("adaptive flag persists", !reloaded.isAdaptiveEnabled());
        check("native flag persists", !reloaded.isNativeEnabled());
        check("memory overlay persists", reloaded.isMemoryOverlayEnabled());
        check("profile persists across reload", reloaded.getProfile() == PerformanceProfile.PERFORMANCE);
        check("render distance override persists", reloaded.getRenderDistanceOverride() == 12);
        check("fps overlay flag persists", !reloaded.isFpsOverlayEnabled());
    }

    static void testLegacyProfile() throws Exception {
        Path dir = Files.createTempDirectory("voxelcore-config-legacy");
        Files.writeString(dir.resolve("voxelcore.properties"), "performance.profile=QUALITY\n");
        check("legacy profile name falls back to BALANCED",
                VoxelConfig.loadOrDefault(dir).getProfile() == PerformanceProfile.BALANCED);
        Files.writeString(dir.resolve("voxelcore.properties"), "performance.profile=EXTREME\n");
        check("EXTREME profile parses", VoxelConfig.loadOrDefault(dir).getProfile() == PerformanceProfile.EXTREME);
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
