package com.jobtracker.contact;

import static org.junit.jupiter.api.Assertions.*;

import com.jobtracker.contact.ContactController.ContactRequest;
import com.jobtracker.job.Job;
import com.jobtracker.job.JobController;
import com.jobtracker.job.JobRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class ContactTest {
    @Autowired ContactController controller;
    @Autowired ContactRepository contacts;
    @Autowired JobController jobController;
    @Autowired JobRepository jobs;

    private Long newJob(String company) {
        Job j = new Job();
        j.setCompany(company);
        j.setRole("Engineer");
        return jobs.save(j).getId();
    }

    private static ContactRequest req(String name) {
        return new ContactRequest(name, null, null);
    }

    @Test
    void addListEditAndDeleteAContact() {
        Long job = newJob("Contact Co 1");
        Contact c = controller.add(job, new ContactRequest(" Priya Sharma ", "Technical Recruiter", " priya@acme.example ")).getBody();
        assertNotNull(c.getId());
        assertEquals("Priya Sharma", c.getName()); // trimmed
        assertEquals("priya@acme.example", c.getContact());
        assertEquals(1, controller.list(job).size());

        Contact edited = controller.update(c.getId(), new ContactRequest("Priya S.", "HR Manager", "+91 98765 43210"));
        assertEquals("Priya S.", edited.getName());
        assertEquals("HR Manager", edited.getRole());
        assertEquals("+91 98765 43210", edited.getContact()); // a phone number is as good as an email

        Contact cleared = controller.update(c.getId(), req("Priya S."));
        assertNull(cleared.getRole()); // a cleared field really is cleared
        assertNull(cleared.getContact());

        controller.delete(c.getId());
        assertTrue(controller.list(job).isEmpty());
    }

    @Test
    void contactsAreKeptInTheOrderTheyWereAddedAndPerJob() {
        Long a = newJob("Contact Co 2a");
        Long b = newJob("Contact Co 2b");
        controller.add(a, req("First"));
        controller.add(a, req("Second"));
        controller.add(b, req("Other job"));
        assertEquals(List.of("First", "Second"), controller.list(a).stream().map(Contact::getName).toList());
        assertEquals(List.of("Other job"), controller.list(b).stream().map(Contact::getName).toList());
    }

    @Test
    void onlyTheNameIsRequired() {
        Long job = newJob("Contact Co 3");
        assertNotNull(controller.add(job, req("Just a name")).getBody().getId());
        assertThrows(ResponseStatusException.class, () -> controller.add(job, req("   ")));
        assertThrows(ResponseStatusException.class, () -> controller.add(job, req(null)));
        assertThrows(ResponseStatusException.class, () -> controller.add(job, req("x".repeat(121))));
        assertEquals(1, controller.list(job).size()); // none of the bad ones were saved
    }

    @Test
    void aContactForAMissingJobOrContactIsNotFound() {
        assertThrows(ResponseStatusException.class, () -> controller.add(987654L, req("Nobody")));
        assertThrows(ResponseStatusException.class, () -> controller.list(987654L));
        assertThrows(ResponseStatusException.class, () -> controller.update(987654L, req("Nobody")));
        assertThrows(ResponseStatusException.class, () -> controller.delete(987654L));
    }

    @Test
    void deletingAJobRemovesItsContacts() {
        Long job = newJob("Contact Co 4");
        controller.add(job, req("Goes with the job"));
        assertFalse(contacts.findByJobIdOrderByCreatedAtAscIdAsc(job).isEmpty());
        jobController.delete(job);
        assertTrue(contacts.findByJobIdOrderByCreatedAtAscIdAsc(job).isEmpty());
    }
}
