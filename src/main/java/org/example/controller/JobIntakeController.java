package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.entity.Job;
import org.example.domain.entity.PendingIntake;
import org.example.service.intake.JobIntakeException;
import org.example.service.intake.JobIntakeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * The extension's entire job: a job page, saved as a PDF, comes in here and this returns almost
 * instantly - nothing here reads the posting. The actual (slow) reading happens later, in the
 * background, and resolving an ambiguous posting happens in the frontend - see {@code IntakeScheduler}
 * and {@code PendingIntakeController}.
 */
@Slf4j
@RestController
public class JobIntakeController {

    private final JobIntakeService intake;

    public JobIntakeController(JobIntakeService intake) {
        this.intake = intake;
    }

    public record AcceptedResponse(String id, String status, boolean alreadyTracked) {
        static AcceptedResponse queued(PendingIntake p) {
            return new AcceptedResponse(p.getId().toString(), p.getStatus().name(), false);
        }

        static AcceptedResponse alreadyTracked(Job job) {
            return new AcceptedResponse(job.getId().toString(), job.getStatus().name(), true);
        }
    }

    public record ErrorResponse(String error) {
    }

    /** @param url the page's URL, if the caller has one - used only to dedupe a resubmission before it's read */
    @PostMapping(path = "/api/jobs/intake", consumes = "multipart/form-data")
    public ResponseEntity<AcceptedResponse> intake(@RequestParam("jdPdf") MultipartFile jdPdf,
                                                   @RequestParam(value = "url", required = false) String url) {
        if (jdPdf.isEmpty()) {
            throw new JobIntakeException("jdPdf is required and must not be empty");
        }
        return switch (intake.accept(readBytes(jdPdf), url)) {
            case JobIntakeService.AcceptOutcome.New n ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.queued(n.pending()));
            case JobIntakeService.AcceptOutcome.AlreadyQueued q ->
                    ResponseEntity.status(HttpStatus.ACCEPTED).body(AcceptedResponse.queued(q.pending()));
            case JobIntakeService.AcceptOutcome.AlreadyTracked t ->
                    ResponseEntity.ok(AcceptedResponse.alreadyTracked(t.job()));
        };
    }

    /** Saves an ambiguous posting as a genuinely new job, using the fields already extracted. */
    @PostMapping("/api/jobs/pending/{id}/confirm")
    public ResponseEntity<JobController.JobSummary> confirm(@PathVariable UUID id) {
        Job saved = intake.confirmPending(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobController.JobSummary.of(saved));
    }

    /** Discards a pending posting (declining an ambiguous one, or dismissing a failed one). */
    @DeleteMapping("/api/jobs/pending/{id}")
    public ResponseEntity<Void> reject(@PathVariable UUID id) {
        intake.rejectPending(id);
        return ResponseEntity.noContent().build();
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded PDF", e);
        }
    }

    @ExceptionHandler(JobIntakeException.class)
    public ResponseEntity<ErrorResponse> onInvalidJob(JobIntakeException e) {
        return ResponseEntity.unprocessableEntity().body(new ErrorResponse(e.getMessage()));
    }
}
