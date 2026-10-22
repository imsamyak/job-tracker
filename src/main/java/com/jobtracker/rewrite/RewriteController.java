package com.jobtracker.rewrite;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Rewrite with AI: a piece of your profile text in, a proposed version out. Nothing is saved here. */
@RestController
@RequestMapping("/api/rewrite")
public class RewriteController {

    static final int MAX_CHARS = 6_000;

    public record Request(TextRewriter.Kind kind, String text, String context, String instruction) {}

    public record Response(String text) {}

    private final TextRewriter rewriter;

    public RewriteController(TextRewriter rewriter) {
        this.rewriter = rewriter;
    }

    @PostMapping
    public Response rewrite(@RequestBody Request req) {
        if (req.kind() == null) throw new ResponseStatusException(BAD_REQUEST, "Say what is being rewritten.");
        if (req.text() == null || req.text().isBlank()) throw new ResponseStatusException(BAD_REQUEST, "There is no text to rewrite.");
        if (req.text().length() > MAX_CHARS) throw new ResponseStatusException(BAD_REQUEST, "That text is too long to rewrite in one go.");
        if (req.instruction() != null && req.instruction().length() > 500) throw new ResponseStatusException(BAD_REQUEST, "Keep the request under 500 characters.");
        try {
            return new Response(rewriter.rewrite(req.kind(), req.text().strip(), req.context(), req.instruction()));
        } catch (TextRewriter.RewriteException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }
    }
}
