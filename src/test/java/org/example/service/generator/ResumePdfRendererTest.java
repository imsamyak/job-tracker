package org.example.service.generator;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResumePdfRendererTest {

    private static final Path FONT_DIR = Path.of("C:/Windows/Fonts");

    @TempDir
    Path tmp;

    @Test
    void rendersAOnePageResumeWithTheChosenSkills() throws IOException {
        List<String> stacks = List.of("java", "react");
        var selection = SkillSelector.load().select(stacks, List.of("Java", "React", "Kafka"));
        Path pdf = tmp.resolve("Resume_Samyak_Jain.pdf");

        new ResumePdfRenderer(FONT_DIR).render(selection.sections(), stacks, "Software Engineer",
                "imsamyak18@gmail.com", pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages(), "the resume must fit on one page");

            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.toUpperCase().contains("SAMYAK JAIN"), text);
            assertTrue(text.contains("Technical Skills"), text);
            assertTrue(text.contains("Messaging:"), text);
            assertTrue(text.contains("Kafka"), text);
            assertTrue(text.contains("React.js, Next.js, Redux"), text);
            // the old hand-written skills list must be gone
            assertTrue(!text.contains("Ngnix"), text);
            // java is one of the stacks, so the certification stays
            assertTrue(text.contains("Certifications"), text);
            // this is a heavy case (8 skill groups + certifications) - filler-bullet-2 (lowest priority,
            // dropped first) must be gone to keep it on one page, not silently left in causing an
            // overflow we didn't catch
            assertTrue(!text.contains("incident-investigation"), text);

            // Segoe UI is embedded when the machine has it (otherwise the CSS fallback fonts are used)
            if (Files.isRegularFile(FONT_DIR.resolve("segoeui.ttf"))) {
                var fonts = new java.util.ArrayList<String>();
                for (var name : doc.getPage(0).getResources().getFontNames()) {
                    fonts.add(doc.getPage(0).getResources().getFont(name).getName());
                }
                assertTrue(fonts.stream().anyMatch(n -> n.contains("Segoe")), "fonts used: " + fonts);
            }

            // a picture for a human to look at: target/test-output/resume-sample.png
            Path out = Path.of("target/test-output");
            Files.createDirectories(out);
            Files.copy(pdf, out.resolve("resume-sample.pdf"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0, 110), "png", out.resolve("resume-sample.png").toFile());
        }
    }

    @Test
    void dropsTheJavaCertificationWhenJavaIsNotOneOfTheStacks() throws IOException {
        List<String> stacks = List.of("react");
        var selection = SkillSelector.load().select(stacks, List.of("React"));
        Path pdf = tmp.resolve("Resume_Samyak_Jain.pdf");

        new ResumePdfRenderer(FONT_DIR).render(selection.sections(), stacks, "Software Engineer",
                "imsamyak18@gmail.com", pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(!text.contains("Certifications"), text);
            assertTrue(!text.contains("Wipro"), text);
        }
    }

    /**
     * The filler bullets are normally hidden and only kept when a resume is light enough to have room for
     * them - this is a light case (7 skill groups, no certifications) and must keep BOTH, filling more of
     * the page rather than leaving it blank.
     */
    @Test
    void keepsBothFillerBulletsOnALightResume() throws IOException {
        List<String> stacks = List.of("react", "node");
        var selection = SkillSelector.load().select(stacks, List.of("React", "Node.js"));
        Path pdf = tmp.resolve("Resume_Samyak_Jain.pdf");

        new ResumePdfRenderer(FONT_DIR).render(selection.sections(), stacks, "Software Engineer",
                "imsamyak18@gmail.com", pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            assertEquals(1, doc.getNumberOfPages());
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("incident-investigation"), text);
            assertTrue(text.contains("session-leakage"), text);
        }
    }

    @Test
    void usesAssociateSoftwareEngineerAsTheDesignationWhenAskedTo() throws IOException {
        List<String> stacks = List.of("java");
        var selection = SkillSelector.load().select(stacks, List.of("Java"));
        Path pdf = tmp.resolve("Resume_Samyak_Jain.pdf");

        new ResumePdfRenderer(FONT_DIR).render(selection.sections(), stacks, "Associate Software Engineer",
                "imsamyak18@gmail.com", pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("Associate Software Engineer"), text);
        }
    }

    @Test
    void usesTheGivenEmailInsteadOfTheDefault() throws IOException {
        List<String> stacks = List.of("java");
        var selection = SkillSelector.load().select(stacks, List.of("Java"));
        Path pdf = tmp.resolve("Resume_Samyak_Jain.pdf");

        new ResumePdfRenderer(FONT_DIR).render(selection.sections(), stacks, "Software Engineer",
                "custom.address@example.com", pdf);

        try (PDDocument doc = Loader.loadPDF(pdf.toFile())) {
            String text = new PDFTextStripper().getText(doc);
            assertTrue(text.contains("custom.address@example.com"), text);
            assertTrue(!text.contains("imsamyak18@gmail.com"), text);
        }
    }
}
