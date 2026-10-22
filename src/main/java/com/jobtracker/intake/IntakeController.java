package com.jobtracker.intake;

import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Hand the app a job description. Every call returns at once (202); the job appears in the list when
 * it has been read.
 */
@RestController
@RequestMapping("/api/intakes")
public class IntakeController {

    public record AddTextRequest(String text) {}

    /** What the screen needs to show a waiting or failed item. */
    public record IntakeView(Long id, String kind, String status, String label, String message, Long jobId, Instant createdAt) {
        static IntakeView of(Intake i) {
            return new IntakeView(i.getId(), i.getKind().name(), i.getStatus().name(), i.getLabel(), i.getMessage(), i.getJobId(), i.getCreatedAt());
        }
    }

    private final IntakeService service;

    public IntakeController(IntakeService service) {
        this.service = service;
    }

    /** A pasted job description, or a single link to one. */
    @PostMapping
    public ResponseEntity<IntakeView> addText(@RequestBody AddTextRequest req) {
        return ResponseEntity.status(ACCEPTED).body(IntakeView.of(service.addText(req.text())));
    }

    /** A screenshot or photo of a job description. */
    @PostMapping("/image")
    public ResponseEntity<IntakeView> addImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "That file is empty.");
        try {
            return ResponseEntity.status(ACCEPTED).body(IntakeView.of(service.addImage(file.getBytes(), file.getOriginalFilename())));
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read the uploaded file.");
        }
    }

    @GetMapping
    public List<IntakeView> active() {
        return service.active().stream().map(IntakeView::of).toList();
    }

    @PostMapping("/{id}/retry")
    public IntakeView retry(@PathVariable Long id) {
        return IntakeView.of(service.retry(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable Long id) {
        service.dismiss(id);
        return ResponseEntity.noContent().build();
    }
}
