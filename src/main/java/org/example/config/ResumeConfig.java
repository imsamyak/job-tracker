package org.example.config;

import org.example.service.generator.ResumeGeneratorService;
import org.example.service.generator.ResumePdfRenderer;
import org.example.service.generator.SkillSelector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;

@Configuration
@EnableScheduling
public class ResumeConfig {

    @Bean
    public SkillSelector skillSelector() {
        return SkillSelector.load();
    }

    @Bean
    public ResumePdfRenderer resumePdfRenderer(@Value("${jtraker.resume.font-dir}") Path fontDir) {
        return new ResumePdfRenderer(fontDir);
    }

    @Bean
    public ResumeGeneratorService resumeGeneratorService(SkillSelector skillSelector, ResumePdfRenderer renderer) {
        return new ResumeGeneratorService(skillSelector, renderer);
    }
}
