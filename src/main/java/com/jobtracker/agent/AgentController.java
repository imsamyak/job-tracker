package com.jobtracker.agent;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Ask AI: the conversation so far in, a reply (and what was done) out. */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    public record Request(List<AgentService.Message> messages) {}

    private final AgentService agent;

    public AgentController(AgentService agent) {
        this.agent = agent;
    }

    @PostMapping("/chat")
    public AgentService.Result chat(@RequestBody Request req) {
        if (req.messages() == null || req.messages().isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Say something first.");
        try {
            return agent.chat(req.messages());
        } catch (AgentBrain.AgentException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }
    }
}
