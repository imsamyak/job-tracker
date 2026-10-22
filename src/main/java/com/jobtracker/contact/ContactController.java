package com.jobtracker.contact;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CREATED;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import com.jobtracker.job.JobRepository;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Contacts (HR, recruiters) saved against a job: a name, a role and one way to reach them. */
@RestController
@RequestMapping("/api")
public class ContactController {

    public record ContactRequest(String name, String role, String contact) {}

    private final ContactRepository contacts;
    private final JobRepository jobs;

    public ContactController(ContactRepository contacts, JobRepository jobs) {
        this.contacts = contacts;
        this.jobs = jobs;
    }

    @GetMapping("/jobs/{jobId}/contacts")
    public List<Contact> list(@PathVariable Long jobId) {
        requireJob(jobId);
        return contacts.findByJobIdOrderByCreatedAtAscIdAsc(jobId);
    }

    @PostMapping("/jobs/{jobId}/contacts")
    public ResponseEntity<Contact> add(@PathVariable Long jobId, @RequestBody ContactRequest req) {
        requireJob(jobId);
        Contact c = new Contact(jobId);
        apply(c, req);
        return ResponseEntity.status(CREATED).body(contacts.save(c));
    }

    @PutMapping("/contacts/{id}")
    public Contact update(@PathVariable Long id, @RequestBody ContactRequest req) {
        Contact c = contacts.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "contact " + id + " not found"));
        apply(c, req);
        return contacts.save(c);
    }

    @DeleteMapping("/contacts/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!contacts.existsById(id)) throw new ResponseStatusException(NOT_FOUND, "contact " + id + " not found");
        contacts.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void requireJob(Long jobId) {
        if (!jobs.existsById(jobId)) throw new ResponseStatusException(NOT_FOUND, "job " + jobId + " not found");
    }

    /** Trims and checks the fields. Blank optional fields become null. Package-private for tests. */
    static void apply(Contact c, ContactRequest r) {
        String name = trim(r.name());
        if (name == null) throw new ResponseStatusException(BAD_REQUEST, "Name is required.");
        if (name.length() > 120) throw new ResponseStatusException(BAD_REQUEST, "That name is too long.");
        String role = trim(r.role());
        if (role != null && role.length() > 120) throw new ResponseStatusException(BAD_REQUEST, "That role is too long.");
        String contact = trim(r.contact());
        if (contact != null && contact.length() > 200) throw new ResponseStatusException(BAD_REQUEST, "That contact is too long.");
        c.setName(name);
        c.setRole(role);
        c.setContact(contact);
    }

    private static String trim(String s) {
        if (s == null) return null;
        String t = s.strip();
        return t.isEmpty() ? null : t;
    }
}
