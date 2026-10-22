package com.jobtracker.agent;

import com.jobtracker.ai.ClaudeCli;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

/** Asks Claude, with no tools of its own: it can only answer in the fixed shape, and the app decides what to run. */
@Component
public class ClaudeAgentBrain implements AgentBrain {

    static final String SCHEMA = """
            {"type":"object","properties":{
              "reply":{"type":"string"},
              "steps":{"type":"array","items":{"type":"object","properties":{
                "type":{"type":"string","enum":["SET_STATUS","ADD_JOB","SYNC_MAIL","DELETE_JOB","OPEN"]},
                "jobId":{"type":"integer"},
                "status":{"type":"string","enum":["TO_APPLY","APPLIED","IN_REVIEW","REJECTED",""]},
                "text":{"type":"string"},
                "route":{"type":"string"}},
                "required":["type","jobId","status","text","route"]}}},
             "required":["reply","steps"]}""";

    private final ClaudeCli claude;

    public ClaudeAgentBrain(ClaudeCli claude) {
        this.claude = claude;
    }

    @Override
    public Plan think(String prompt) throws AgentException {
        try {
            return parse(claude.run("agent", prompt, SCHEMA));
        } catch (ClaudeCli.AiException e) {
            throw new AgentException("The assistant could not answer: " + e.getMessage(), e);
        }
    }

    static Plan parse(JsonNode a) {
        List<Step> steps = new ArrayList<>();
        for (JsonNode s : a.path("steps")) {
            steps.add(new Step(s.path("type").asText(""), s.path("jobId").asLong(0), s.path("status").asText(""), s.path("text").asText(""), s.path("route").asText("")));
        }
        return new Plan(a.path("reply").asText("").strip(), steps);
    }
}
