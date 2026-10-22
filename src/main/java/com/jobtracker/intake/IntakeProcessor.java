package com.jobtracker.intake;

import com.jobtracker.job.Job;
import com.jobtracker.job.JobDescription;
import com.jobtracker.job.JobDescriptionRepository;
import com.jobtracker.job.JobRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns one intake into a job: read the posting, check it is a real posting, skip it if the job is
 * already in the list, otherwise add it with status Pending (stored as TO_APPLY).
 */
@Service
public class IntakeProcessor {
    private static final Logger log = LoggerFactory.getLogger(IntakeProcessor.class);
    private static final int MAX_DESCRIPTION_CHARS = 40_000;

    private final IntakeRepository intakes;
    private final JobRepository jobs;
    private final JobDescriptionRepository descriptions;
    private final JdExtractor extractor;
    private final LinkFetcher links;

    public IntakeProcessor(IntakeRepository intakes, JobRepository jobs, JobDescriptionRepository descriptions,
                           JdExtractor extractor, LinkFetcher links) {
        this.intakes = intakes;
        this.jobs = jobs;
        this.descriptions = descriptions;
        this.extractor = extractor;
        this.links = links;
    }

    /** Never throws: whatever goes wrong is recorded on the intake so the user sees it. */
    public void process(Long intakeId) {
        Intake intake = intakes.findById(intakeId).orElse(null);
        if (intake == null) return;
        try {
            run(intake);
        } catch (ExtractionException e) {
            fail(intake, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Intake {} failed unexpectedly", intakeId, e);
            fail(intake, "Something went wrong while reading it: " + e.getMessage());
        }
    }

    private void run(Intake intake) throws ExtractionException {
        String text;
        String link = null;
        String pagePlatform = null;
        ExtractedJd jd;

        switch (intake.getKind()) {
            case IMAGE -> {
                text = null;
                jd = extractor.extract(null, Path.of(intake.getImagePath()));
            }
            case LINK -> {
                LinkFetcher.Page page = links.fetch(intake.getText());
                link = page.url();
                pagePlatform = page.platform();
                text = page.text();
                jd = extractor.extract(text, null);
            }
            default -> {
                text = intake.getText();
                jd = extractor.extract(text, null);
            }
        }

        if (!jd.isJobPosting()) {
            throw new ExtractionException("That does not look like a job description.");
        }
        if (jd.company() == null || jd.role() == null) {
            throw new ExtractionException("Could not find the company and the role in it. Try pasting the full posting.");
        }

        Job existing = findDuplicate(jd);
        if (existing != null) {
            intake.setStatus(Intake.Status.DUPLICATE);
            intake.setJobId(existing.getId());
            intake.setMessage("Already in your list: " + existing.getCompany() + " - " + existing.getRole());
            intakes.save(intake);
            return;
        }

        Job job = new Job();
        job.setCompany(jd.company());
        job.setRole(jd.role());
        job.setLocation(jd.location());
        job.setWorkMode(jd.workMode());
        job.setJobType(jd.jobType());
        job.setExperience(jd.experience());
        job.setSalary(jd.salary());
        job.setPostingId(jd.postingId());
        job.setPlatform(pagePlatform != null ? pagePlatform : jd.platform());
        job.setLink(link);
        job.setAddedFrom(intake.getKind().name());
        job.setSkills(jd.skills());
        job.setAddedOn(LocalDate.now());
        job = jobs.save(job);

        String description = intake.getKind() == Intake.Kind.IMAGE ? jd.description() : text;
        if (description != null && !description.isBlank()) {
            descriptions.save(new JobDescription(job.getId(),
                    description.length() > MAX_DESCRIPTION_CHARS ? description.substring(0, MAX_DESCRIPTION_CHARS) : description));
        }

        intake.setStatus(Intake.Status.DONE);
        intake.setJobId(job.getId());
        intake.setMessage(null);
        intakes.save(intake);
    }

    /** Same company and role (ignoring case, spacing and "Inc/Ltd"); or the same posting id at the same company. */
    private Job findDuplicate(ExtractedJd jd) {
        String company = normalise(jd.company());
        String role = normalise(jd.role());
        List<Job> all = jobs.findAllByOrderByAddedOnDescIdDesc();
        for (Job j : all) {
            if (!normalise(j.getCompany()).equals(company)) continue;
            if (normalise(j.getRole()).equals(role)) return j;
            if (jd.postingId() != null && jd.postingId().equalsIgnoreCase(j.getPostingId())) return j;
        }
        return null;
    }

    static String normalise(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT)
                .replaceAll("\\b(inc|ltd|llc|pvt|private|limited|corp|corporation)\\b", "")
                .replaceAll("[^a-z0-9]", "");
    }

    private void fail(Intake intake, String message) {
        intake.setStatus(Intake.Status.FAILED);
        intake.setMessage(message.length() > 900 ? message.substring(0, 900) : message);
        intakes.save(intake);
    }
}
