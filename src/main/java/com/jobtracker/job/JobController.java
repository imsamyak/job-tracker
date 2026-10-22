package com.jobtracker.job;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.jobtracker.contact.ContactRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    public record UpdateStatusRequest(JobStatus status) {}

    private final JobRepository jobs;
    private final JobDescriptionRepository descriptions;
    private final ContactRepository contacts;

    public JobController(JobRepository jobs, JobDescriptionRepository descriptions, ContactRepository contacts) {
        this.jobs = jobs;
        this.descriptions = descriptions;
        this.contacts = contacts;
    }

    /** Add a job by hand, without any posting to read. Only company and role are required. */
    public record CreateJobRequest(String company, String role, String location, String workMode, String jobType,
                                   String experience, String salary, String postingId, String platform, String link,
                                   List<String> skills, String description, JobStatus status) {}

    @PostMapping
    @Transactional
    public ResponseEntity<Job> create(@RequestBody CreateJobRequest req) {
        if (req.company() == null || req.company().isBlank() || req.role() == null || req.role().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "company and role are required");
        }
        Job job = new Job();
        job.setCompany(req.company().strip());
        job.setRole(req.role().strip());
        job.setLocation(req.location());
        job.setWorkMode(req.workMode());
        job.setJobType(req.jobType());
        job.setExperience(req.experience());
        job.setSalary(req.salary());
        job.setPostingId(req.postingId());
        job.setPlatform(req.platform());
        job.setLink(req.link());
        job.setAddedFrom("MANUAL");
        if (req.skills() != null) job.setSkills(req.skills());
        if (req.status() != null) job.setStatus(req.status());
        job = jobs.save(job);
        if (req.description() != null && !req.description().isBlank()) {
            descriptions.save(new JobDescription(job.getId(), req.description()));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(job);
    }

    /** Newest first. */
    @GetMapping
    public List<Job> list() {
        return jobs.findAllByOrderByAddedOnDescIdDesc();
    }

    @GetMapping("/{id}")
    public Job get(@PathVariable Long id) {
        return find(id);
    }

    /** The full job description text, or an empty string when none was kept. */
    @GetMapping("/{id}/description")
    public String description(@PathVariable Long id) {
        find(id);
        return descriptions.findById(id).map(JobDescription::getText).orElse("");
    }

    @PatchMapping("/{id}/status")
    public Job updateStatus(@PathVariable Long id, @RequestBody UpdateStatusRequest req) {
        if (req.status() == null) throw new ResponseStatusException(BAD_REQUEST, "status is required");
        Job job = find(id);
        job.setStatus(req.status());
        return jobs.save(job);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        find(id);
        descriptions.deleteById(id);
        contacts.deleteByJobId(id); // a job's contacts go with it
        jobs.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Job find(Long id) {
        return jobs.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "job " + id + " not found"));
    }
}
