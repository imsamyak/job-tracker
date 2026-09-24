package org.example.service.generator;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.domain.constant.JobMode;
import org.example.domain.constant.JobStatus;
import org.example.domain.constant.JobType;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.Job;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the designation/email selection logic on top of ResumePdfRendererTest's rendering coverage. */
class ResumeGeneratorServiceTest {

    private static final Path FONT_DIR = Path.of("C:/Windows/Fonts");

    @TempDir
    Path tmp;

    private final ResumeGeneratorService service =
            new ResumeGeneratorService(SkillSelector.load(), new ResumePdfRenderer(FONT_DIR));

    private Job.JobBuilder job(String role) {
        return Job.builder()
                .company("Acme").role(role).folderName("Acme_" + role)
                .type(JobType.FullTime).mode(JobMode.Remote)
                .experience(new JobExperienceRequirement(2, 5))
                .skills(List.of("Java")).stacks(List.of("java")).contacts(List.of())
                .description("desc").status(JobStatus.NEW);
    }

    private String textOf(Path pdf) throws IOException {
        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    @Test
    void usesAssociateSoftwareEngineerWhenTheTargetRoleMentionsAssociate() throws IOException {
        Job job = job("Associate Software Engineer - Full Stack").build();

        Path resume = service.generate(job, tmp);

        assertTrue(textOf(resume).contains("Associate Software Engineer"));
    }

    @Test
    void usesPlainSoftwareEngineerOtherwise() throws IOException {
        Job job = job("Backend Engineer").build();

        Path resume = service.generate(job, tmp);

        String text = textOf(resume);
        assertTrue(text.contains("Software Engineer"));
        assertTrue(!text.contains("Associate Software Engineer"));
    }

    @Test
    void usesTheDefaultEmailForTheNormalResume() throws IOException {
        Job job = job("Backend Engineer").build();

        Path resume = service.generate(job, tmp);

        assertTrue(textOf(resume).contains(ResumeGeneratorService.DEFAULT_EMAIL));
    }

    @Test
    void generateReferralUsesTheReferralEmailAndSavesUnderAReferralSubfolder() throws IOException {
        Job job = job("Backend Engineer").build();

        Path resume = service.generateReferral(job, tmp);

        assertTrue(resume.toString().replace('\\', '/').endsWith("referral/" + ResumeGeneratorService.RESUME_FILE_NAME));
        String text = textOf(resume);
        assertTrue(text.contains(org.example.service.Referral.EMAIL));
        assertTrue(!text.contains(ResumeGeneratorService.DEFAULT_EMAIL));
    }
}
