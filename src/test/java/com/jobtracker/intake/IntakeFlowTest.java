package com.jobtracker.intake;

import static org.junit.jupiter.api.Assertions.*;

import com.jobtracker.job.*;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
class IntakeFlowTest {

    /** Stands in for Claude: returns whatever the test scripted and remembers what it was asked. */
    static class FakeExtractor implements JdExtractor {
        ExtractedJd answer;
        ExtractionException failure;
        String lastText;
        Path lastImage;

        @Override
        public ExtractedJd extract(String text, Path image) throws ExtractionException {
            lastText = text;
            lastImage = image;
            if (failure != null) throw failure;
            return answer;
        }
    }

    /** No network in tests. */
    static class FakeLinkFetcher extends LinkFetcher {
        @Override
        public Page fetch(String link) {
            return new Page(link, "Page title: Backend Engineer\n\nWe are hiring a backend engineer ...", "LinkedIn");
        }
    }

    @TestConfiguration
    static class Config {
        @Bean @Primary FakeExtractor fakeExtractor() { return new FakeExtractor(); }
        @Bean @Primary LinkFetcher fakeLinkFetcher() { return new FakeLinkFetcher(); }
    }

    @Autowired FakeExtractor extractor;
    @Autowired IntakeService service;
    @Autowired IntakeProcessor processor;
    @Autowired IntakeRepository intakes;
    @Autowired JobRepository jobs;
    @Autowired JobDescriptionRepository descriptions;
    @Autowired JobController jobController;

    private static ExtractedJd jd(String company, String role) {
        return new ExtractedJd(true, company, role, "Pune, India", "Hybrid", "FullTime", "3-5 years", null,
                List.of("Java", "Spring Boot"), "REQ-" + company.hashCode(), "Company Website", null);
    }

    @BeforeEach
    void reset() {
        extractor.answer = null;
        extractor.failure = null;
        extractor.lastText = null;
        extractor.lastImage = null;
    }

    private Intake run(Intake i) {
        processor.process(i.getId());
        return intakes.findById(i.getId()).orElseThrow();
    }

    private Job jobOf(String company) {
        return jobs.findAll().stream().filter(j -> company.equals(j.getCompany())).findFirst().orElseThrow();
    }

    @Test
    void pastedTextBecomesAToApplyJobWithItsDescription() {
        extractor.answer = jd("Nimbus Labs", "Platform Engineer");
        Intake i = run(service.addText("Nimbus Labs is hiring a Platform Engineer in Pune ..."));
        assertEquals(Intake.Status.DONE, i.getStatus());
        Job job = jobOf("Nimbus Labs");
        assertEquals(JobStatus.TO_APPLY, job.getStatus());
        assertEquals("Platform Engineer", job.getRole());
        assertEquals("Hybrid", job.getWorkMode());
        assertEquals(List.of("Java", "Spring Boot"), job.getSkills());
        assertEquals("TEXT", job.getAddedFrom());
        assertEquals(job.getId(), i.getJobId());
        assertTrue(descriptions.findById(job.getId()).orElseThrow().getText().startsWith("Nimbus Labs is hiring"));
    }

    @Test
    void aSingleWebAddressIsTreatedAsALink() {
        extractor.answer = jd("Orbit Works", "Data Engineer");
        Intake queued = service.addText("  https://www.linkedin.com/jobs/view/12345  ");
        assertEquals(Intake.Kind.LINK, queued.getKind());
        run(queued);
        Job job = jobOf("Orbit Works");
        assertEquals("LINK", job.getAddedFrom());
        assertEquals("https://www.linkedin.com/jobs/view/12345", job.getLink());
        assertEquals("LinkedIn", job.getPlatform()); // known board wins over the model's guess
        assertTrue(extractor.lastText.contains("hiring a backend engineer")); // Claude was given the page text
    }

    @Test
    void textThatMerelyContainsALinkStaysText() {
        assertEquals(Intake.Kind.TEXT, service.addText("Apply here https://example.com/jobs/1 before Friday").getKind());
    }

    @Test
    void anImageIsSavedAndHandedToTheExtractorWhichSuppliesTheDescription() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0, 1, 2, 3};
        ExtractedJd fromImage = new ExtractedJd(true, "Quartz Systems", "UI Developer", "Remote", "Remote", null, null, null,
                List.of("React"), null, "LinkedIn", "Quartz Systems is looking for a UI Developer ...");
        extractor.answer = fromImage;
        Intake queued = service.addImage(png, "screenshot.png");
        assertEquals(Intake.Kind.IMAGE, queued.getKind());
        assertTrue(Path.of(queued.getImagePath()).toFile().exists());
        run(queued);
        assertEquals(Path.of(queued.getImagePath()), extractor.lastImage);
        Job job = jobOf("Quartz Systems");
        assertEquals("IMAGE", job.getAddedFrom());
        assertTrue(descriptions.findById(job.getId()).orElseThrow().getText().contains("looking for a UI Developer"));
        service.dismiss(queued.getId());
        assertFalse(Path.of(queued.getImagePath()).toFile().exists());
    }

    @Test
    void onlyRealImagesAreAccepted() {
        assertThrows(ResponseStatusException.class, () -> service.addImage("MZ this is an exe pretending".getBytes(), "evil.png"));
        assertThrows(ResponseStatusException.class, () -> service.addImage(new byte[3], "tiny.png"));
        assertThrows(ResponseStatusException.class, () -> service.addText("   "));
    }

    @Test
    void theSameJobTwiceIsNotAddedTwice() {
        extractor.answer = jd("Dupe Corp Inc", "Backend Developer");
        run(service.addText("first copy of the posting"));
        long before = jobs.count();
        extractor.answer = jd("DUPE CORP", "backend developer"); // spelled differently
        Intake second = run(service.addText("second copy of the posting"));
        assertEquals(Intake.Status.DUPLICATE, second.getStatus());
        assertTrue(second.getMessage().contains("Already in your list"));
        assertEquals(before, jobs.count());
        assertEquals(jobOf("Dupe Corp Inc").getId(), second.getJobId());
    }

    @Test
    void somethingThatIsNotAJobPostingFailsWithAReason() {
        extractor.answer = new ExtractedJd(false, null, null, null, null, null, null, null, List.of(), null, null, null);
        Intake i = run(service.addText("Please log in to continue"));
        assertEquals(Intake.Status.FAILED, i.getStatus());
        assertTrue(i.getMessage().contains("does not look like a job description"));
    }

    @Test
    void aPostingWithoutCompanyOrRoleFailsInsteadOfAddingAHalfEmptyRow() {
        // other tests may have left an "Acme" job in the shared test database: what matters is that none was added
        long before = jobs.findAll().stream().filter(j -> "Acme".equals(j.getCompany())).count();
        extractor.answer = new ExtractedJd(true, "Acme", null, null, null, null, null, null, List.of(), null, null, null);
        Intake i = run(service.addText("Acme is hiring"));
        assertEquals(Intake.Status.FAILED, i.getStatus());
        assertEquals(before, jobs.findAll().stream().filter(j -> "Acme".equals(j.getCompany())).count());
    }

    @Test
    void failureReasonIsShownAndRetryQueuesItAgain() {
        extractor.failure = new ExtractionException("Claude could not read it: Credit balance is too low");
        Intake failed = run(service.addText("Some posting text"));
        assertEquals(Intake.Status.FAILED, failed.getStatus());
        assertTrue(failed.getMessage().contains("Credit balance is too low"));

        extractor.failure = null;
        extractor.answer = jd("Retry Works", "SRE");
        assertEquals(Intake.Status.PENDING, service.retry(failed.getId()).getStatus());
        assertEquals(Intake.Status.DONE, run(failed).getStatus());
        assertEquals("SRE", jobOf("Retry Works").getRole());
    }

    @Test
    void activeListHidesFinishedItemsButKeepsWaitingFailedAndDuplicates() {
        extractor.answer = jd("Listed Co", "Dev");
        Intake done = run(service.addText("a posting that will succeed"));
        Intake waiting = service.addText("a posting that is still waiting");
        List<Long> ids = service.active().stream().map(Intake::getId).toList();
        assertFalse(ids.contains(done.getId()));
        assertTrue(ids.contains(waiting.getId()));
    }

    @Test
    void youCanChangeAStatusAndDeleteAJob() {
        extractor.answer = jd("Status Co", "Tester");
        run(service.addText("posting for status test"));
        Job job = jobOf("Status Co");
        assertEquals(JobStatus.APPLIED, jobController.updateStatus(job.getId(), new JobController.UpdateStatusRequest(JobStatus.APPLIED)).getStatus());
        jobController.delete(job.getId());
        assertTrue(jobs.findById(job.getId()).isEmpty());
        assertTrue(descriptions.findById(job.getId()).isEmpty());
    }
}
