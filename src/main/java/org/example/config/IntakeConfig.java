package org.example.config;

import org.example.respository.JobRepository;
import org.example.respository.PendingIntakeRepository;
import org.example.service.JobEventBroadcaster;
import org.example.service.fit.FitScoreCalculator;
import org.example.service.generator.SkillSelector;
import org.example.service.intake.ClaudeCliJdExtractor;
import org.example.service.intake.JdExtractor;
import org.example.service.intake.JobIntakeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class IntakeConfig {

    @Bean
    public JdExtractor jdExtractor(SkillSelector skillSelector,
                                   @Value("${jtraker.claude.executable}") Path executable,
                                   @Value("${jtraker.claude.model}") String model,
                                   @Value("${jtraker.claude.max-budget-usd}") double maxBudgetUsd,
                                   @Value("${jtraker.claude.timeout}") Duration timeout,
                                   @Value("${jtraker.claude.max-page-text-chars}") int maxPageTextChars,
                                   @Value("${jtraker.intake.mode:text}") String intakeMode) {
        return new ClaudeCliJdExtractor(executable, model, maxBudgetUsd, timeout, maxPageTextChars,
                skillSelector.knownStacks(), "text".equalsIgnoreCase(intakeMode));
    }

    @Bean
    public JobIntakeService jobIntakeService(JdExtractor jdExtractor, JobRepository jobRepository,
                                             PendingIntakeRepository pendingIntakeRepository,
                                             SkillSelector skillSelector, JobEventBroadcaster events,
                                             FitScoreCalculator fitScoreCalculator,
                                             org.example.service.rules.ExtractionPilotService pilot,
                                             @Value("${jtraker.intake.pending-dir}") Path pendingDir,
                                             @Value("${spring.generated.path}") Path generatedRoot) {
        JobIntakeService service = new JobIntakeService(jdExtractor, jobRepository, pendingIntakeRepository,
                skillSelector.knownStacks(), pendingDir, generatedRoot, events, fitScoreCalculator);
        service.setPilot(pilot);
        return service;
    }
}
