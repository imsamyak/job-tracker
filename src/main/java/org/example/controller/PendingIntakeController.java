package org.example.controller;

import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.example.service.IntakeScheduler;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Read-only view of postings still being processed (or awaiting a Yes/No), for the frontend's list. */
@RestController
public class PendingIntakeController {

    private final PendingIntakeRepository pendingIntakes;
    private final JobRepository jobs;
    private final IntakeScheduler scheduler;

    public PendingIntakeController(PendingIntakeRepository pendingIntakes, JobRepository jobs,
                                   IntakeScheduler scheduler) {
        this.pendingIntakes = pendingIntakes;
        this.jobs = jobs;
        this.scheduler = scheduler;
    }

    /**
     * @param company/role   null while still PENDING (not read yet)
     * @param similarJob     the earlier job this one resembles, only for NEEDS_CONFIRMATION
     * @param errorMessage   only for FAILED
     */
    public record SimilarJob(String id, String company, String role, String status) {
        static SimilarJob of(Job job) {
            return new SimilarJob(job.getId().toString(), job.getCompany(), job.getRole(), job.getStatus().name());
        }
    }

    /** @param active true only while a worker thread is reading THIS row right now - see {@link IntakeScheduler}. */
    public record PendingIntakeSummary(String id, String status, String company, String role,
                                       SimilarJob similarJob, String errorMessage, Instant createdAt,
                                       boolean active) {
    }

    @GetMapping("/api/pending-intakes")
    public List<PendingIntakeSummary> list() {
        Set<UUID> active = scheduler.activeIds();
        return pendingIntakes.findAll(Sort.by(Sort.Direction.ASC, "createdAt")).stream()
                .map(p -> toSummary(p, active))
                .toList();
    }

    private PendingIntakeSummary toSummary(PendingIntake p, Set<UUID> active) {
        SimilarJob similar = p.getSimilarJobId() == null ? null
                : jobs.findById(p.getSimilarJobId()).map(SimilarJob::of).orElse(null);
        return new PendingIntakeSummary(p.getId().toString(), p.getStatus().name(), p.getCompany(), p.getRole(),
                similar, p.getErrorMessage(), p.getCreatedAt(), active.contains(p.getId()));
    }
}
