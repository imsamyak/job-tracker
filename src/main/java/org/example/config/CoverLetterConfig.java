package org.example.config;

import org.example.service.coverletter.ClaudeCoverLetterWriter;
import org.example.service.coverletter.CoverLetterGeneratorService;
import org.example.service.coverletter.CoverLetterPdfRenderer;
import org.example.service.coverletter.CoverLetterWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class CoverLetterConfig {

    @Bean
    public CoverLetterWriter coverLetterWriter(@Value("${jtraker.claude.executable}") Path executable,
                                               @Value("${jtraker.claude.model}") String model,
                                               @Value("${jtraker.claude.max-budget-usd}") double maxBudgetUsd,
                                               @Value("${jtraker.claude.timeout}") Duration timeout) {
        return new ClaudeCoverLetterWriter(executable, model, maxBudgetUsd, timeout);
    }

    @Bean
    public CoverLetterPdfRenderer coverLetterPdfRenderer(@Value("${jtraker.resume.font-dir}") Path fontDir) {
        return new CoverLetterPdfRenderer(fontDir);
    }

    @Bean
    public CoverLetterGeneratorService coverLetterGeneratorService(CoverLetterWriter writer,
                                                                    CoverLetterPdfRenderer renderer) {
        return new CoverLetterGeneratorService(writer, renderer);
    }
}
