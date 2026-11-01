package com.voxelcore.core;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Minimal typed pub/sub bus so modules (Core, UI, Render, Chunk, Memory, Native) can react to
 * each other without holding direct references. CopyOnWriteArrayList because subscription is
 * rare (startup) and dispatch is frequent and may happen from render/chunk worker threads.
 */
public final class EventBus {

    private static final Logger LOGGER = System.getLogger("VoxelCore/EventBus");

    private final Map<Class<?>, CopyOnWriteArrayList<Consumer<Object>>> listeners = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    public <T> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                .add((Consumer<Object>) listener);
    }

    public void publish(Object event) {
        List<Consumer<Object>> subs = listeners.get(event.getClass());
        if (subs == null || subs.isEmpty()) {
            return;
        }
        for (Consumer<Object> listener : subs) {
            try {
                listener.accept(event);
            } catch (Throwable t) {
                LOGGER.log(Level.ERROR, "Listener threw handling " + event.getClass().getSimpleName(), t);
            }
        }
    }
}
