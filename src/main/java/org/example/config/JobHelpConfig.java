package org.example.config;

import org.example.service.help.ClaudeJobHelpWriter;
import org.example.service.help.JobHelpWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.time.Duration;

@Configuration
public class JobHelpConfig {

    @Bean
    public JobHelpWriter jobHelpWriter(@Value("${jtraker.claude.executable}") Path executable,
                                       @Value("${jtraker.claude.model}") String model,
                                       @Value("${jtraker.claude.max-budget-usd}") double maxBudgetUsd,
                                       @Value("${jtraker.claude.timeout}") Duration timeout) {
        return new ClaudeJobHelpWriter(executable, model, maxBudgetUsd, timeout);
    }
}
