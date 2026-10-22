package com.jobtracker.ai;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;

import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Which Claude login the app is using, and a way to start signing in with a Claude plan. */
@RestController
@RequestMapping("/api/claude")
public class ClaudeAccountController {

    private final ClaudeAccount account;

    public ClaudeAccountController(ClaudeAccount account) {
        this.account = account;
    }

    @GetMapping
    public ClaudeAccount.Status status() {
        return account.status();
    }

    /** Opens the sign-in in the browser on this computer. Returns at once; poll GET until the status changes. */
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void login() {
        try {
            account.startLogin();
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Could not start claude: " + e.getMessage());
        }
    }
}
