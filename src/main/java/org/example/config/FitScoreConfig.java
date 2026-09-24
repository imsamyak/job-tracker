package org.example.config;

import org.example.service.FitScoreService;
import org.example.service.fit.ClaudeFitScoreCalculator;
import org.example.service.fit.FitScoreCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class FitScoreConfig {

    @Bean
    public FitScoreCalculator fitScoreCalculator(@Value("${jtraker.claude.executable}") Path executable,
                                                 @Value("${jtraker.claude.model}") String model,
                                                 @Value("${jtraker.claude.max-budget-usd}") double maxBudgetUsd,
                                                 @Value("${jtraker.claude.timeout}") Duration timeout) {
        return new ClaudeFitScoreCalculator(executable, model, maxBudgetUsd, timeout);
    }

    /** Shared with {@code FitScoreBackfillRunner} - see that class for why the backfill exists at all. */
    @Bean
    public FitScoreService fitScoreService(FitScoreCalculator fitScoreCalculator) {
        return new FitScoreService(fitScoreCalculator);
    }
}
