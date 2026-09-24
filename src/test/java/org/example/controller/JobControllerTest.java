package org.example.controller;

import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.example.respository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against an in-memory database. {@code spring.generated.path} is redirected to a JUnit temp
 * directory via {@link DynamicPropertySource} - without this, the "deleteFolder=true" tests would
 * operate on the real project's ./generated folder, since that's {@code JobController}'s default.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:job-controller-test;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "jtraker.resume.schedule-initial-delay=PT1H",
        "jtraker.resume.schedule-delay=PT1H"
})
class JobControllerTest {

    @TempDir
    static Path generatedFolder;

    @DynamicPropertySource
    static void generatedPath(DynamicPropertyRegistry registry) {
        registry.add("spring.generated.path", () -> generatedFolder.toString());
    }

    @Autowired
    JobRepository jobs;

    @Autowired
    JobController controller;

    @BeforeEach
    void clean() {
        jobs.deleteAll();
    }

    private Job save(String company) {
        Job job = Job.builder()
                .company(company)
                .role("Engineer")
                .folderName(company + "_Engineer")
                .type(JobType.FullTime)
                .mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java"))
                .stacks(List.of("java"))
                .contacts(List.of())
                .description("desc")
                .status(JobStatus.NEW)
                .build();
        return jobs.save(job);
    }

    private Path createGeneratedFolder(Job job) throws Exception {
        Path folder = generatedFolder.resolve(job.getFolderName());
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("Resume_Samyak_Jain.pdf"), "not really a pdf");
        return folder;
    }

    @Test
    void deletesTheJobRecordAndReturns204() {
        Job job = save("Acme");

        var response = controller.delete(job.getId(), false);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(jobs.findById(job.getId()).isEmpty());
    }

    @Test
    void deletingAnUnknownIdReturns404AndChangesNothing() {
        save("Acme");

        var response = controller.delete(UUID.randomUUID(), false);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals(1, jobs.count());
    }

    @Test
    void byDefaultDeletingDoesNotTouchTheGeneratedFolder() throws Exception {
        Job job = save("Acme");
        Path folder = createGeneratedFolder(job);

        controller.delete(job.getId(), false);

        assertTrue(Files.isRegularFile(folder.resolve("Resume_Samyak_Jain.pdf")),
                "deleting the tracker entry must not remove the generated files unless asked to");
    }

    @Test
    void deleteFolderTrueRemovesTheGeneratedFolderToo() throws Exception {
        Job job = save("Beta");
        Path folder = createGeneratedFolder(job);

        var response = controller.delete(job.getId(), true);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertFalse(Files.exists(folder), "deleteFolder=true must remove the job's own folder");
    }

    @Test
    void deleteFolderTrueOnAMissingFolderStillDeletesTheJobRecord() {
        Job job = save("Gamma"); // no folder ever created on disk for this one

        var response = controller.delete(job.getId(), true);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertTrue(jobs.findById(job.getId()).isEmpty());
    }

    @Test
    void markingAJobAppliedSetsTheFlagAndTimestamp() {
        Job job = save("Delta");

        var response = controller.setApplied(job.getId(), true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().applied());
        assertTrue(response.getBody().appliedAt() != null);
        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertTrue(reloaded.isApplied());
    }

    @Test
    void unmarkingAppliedClearsTheTimestamp() {
        Job job = save("Epsilon");
        controller.setApplied(job.getId(), true);

        var response = controller.setApplied(job.getId(), false);

        assertFalse(response.getBody().applied());
        assertEquals(null, response.getBody().appliedAt());
    }

    @Test
    void settingAppliedOnAnUnknownIdReturns404() {
        var response = controller.setApplied(UUID.randomUUID(), true);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void correctingTheJobIdSavesTheNewValue() {
        Job job = save("Nu");

        var response = controller.setJobId(job.getId(), "R-999");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        Job reloaded = jobs.findById(job.getId()).orElseThrow();
        assertEquals("R-999", reloaded.getJobId());
        // Correcting the job id must never rename the folder - that folder may already hold real files.
        assertEquals("Nu_Engineer", reloaded.getFolderName());
    }

    @Test
    void clearingTheJobIdSetsItToNull() {
        Job job = save("Xi");
        controller.setJobId(job.getId(), "R-1");

        controller.setJobId(job.getId(), "");

        assertEquals(null, jobs.findById(job.getId()).orElseThrow().getJobId());
    }

    @Test
    void settingJobIdOnAnUnknownIdReturns404() {
        var response = controller.setJobId(UUID.randomUUID(), "R-1");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void generateReferralResumeOnAnUnknownIdReturns404() {
        var response = controller.generateReferralResume(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void generateReferralCoverLetterOnAnUnknownIdReturns404() {
        var response = controller.generateReferralCoverLetter(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadReferralResumeBeforeGenerationReturns404() {
        Job job = save("Rho");

        var response = controller.downloadReferralResume(job.getId());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    /**
     * Safe to run against the real, Spring-wired ReferralService: resume generation has no Claude call in
     * it at all (unlike the cover letter side, which ReferralServiceTest covers with a fake writer
     * instead). This is also the regression test for the LazyInitializationException this endpoint threw
     * once already (see JobController.generateReferralResume's own note).
     */
    @Test
    void generatingTheReferralResumeMarksItPendingThenEventuallyReady() throws Exception {
        Job job = save("Sigma");

        var response = controller.generateReferralResume(job.getId());

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("PENDING", response.getBody().referralResumeStatus());

        // The background thread pool renders the PDF asynchronously - give it a moment, then check it landed.
        for (int i = 0; i < 50 && !"READY".equals(jobs.findById(job.getId()).orElseThrow().getReferralResumeStatus().name()); i++) {
            Thread.sleep(100);
        }
        assertEquals("READY", jobs.findById(job.getId()).orElseThrow().getReferralResumeStatus().name());
    }

    @Test
    void getReturnsTheFullDetailForAKnownJob() {
        Job job = save("Zeta");

        var response = controller.get(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Zeta", response.getBody().company());
        assertEquals(List.of("Java"), response.getBody().skills());
    }

    @Test
    void getOnAnUnknownIdReturns404() {
        var response = controller.get(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listFilesReturnsWhatsActuallyOnDisk() throws Exception {
        Job job = save("Theta");
        createGeneratedFolder(job);

        var response = controller.listFiles(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("Resume_Samyak_Jain.pdf", response.getBody().get(0).name());
        assertTrue(response.getBody().get(0).sizeBytes() > 0);
    }

    @Test
    void listFilesOnAJobWithNoFolderYetReturnsAnEmptyList() {
        Job job = save("Iota"); // no folder ever created

        var response = controller.listFiles(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void listFilesOnAnUnknownIdReturns404() {
        var response = controller.listFiles(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadFileReturnsTheRequestedFilesBytes() throws Exception {
        Job job = save("Kappa");
        createGeneratedFolder(job);

        var response = controller.downloadFile(job.getId(), "Resume_Samyak_Jain.pdf");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contentLength() > 0);
    }

    @Test
    void downloadFileRefusesToEscapeTheJobsOwnFolder() throws Exception {
        Job job = save("Lambda");
        createGeneratedFolder(job);

        var response = controller.downloadFile(job.getId(), "../../pom.xml");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void downloadFileOnAMissingFileReturns404() {
        Job job = save("Mu"); // no folder ever created

        var response = controller.downloadFile(job.getId(), "Resume_Samyak_Jain.pdf");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // Deliberately NOT testing the happy path here: it would go through the real, Spring-wired
    // CoverLetterService (real claude -p writer) since this test class autowires the real JobController.
    // See CoverLetterServiceTest for the happy/failure paths, built with a fake writer instead.
    @Test
    void generateCoverLetterOnAnUnknownIdReturns404() {
        var response = controller.generateCoverLetter(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void folderPathOnAnUnknownIdReturns404() {
        var response = controller.folderPath(UUID.randomUUID());

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void folderPathReturnsTheAbsolutePathEvenBeforeTheFolderExistsOnDisk() {
        Job job = save("Omicron");

        var response = controller.folderPath(job.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(generatedFolder.resolve(job.getFolderName()).toAbsolutePath().toString(), response.getBody());
    }

    // Deliberately NOT testing the happy path here - same reason as generateCoverLetterOnAnUnknownIdReturns404
    // above: it would shell out to the real claude -p writer. See JobHelpServiceTest for the happy/failure
    // paths, built with a fake writer instead.
    @Test
    void helpOnAnUnknownIdReturns404() {
        var response = controller.help(UUID.randomUUID(), "Why do you want to work here?");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void helpWithABlankQuestionReturnsBadRequest() {
        Job job = save("Pi");

        var response = controller.help(job.getId(), "   ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
