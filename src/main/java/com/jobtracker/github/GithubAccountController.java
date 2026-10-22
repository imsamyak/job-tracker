package com.jobtracker.github;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Whether GitHub is connected, and connecting or disconnecting it. The token goes in once and is never sent back out. */
@RestController
@RequestMapping("/api/github")
public class GithubAccountController {

    public record Status(boolean connected, String login, Instant connectedAt) {
        static Status of(GithubAccount a) {
            return new Status(true, a.getLogin(), a.getConnectedAt());
        }
    }

    public record Token(String token) {}

    private final GithubAccountService service;

    public GithubAccountController(GithubAccountService service) {
        this.service = service;
    }

    @GetMapping
    public Status status() {
        return service.current().map(Status::of).orElse(new Status(false, null, null));
    }

    @PostMapping
    public Status connect(@RequestBody Token body) {
        String token = body.token() == null ? "" : body.token().strip();
        if (token.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Paste the token.");
        try {
            return Status.of(service.connect(token));
        } catch (GithubIdentity.TokenException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        service.disconnect();
        return ResponseEntity.noContent().build();
    }
}
