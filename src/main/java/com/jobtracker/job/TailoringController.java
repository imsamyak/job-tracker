package com.jobtracker.job;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.jobtracker.tailoring.Tailoring;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** What the resume for one job would show. Free and instant, so it is worked out whenever it is asked for. */
@RestController
@RequestMapping("/api/jobs")
public class TailoringController {

    public record Section(String label, List<String> skills) {}

    /** @param unmatched words from the job that are not on your skills list: never added to a resume */
    public record Tailored(List<String> stacks, List<Section> sections, List<String> unmatched) {}

    private final JobRepository jobs;
    private final JobDescriptionRepository descriptions;
    private final Tailoring tailoring;

    public TailoringController(JobRepository jobs, JobDescriptionRepository descriptions, Tailoring tailoring) {
        this.jobs = jobs;
        this.descriptions = descriptions;
        this.tailoring = tailoring;
    }

    @GetMapping("/{id}/tailored")
    public Tailored tailored(@PathVariable Long id) {
        Job job = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such job."));
        String text = descriptions.findById(id).map(JobDescription::getText).orElse("");
        // the role and company count too: a one-line job still says "Java Developer"
        Tailoring.Tailored t = tailoring.tailor(job.getRole() + "\n" + text, job.getSkills());
        return new Tailored(t.stacks(), t.skills().sections().stream().map(s -> new Section(s.label(), s.skills())).toList(), t.skills().unmatched());
    }
}
