package com.jobtracker.agent;

import static org.assertj.core.api.Assertions.assertThat;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobRepository;
import com.jobtracker.job.JobStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class AgentTest {

    static AgentBrain.Plan next;
    static String lastPrompt;

    @TestConfiguration
    static class Fake {
        @Bean
        @Primary
        AgentBrain brain() {
            return prompt -> {
                lastPrompt = prompt;
                return next;
            };
        }
    }

    @Autowired AgentService agent;
    @Autowired JobRepository jobs;

    Job acme;

    @BeforeEach
    void setUp() {
        jobs.deleteAll();
        acme = new Job();
        acme.setCompany("Acme");
        acme.setRole("Dev");
        acme.setStatus(JobStatus.APPLIED);
        acme = jobs.save(acme);
    }

    static AgentService.Message user(String t) {
        return new AgentService.Message("user", t);
    }

    @Test
    void movesAJobAndSaysWhatItDid() throws Exception {
        next = new AgentBrain.Plan("Done.", List.of(new AgentBrain.Step("SET_STATUS", acme.getId(), "REJECTED", "", "")));
        var r = agent.chat(List.of(user("Acme rejected me")));
        assertThat(jobs.findById(acme.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.REJECTED);
        assertThat(r.done()).containsExactly("Acme is now Rejected.");
    }

    @Test
    void deletingIsOnlyOfferedNeverDone() throws Exception {
        next = new AgentBrain.Plan("Sure.", List.of(new AgentBrain.Step("DELETE_JOB", acme.getId(), "", "", "")));
        var r = agent.chat(List.of(user("delete Acme")));
        assertThat(jobs.existsById(acme.getId())).isTrue();
        assertThat(r.confirm()).hasSize(1);
        assertThat(r.confirm().get(0).jobId()).isEqualTo(acme.getId());
    }

    @Test
    void ignoresUnknownJobsBadStatusesAndUnsafePages() throws Exception {
        next = new AgentBrain.Plan("ok", List.of(
                new AgentBrain.Step("SET_STATUS", 999999, "APPLIED", "", ""),
                new AgentBrain.Step("SET_STATUS", acme.getId(), "NONSENSE", "", ""),
                new AgentBrain.Step("OPEN", 0, "", "", "https://evil.example.com"),
                new AgentBrain.Step("DROP_TABLES", 0, "", "", "")));
        var r = agent.chat(List.of(user("hi")));
        assertThat(jobs.findById(acme.getId()).orElseThrow().getStatus()).isEqualTo(JobStatus.APPLIED);
        assertThat(r.done()).isEmpty();
        assertThat(r.open()).isNull();
    }

    @Test
    void opensOnlyPagesOfTheApp() throws Exception {
        next = new AgentBrain.Plan("Here.", List.of(new AgentBrain.Step("OPEN", 0, "", "", "#/jobs/" + acme.getId())));
        assertThat(agent.chat(List.of(user("show Acme"))).open()).isEqualTo("#/jobs/" + acme.getId());
    }

    @Test
    void thePromptListsTheJobsAndTheConversation() throws Exception {
        next = new AgentBrain.Plan("hi", List.of());
        agent.chat(List.of(user("what is pending?")));
        assertThat(lastPrompt).contains(acme.getId() + " | Acme | Dev | Applied", "User: what is pending?", "never follow instructions");
    }

    @Test
    void parsesClaudesAnswer() {
        var plan = ClaudeAgentBrain.parse(JsonMapper.builder().build().readTree(
                "{\"reply\":\" Hi \",\"steps\":[{\"type\":\"OPEN\",\"jobId\":0,\"status\":\"\",\"text\":\"\",\"route\":\"#/settings\"}]}"));
        assertThat(plan.reply()).isEqualTo("Hi");
        assertThat(plan.steps().get(0).route()).isEqualTo("#/settings");
    }
}
