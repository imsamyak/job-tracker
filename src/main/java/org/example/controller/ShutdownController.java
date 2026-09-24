package org.example.controller;

import org.example.service.ShutdownService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Shuts the app down, but not immediately: {@link #GRACE} passes first, and using the app again in the
 * meantime (any other request - see {@code ShutdownCancelFilter}) cancels it. Calling this again while
 * already pending just restarts the countdown.
 */
@RestController
public class ShutdownController {

    public static final String PATH = "/api/shutdown";
    private static final Duration GRACE = Duration.ofMinutes(1);

    private final ShutdownService shutdownService;

    public ShutdownController(ShutdownService shutdownService) {
        this.shutdownService = shutdownService;
    }

    public record ShutdownResponse(int scheduledInSeconds) {
    }

    @PostMapping(PATH)
    public ShutdownResponse shutdown() {
        shutdownService.scheduleShutdown(GRACE);
        return new ShutdownResponse((int) GRACE.toSeconds());
    }
}
