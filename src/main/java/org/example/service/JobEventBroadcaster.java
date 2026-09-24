package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tells any open browser tab when the job list has changed (a job was created, its status changed, or it
 * was deleted), over Server-Sent Events - one-way, server-to-browser, which is all this needs: the
 * frontend already talks back over plain HTTP for its own actions (POST/DELETE), so a full WebSocket
 * would add a handshake and a dependency for no benefit here.
 * <p>
 * The event itself carries no data - a plain signal to refetch the (small, personal) job list is simpler
 * and more robust than trying to keep an incremental diff in sync with every client.
 */
@Slf4j
@Component
public class JobEventBroadcaster {

    private static final String EVENT_NAME = "jobs-changed";
    private static final long NO_TIMEOUT = 0L;

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(NO_TIMEOUT);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    /** Call after any change a job list on screen should reflect: created, status changed, or deleted. */
    public void jobsChanged() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_NAME).data(""));
            } catch (IOException | IllegalStateException e) {
                // The tab is gone (closed, reloaded, network dropped) - not an error, just cleanup.
                emitters.remove(emitter);
            }
        }
    }
}
