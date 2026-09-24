package org.example.service.rules;

import lombok.extern.slf4j.Slf4j;
import org.example.domain.embed.JobExperienceRequirement;
import org.example.domain.entity.ExtractionComparison;
import org.example.domain.entity.Job;
import org.example.respository.ExtractionComparisonRepository;
import org.example.respository.JobRepository;
import org.example.service.intake.ClaudeCliJdExtractor;
import org.example.service.intake.ExtractedJob;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The pilot that measures how close keyword rules get to Claude's extraction, so a field can be moved off
 * Claude only once the numbers justify it. Two ways in, both writing rows to {@code extraction_comparison}:
 * <ul>
 *   <li>{@link #backtest()} - rules over every job already stored (its saved JobDescription.pdf text) vs the
 *       fields Claude extracted back then. Costs nothing: no Claude call.</li>
 *   <li>{@link #recordShadow} - called by live intake right after Claude extracts a posting. Also free: the
 *       Claude answer already exists, the rules just run beside it.</li>
 * </ul>
 * Neither changes what intake saves.
 */
@Slf4j
@Service
public class ExtractionPilotService {

    public static final String BACKTEST = "BACKTEST";
    public static final String SHADOW = "SHADOW";

    public record FieldSummary(String field, long count, double avgScore, double matchRate) {
    }

    public record Report(String runId, int jobsScored, int jobsSkipped, List<FieldSummary> fields) {
    }

    private final JobRepository jobs;
    private final ExtractionComparisonRepository comparisons;
    private final TransactionTemplate tx;
    private final Path generatedRoot;
    private final boolean shadowEnabled;
    private final Path keywordsOverride;

    public ExtractionPilotService(JobRepository jobs, ExtractionComparisonRepository comparisons,
                                  TransactionTemplate tx,
                                  @Value("${spring.generated.path}") Path generatedRoot,
                                  @Value("${jtraker.pilot.shadow-rules:true}") boolean shadowEnabled,
                                  @Value("${jtraker.pilot.keywords-file:}") String keywordsFile) {
        this.jobs = jobs;
        this.comparisons = comparisons;
        this.tx = tx;
        this.generatedRoot = generatedRoot;
        this.shadowEnabled = shadowEnabled;
        this.keywordsOverride = keywordsFile == null || keywordsFile.isBlank() ? null : Path.of(keywordsFile);
    }

    /** Rules vs the stored Claude fields of every job that still has its JobDescription.pdf. */
    public Report backtest() {
        String runId = "backtest-" + Instant.now();
        KeywordRules rules = KeywordRules.load(keywordsOverride);
        RuleExtractor extractor = new RuleExtractor(rules);
        ExtractionScorer scorer = new ExtractionScorer(rules);
        int[] counts = new int[2]; // scored, skipped

        tx.executeWithoutResult(status -> {
            for (Job job : jobs.findAll()) {
                Path pdf = generatedRoot.resolve(job.getFolderName()).resolve("JobDescription.pdf");
                String text = Files.isRegularFile(pdf) ? ClaudeCliJdExtractor.pdfText(pdf) : null;
                if (text == null || text.isBlank()) {
                    counts[1]++;
                    continue;
                }
                record(runId, BACKTEST, job.getId(), label(job.getCompany(), job.getRole()),
                        extractor.extract(text), factsOf(job), scorer);
                counts[0]++;
            }
        });
        log.info("Rule backtest {}: {} jobs scored, {} skipped (no PDF/text)", runId, counts[0], counts[1]);
        return new Report(runId, counts[0], counts[1], summary(runId));
    }

    /** Live intake hook: rules vs the fields Claude just extracted from this posting. Never throws. */
    public void recordShadow(Path pdf, ExtractedJob claude) {
        if (!shadowEnabled) {
            return;
        }
        try {
            String text = ClaudeCliJdExtractor.pdfText(pdf);
            if (text == null || text.isBlank()) {
                return;
            }
            KeywordRules rules = KeywordRules.load(keywordsOverride);
            ExtractionScorer.Facts facts = new ExtractionScorer.Facts(claude.role(), claude.jobId(), claude.type(),
                    claude.mode(), claude.location(), claude.experienceMinYears(), claude.experienceMaxYears(),
                    claude.stacks(), claude.skills());
            record("shadow-" + UUID.randomUUID(), SHADOW, null, label(claude.company(), claude.role()),
                    new RuleExtractor(rules).extract(text), facts, new ExtractionScorer(rules));
        } catch (RuntimeException e) {
            log.warn("Shadow rule comparison failed (ignored): {}", e.getMessage());
        }
    }

    /** Per-field agreement for one run, or for every SHADOW row when {@code runId} is null and source is SHADOW. */
    public List<FieldSummary> summary(String runId) {
        return toSummary(comparisons.summarizeRun(runId));
    }

    public record Disagreement(String job, String rule, String claude, String detail) {
    }

    /** The rows that did NOT match for one field in the latest backtest - what to read when tuning the keywords. */
    public List<Disagreement> latestBacktestDisagreements(String field) {
        return comparisons.findFirstByRunSourceOrderByCreatedAtDesc(BACKTEST)
                .map(c -> comparisons.findByRunIdAndFieldNameAndMatchedFalse(c.getRunId(), field).stream()
                        .map(d -> new Disagreement(d.getJobLabel(), d.getRuleValue(), d.getClaudeValue(), d.getDetail()))
                        .toList())
                .orElse(List.of());
    }

    public List<FieldSummary> shadowSummary() {
        return toSummary(comparisons.summarizeSource(SHADOW));
    }

    public List<FieldSummary> latestBacktestSummary() {
        return comparisons.findFirstByRunSourceOrderByCreatedAtDesc(BACKTEST)
                .map(c -> summary(c.getRunId())).orElse(List.of());
    }

    private void record(String runId, String source, UUID jobId, String label, RuleExtractor.Result rule,
                        ExtractionScorer.Facts claude, ExtractionScorer scorer) {
        Instant now = Instant.now();
        for (var s : scorer.score(rule, claude)) {
            comparisons.save(ExtractionComparison.builder()
                    .runId(runId).runSource(source).jobId(jobId).jobLabel(label)
                    .fieldName(s.field()).ruleValue(cut(s.ruleValue())).claudeValue(cut(s.claudeValue()))
                    .score(s.score()).matched(s.matched()).detail(cut(s.detail())).createdAt(now)
                    .build());
        }
    }

    private static ExtractionScorer.Facts factsOf(Job job) {
        JobExperienceRequirement exp = job.getExperience();
        return new ExtractionScorer.Facts(job.getRole(), job.getJobId(),
                job.getType() == null ? null : job.getType().name(),
                job.getMode() == null ? null : job.getMode().name(), job.getLocation(),
                exp == null ? 0 : exp.getMin(), exp == null ? null : exp.getMax(),
                List.copyOf(job.getStacks()), List.copyOf(job.getSkills()));
    }

    private static List<FieldSummary> toSummary(List<Object[]> rows) {
        return rows.stream().map(r -> {
            long count = ((Number) r[1]).longValue();
            double avg = ((Number) r[2]).doubleValue();
            double matchRate = count == 0 ? 0 : ((Number) r[3]).doubleValue() / count;
            return new FieldSummary((String) r[0], count, round(avg), round(matchRate));
        }).toList();
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    private static String label(String company, String role) {
        return (company == null ? "?" : company) + " | " + (role == null ? "?" : role);
    }

    private static String cut(String s) {
        return s == null ? null : (s.length() > 1900 ? s.substring(0, 1900) : s);
    }
}
