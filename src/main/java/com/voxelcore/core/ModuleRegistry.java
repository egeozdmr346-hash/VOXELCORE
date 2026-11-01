package com.voxelcore.core;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Central registry for all VoxelCore subsystems. Thread-safe: registration happens on the
 * main thread during bootstrap, but lookups (e.g. from render/chunk worker threads) must
 * not race with that. A read-write lock keeps the common case (lookup) cheap.
 *
 * Insertion order is preserved and is the module initialization order, so Bootstrap controls
 * dependency ordering purely by the sequence it registers modules in (no separate DAG needed
 * at this project's current size).
 */
public final class ModuleRegistry {

    private static final Logger LOGGER = System.getLogger("VoxelCore/ModuleRegistry");

    private final Map<String, VoxelModule> modules = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean initialized = false;

    public void register(VoxelModule module) {
        Objects.requireNonNull(module, "module");
        lock.writeLock().lock();
        try {
            if (initialized) {
                throw new IllegalStateException(
                        "Cannot register module '" + module.id() + "' after initializeAll() has run. "
                                + "Register all modules during Bootstrap before calling initializeAll().");
            }
            VoxelModule previous = modules.putIfAbsent(module.id(), module);
            if (previous != null) {
                throw new IllegalStateException("Duplicate module id: " + module.id());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends VoxelModule> T get(String id, Class<T> type) {
        lock.readLock().lock();
        try {
            VoxelModule module = modules.get(id);
            if (module == null) {
                return null;
            }
            if (!type.isInstance(module)) {
                throw new ClassCastException(
                        "Module '" + id + "' is " + module.getClass().getName() + ", not " + type.getName());
            }
            return (T) module;
        } finally {
            lock.readLock().unlock();
        }
    }

    public Collection<VoxelModule> all() {
        lock.readLock().lock();
        try {
            return Map.copyOf(modules).values();
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Initializes every registered module in registration order. A module that throws is
     * logged and skipped — it does not take down the rest of the engine. This is the
     * difference between "one optional subsystem is degraded" and "the whole client won't start".
     */
    public void initializeAll() {
        lock.writeLock().lock();
        try {
            if (initialized) {
                throw new IllegalStateException("initializeAll() already called");
            }
            initialized = true;
        } finally {
            lock.writeLock().unlock();
        }

        for (VoxelModule module : modules.values()) {
            long start = System.nanoTime();
            try {
                module.onInitialize();
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                LOGGER.log(Level.INFO, String.format("Initialized module '%s' (%s) in %.2f ms",
                        module.id(), module.displayName(), ms));
            } catch (Throwable t) {
                LOGGER.log(Level.ERROR, "Module '" + module.id() + "' failed to initialize — "
                        + "disabling it, the rest of VoxelCore continues.", t);
            }
        }
    }

    public void shutdownAll() {
        lock.readLock().lock();
        Collection<VoxelModule> snapshot;
        try {
            snapshot = Map.copyOf(modules).values();
        } finally {
            lock.readLock().unlock();
        }
        for (VoxelModule module : snapshot) {
            try {
                module.onShutdown();
            } catch (Throwable t) {
                LOGGER.log(Level.ERROR, "Module '" + module.id() + "' threw during shutdown", t);
            }
        }
    }

    public int size() {
        lock.readLock().lock();
        try {
            return modules.size();
        } finally {
            lock.readLock().unlock();
        }
    }
}
