package com.jobtracker.resume;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

import java.io.IOException;
import java.time.Instant;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/** Your sample resume: add it, see it, and read your profile details out of it. */
@RestController
@RequestMapping("/api/resume")
public class ResumeController {

    public record ResumeInfo(boolean present, String filename, Long size, Instant uploadedAt) {
        static ResumeInfo of(Resume r) {
            return new ResumeInfo(true, r.getFilename(), r.getSize(), r.getUploadedAt());
        }
    }

    private final ResumeService resumes;
    private final ProfileExtractor extractor;

    public ResumeController(ResumeService resumes, ProfileExtractor extractor) {
        this.resumes = resumes;
        this.extractor = extractor;
    }

    @GetMapping
    public ResumeInfo info() {
        return resumes.current().map(ResumeInfo::of).orElse(new ResumeInfo(false, null, null, null));
    }

    /** Adds the resume, or replaces the one already there. */
    @PostMapping
    public ResumeInfo upload(@RequestParam("file") MultipartFile file) {
        try {
            return ResumeInfo.of(resumes.save(file.getBytes(), file.getOriginalFilename()));
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read the uploaded file.");
        }
    }

    /** The PDF itself, to look at in the browser. */
    @GetMapping("/file")
    public ResponseEntity<Resource> file() {
        Resume r = resumes.current().orElseThrow(() -> new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "No resume has been added yet."));
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(r.getFilename()).build().toString())
                .body(new FileSystemResource(resumes.file()));
    }

    @DeleteMapping
    public ResponseEntity<Void> delete() {
        resumes.delete();
        return ResponseEntity.noContent().build();
    }

    /** Reads the resume and returns the profile details in it. Nothing is saved: the screen shows them for review. */
    /** Reads the details out of another resume without keeping it, so your saved sample resume stays as it is. */
    @PostMapping("/read")
    public ExtractedProfile read(@RequestParam("file") MultipartFile file) {
        String text;
        try {
            text = resumes.textOf(file.getBytes());
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "Could not read the uploaded file.");
        }
        if (text.isBlank()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "That PDF has no readable text (it may be a scan). Add a PDF made from a document.");
        }
        try {
            return extractor.extract(text);
        } catch (ProfileExtractor.ProfileExtractionException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }
    }

    @PostMapping("/extract")
    public ExtractedProfile extract() {
        String text = resumes.text();
        if (text.isBlank()) {
            throw new ResponseStatusException(UNPROCESSABLE_ENTITY, "That PDF has no readable text (it may be a scan). Add a PDF made from a document.");
        }
        try {
            return extractor.extract(text);
        } catch (ProfileExtractor.ProfileExtractionException e) {
            throw new ResponseStatusException(BAD_GATEWAY, e.getMessage());
        }
    }
}
