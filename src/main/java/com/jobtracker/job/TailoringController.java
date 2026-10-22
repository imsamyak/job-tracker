package com.jobtracker.job;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.jobtracker.tailoring.Tailoring;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** What the resume for one job would show. Free and instant, so it is worked out whenever it is asked for. */
@RestController
@RequestMapping("/api/jobs")
public class TailoringController {

    public record Section(String label, List<String> skills) {}

    /** @param unmatched words from the job that are not on your skills list: never added to a resume */
    public record Tailored(List<String> stacks, String summary, List<Section> sections, List<String> unmatched) {}

    /** One job of the profile's experience; bullets are its lines as written. */
    public record EntryIn(String id, List<String> bullets) {}

    public record BulletsIn(List<EntryIn> entries, Integer maxLines) {}

    public record BulletOut(String text, List<String> named) {}

    public record EntryOut(String id, List<BulletOut> bullets) {}

    static final int DEFAULT_LINES = 14;
    static final int MAX_LINES = 60;

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
        return new Tailored(t.stacks(), Tailoring.summaryVariant(t.stacks()), t.skills().sections().stream().map(s -> new Section(s.label(), s.skills())).toList(), t.skills().unmatched());
    }

    /** The user's own experience bullets chosen and ordered for this job. They come from the browser-held profile, so they are sent in. */
    @PostMapping("/{id}/bullets")
    public List<EntryOut> bullets(@PathVariable Long id, @RequestBody BulletsIn in) {
        Job job = jobs.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "No such job."));
        if (in.entries() == null) throw new ResponseStatusException(BAD_REQUEST, "No experience was sent.");
        String text = descriptions.findById(id).map(JobDescription::getText).orElse("");
        int lines = Math.max(1, Math.min(in.maxLines() == null ? DEFAULT_LINES : in.maxLines(), MAX_LINES));
        List<Tailoring.BulletSource> sources = in.entries().stream().map(e -> new Tailoring.BulletSource(e.id(), e.bullets())).toList();
        return tailoring.pickBullets(job.getRole() + "\n" + text, job.getSkills(), sources, lines).stream()
                .map(e -> new EntryOut(e.id(), e.bullets().stream().map(b -> new BulletOut(b.text(), b.named())).toList()))
                .toList();
    }
}
