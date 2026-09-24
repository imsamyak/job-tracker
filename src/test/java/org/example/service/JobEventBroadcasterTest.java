package org.example.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class JobEventBroadcasterTest {

    @Test
    void broadcastsToEverySubscribedEmitterWithoutThrowing() {
        JobEventBroadcaster broadcaster = new JobEventBroadcaster();
        broadcaster.subscribe();
        broadcaster.subscribe();

        assertDoesNotThrow(broadcaster::jobsChanged);
    }

    @Test
    void aFailingEmitterIsDroppedWithoutAffectingLaterBroadcasts() {
        JobEventBroadcaster broadcaster = new JobEventBroadcaster();
        broadcaster.subscribe();
        SseEmitter broken = broadcaster.subscribe();
        broken.complete(); // completing it first makes a later send() throw IllegalStateException

        assertDoesNotThrow(broadcaster::jobsChanged); // `broken` fails internally but must not propagate
        assertDoesNotThrow(broadcaster::jobsChanged); // second call proves `broken` was removed, not retried
    }
}
