package com.example.onboarding.infrastructure.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Per-job fan-out of SseEmitter subscribers. Multiple browser tabs can watch the same job,
 * so we keep a list of emitters per jobId. The registry hides the map/list bookkeeping so
 * publishers and controllers only deal with jobId + event.
 *
 * Concurrency: the orchestrator writes from a virtual thread while the servlet container
 * reads from request threads — ConcurrentHashMap + CopyOnWriteArrayList give us safe
 * iteration during broadcasts without explicit locking.
 */
@Component
public class SseEmitterRegistry {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterRegistry.class);

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public void register(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        emitter.onCompletion(() -> remove(jobId, emitter));
        emitter.onTimeout(()    -> remove(jobId, emitter));
        emitter.onError(e       -> remove(jobId, emitter));
    }

    /**
     * Sends an event to every live emitter subscribed to the job. Dead emitters are dropped
     * silently — the client will reconnect or has already completed.
     */
    public void broadcast(UUID jobId, String eventName, Object payload) {
        List<SseEmitter> list = emitters.get(jobId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(System.currentTimeMillis()))
                        .name(eventName)
                        .data(payload));
            } catch (IOException | IllegalStateException e) {
                log.debug("Dropping dead emitter for job {}: {}", jobId, e.getMessage());
                remove(jobId, emitter);
            }
        }
    }

    /** Completes all emitters for a terminal job so clients close cleanly. */
    public void complete(UUID jobId) {
        List<SseEmitter> list = emitters.remove(jobId);
        if (list == null) return;
        for (SseEmitter emitter : list) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // already closed
            }
        }
    }

    private void remove(UUID jobId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(jobId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(jobId, list);
        }
    }
}
