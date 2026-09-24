package org.example.service.coverletter;

import org.example.domain.entity.Job;
import org.example.service.CandidateProfile;
import org.example.service.Referral;
import org.example.service.generator.ResumeGeneratorService;

import java.nio.file.Path;
import java.util.List;

/**
 * Builds the tailored cover letter PDF for a job: writes the wording with {@link CoverLetterWriter}, then
 * renders it with {@link CoverLetterPdfRenderer}.
 */
public class CoverLetterGeneratorService {

    /** The cover letter file is always called this, whatever the job - matches Resume_Samyak_Jain.pdf. */
    public static final String COVER_LETTER_FILE_NAME = "Cover_Letter_Samyak_Jain.pdf";

    private final CoverLetterWriter writer;
    private final CoverLetterPdfRenderer renderer;

    public CoverLetterGeneratorService(CoverLetterWriter writer, CoverLetterPdfRenderer renderer) {
        this.writer = writer;
        this.renderer = renderer;
    }

    /**
     * @param jobFolder the folder of this job, created when missing
     * @return the path of the written cover letter
     */
    public Path generate(Job job, Path jobFolder) {
        return render(job, jobFolder.resolve(COVER_LETTER_FILE_NAME), ResumeGeneratorService.DEFAULT_EMAIL);
    }

    /**
     * The referral variant - a fresh tailored letter (its own claude -p call; wording doesn't depend on
     * email, but each variant is generated independently and lazily, same as the resume) with the
     * referral email instead, saved under a {@code referral/} subfolder. Lazy - only called when
     * explicitly asked for (see {@code ReferralService}).
     */
    public Path generateReferral(Job job, Path jobFolder) {
        Path referralLetter = jobFolder.resolve(Referral.SUBFOLDER).resolve(COVER_LETTER_FILE_NAME);
        return render(job, referralLetter, Referral.EMAIL);
    }

    private Path render(Job job, Path outputPdf, String email) {
        CoverLetterRequest request = new CoverLetterRequest(job.getCompany(), job.getRole(), job.getDescription(),
                job.getStacks(), job.getSkills(), CandidateProfile.text());
        List<String> paragraphs = writer.write(request);
        renderer.render(job.getCompany(), paragraphs, email, outputPdf);
        return outputPdf;
    }
}
