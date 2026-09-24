package org.example.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per (run, job, field): what the keyword rules extracted vs what Claude extracted, and how well
 * they agree. Never shown in the UI - read it in the H2 console. {@code runSource} is BACKTEST (rules run
 * over already-stored jobs) or SHADOW (rules run alongside every live Claude intake).
 */
@Entity
@Table(name = "extraction_comparison")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtractionComparison {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private String runId;

    @Column(name = "run_source", nullable = false)
    private String runSource;

    /** Null for a shadow comparison of a posting that was discarded as a duplicate and so has no job row. */
    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_label", length = 500)
    private String jobLabel;

    @Column(name = "field_name", nullable = false)
    private String fieldName;

    @Column(name = "rule_value", length = 2000)
    private String ruleValue;

    @Column(name = "claude_value", length = 2000)
    private String claudeValue;

    /** 1.0 = identical; for set-like fields the Jaccard overlap, for skills_recall/precision the ratio. */
    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private boolean matched;

    @Column(length = 2000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
