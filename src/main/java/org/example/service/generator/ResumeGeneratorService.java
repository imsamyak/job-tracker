package org.example.service.generator;

import org.example.domain.entity.Job;
import org.example.service.Referral;

import java.nio.file.Path;
import java.util.Locale;

/**
 * Builds the tailored resume PDF for a job: picks the skills with {@link SkillSelector}, then renders
 * the PDF with {@link ResumePdfRenderer}.
 */
public class ResumeGeneratorService {

    /** The resume file is always called this, whatever the job. */
    public static final String RESUME_FILE_NAME = "Resume_Samyak_Jain.pdf";

    /** Always used for the normal (automatic) resume - no per-job override; see {@link #generateReferral}
     *  for the one case that uses a different, fixed email. */
    public static final String DEFAULT_EMAIL = "imsamyak18@gmail.com";

    private final SkillSelector skillSelector;
    private final ResumePdfRenderer renderer;

    public ResumeGeneratorService(SkillSelector skillSelector, ResumePdfRenderer renderer) {
        this.skillSelector = skillSelector;
        this.renderer = renderer;
    }

    /**
     * @param jobFolder the folder of this job, created when missing
     * @return the path of the written resume
     */
    public Path generate(Job job, Path jobFolder) {
        return render(job, jobFolder.resolve(RESUME_FILE_NAME), DEFAULT_EMAIL);
    }

    /**
     * The referral variant - identical resume (same designation logic, same skills selection) except for
     * the email, and saved under a {@code referral/} subfolder rather than replacing the normal resume.
     * Lazy - only ever called when explicitly asked for (see {@code ReferralService}), never automatic.
     */
    public Path generateReferral(Job job, Path jobFolder) {
        Path referralResume = jobFolder.resolve(Referral.SUBFOLDER).resolve(RESUME_FILE_NAME);
        return render(job, referralResume, Referral.EMAIL);
    }

    private Path render(Job job, Path outputPdf, String email) {
        SkillSelector.Selection selection = skillSelector.select(job.getStacks(), job.getSkills());
        renderer.render(selection.sections(), job.getStacks(), designationFor(job), email, outputPdf);
        return outputPdf;
    }

    /** "Associate Software Engineer" when the TARGET job's own role mentions "Associate", else plain
     *  "Software Engineer" - this is the candidate's own title at Compro Technologies, tailored to read
     *  naturally against the seniority level of the role being applied to. Same for the referral variant. */
    private static String designationFor(Job job) {
        String role = job.getRole();
        boolean associate = role != null && role.toLowerCase(Locale.ROOT).contains("associate");
        return associate ? "Associate Software Engineer" : "Software Engineer";
    }
}
