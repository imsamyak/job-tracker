package org.example.service.rules;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleExtractorTest {

    private final RuleExtractor extractor = new RuleExtractor(KeywordRules.load(null));

    @Test
    void javaIsNotMistakenForJavascriptAndStacksAreOrderedByEmphasis() {
        var r = extractor.extract("""
                Backend Engineer
                We use Java, Spring Boot and Hibernate on AWS Lambda. Some JavaScript for tooling.
                Spring Boot experience is a must; Java 17 preferred.
                """);

        assertEquals("java", r.stacks().get(0));
        assertTrue(r.skills().contains("Java"));
        assertTrue(r.skills().contains("JavaScript"));
    }

    @Test
    void readsExperienceRangeAndSingleMinimum() {
        assertEquals(3, extractor.extract("Requires 3-5 years of experience in backend.").experienceMin());
        assertEquals(5, extractor.extract("Requires 3-5 years of experience in backend.").experienceMax());
        var single = extractor.extract("Minimum 2+ years of professional experience.");
        assertEquals(2, single.experienceMin());
        assertNull(single.experienceMax());
    }

    @Test
    void ignoresYearsThatAreNotAboutExperience() {
        assertEquals(0, extractor.extract("We were founded 12 years ago and have grown fast.").experienceMin());
    }

    @Test
    void modeAndTypeFollowTheKeywords() {
        assertEquals("Hybrid", extractor.extract("This is a hybrid role, remote two days.").mode());
        assertEquals("Remote", extractor.extract("Fully remote position.").mode());
        assertEquals("Onsite", extractor.extract("No mention of place.").mode());
        assertEquals("Internship", extractor.extract("Software Engineering Intern").type());
        assertEquals("FullTime", extractor.extract("Software Engineer").type());
    }

    @Test
    void findsAJobIdOnlyWhenItContainsADigit() {
        assertEquals("R-92757", extractor.extract("Job ID: R-92757\nBengaluru").jobId());
        assertNull(extractor.extract("Job ID: pending").jobId());
    }

    @Test
    void scorerTreatsIdenticalSetsAsAPerfectMatch() {
        var rules = KeywordRules.load(null);
        var scorer = new ExtractionScorer(rules);
        var rule = extractor.extract("Java and Spring Boot with Kafka. Java 17. Spring Boot. 3 years of experience.");
        var claude = new ExtractionScorer.Facts(null, null, "FullTime", "Onsite", null, 3, null,
                List.of("java"), List.of("Java", "Spring Boot", "Kafka"));

        var scores = scorer.score(rule, claude);

        assertEquals(1.0, scores.stream().filter(s -> s.field().equals("skills")).findFirst().orElseThrow().score());
        assertEquals(1.0, scores.stream().filter(s -> s.field().equals("experience_min")).findFirst().orElseThrow().score());
    }
}
