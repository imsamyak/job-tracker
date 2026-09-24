package org.example.service.generator;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.DataNode;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Renders the resume PDF: template.html with the chosen skill sections put into its "Technical Skills"
 * block, styled by style.css. Both files come from the classpath ({@code templates/resume}).
 */
public class ResumePdfRenderer {

    private static final String TEMPLATE = "/templates/resume/template.html";
    private static final String STYLE = "/templates/resume/style.css";
    private static final String FONT_FAMILY = "Segoe UI";
    private static final int MAX_PAGES = 1;

    /** file name -> {weight, italic} for the Segoe UI files in the font directory. */
    private static final Object[][] FONT_FILES = {
            {"segoeui.ttf", 400, false},
            {"segoeuib.ttf", 700, false},
            {"segoeuii.ttf", 400, true},
            {"segoeuiz.ttf", 700, true},
    };

    private final String template;
    private final String style;
    private final Path fontDir;

    /** @param fontDir folder holding the Segoe UI files; when a file is missing the CSS fallback fonts are used */
    public ResumePdfRenderer(Path fontDir) {
        this.template = readResource(TEMPLATE);
        this.style = readResource(STYLE);
        this.fontDir = fontDir;
    }

    /**
     * Ids of the optional bullets, in the order they're dropped if needed to keep the resume on one page -
     * "filler-bullet-2" goes first (dropped before "filler-bullet"), so a resume with just enough spare
     * room keeps the higher-priority one. Each is real content split out of an existing bullet, not
     * invented text - see template.html.
     */
    private static final List<String> FILLER_BULLET_IDS = List.of("filler-bullet-2", "filler-bullet");

    /**
     * @param stacks      the job's stacks; the Java certification section is dropped unless "java" is one of them
     * @param designation shown as the Compro Technologies job title - e.g. "Software Engineer" or
     *                     "Associate Software Engineer"
     * @param email       shown (and used as the mailto: link) in the header contacts line
     * @throws IllegalStateException if the resume does not fit on one page even with every filler bullet
     *                                removed; nothing is written to {@code outputPdf} in that case, so a
     *                                failed job leaves no file behind
     */
    public void render(List<SkillSelector.ResumeSection> sections, List<String> stacks, String designation,
                       String email, Path outputPdf) {
        Document doc = Jsoup.parse(template);
        inlineStyle(doc);
        fillSkills(doc, sections);
        setDesignation(doc, designation);
        setEmail(doc, email);
        if (stacks == null || !stacks.contains("java")) {
            Element certifications = doc.getElementById("certifications");
            if (certifications != null) {
                certifications.remove();
            }
        }

        // Try with every filler bullet included first - a resume with lighter skills content has room for
        // them and reads more complete. Drop them one at a time, in FILLER_BULLET_IDS order, only as far
        // as needed to stop the fuller version spilling onto a second page.
        byte[] pdf = renderToBytes(doc);
        int pages = countPages(pdf);
        for (String fillerId : FILLER_BULLET_IDS) {
            if (pages <= MAX_PAGES) {
                break;
            }
            Element filler = doc.getElementById(fillerId);
            if (filler != null) {
                filler.remove();
                pdf = renderToBytes(doc);
                pages = countPages(pdf);
            }
        }

        if (pages > MAX_PAGES) {
            throw new IllegalStateException(
                    "Resume does not fit on one page (" + pages + " pages) even with every filler bullet removed; "
                            + "trim the skills or experience content");
        }

        try {
            Files.createDirectories(outputPdf.toAbsolutePath().getParent());
            Files.write(outputPdf, pdf);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write " + outputPdf, e);
        }
    }

    private byte[] renderToBytes(Document doc) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withW3cDocument(new W3CDom().fromJsoup(doc), "/");
            builder.toStream(buffer);
            builder.run();
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render the resume PDF: " + e.getMessage(), e);
        }
    }

    private int countPages(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read back the rendered PDF", e);
        }
    }

    private void inlineStyle(Document doc) {
        doc.select("link[rel=stylesheet]").remove();
        Element styleTag = doc.head().appendElement("style");
        styleTag.appendChild(new DataNode(style));
    }

    private void setDesignation(Document doc, String designation) {
        Element el = doc.getElementById("designation");
        if (el == null) {
            throw new IllegalStateException("template.html has no element with id=\"designation\"");
        }
        el.text(designation);
    }

    private void setEmail(Document doc, String email) {
        Element el = doc.getElementById("email-link");
        if (el == null) {
            throw new IllegalStateException("template.html has no element with id=\"email-link\"");
        }
        el.text(email);
        el.attr("href", "mailto:" + email);
    }

    private void fillSkills(Document doc, List<SkillSelector.ResumeSection> sections) {
        Element skills = doc.getElementById("skills");
        if (skills == null) {
            throw new IllegalStateException("template.html has no element with id=\"skills\"");
        }
        skills.empty();
        for (SkillSelector.ResumeSection section : sections) {
            Element row = skills.appendElement("div");
            row.appendElement("span").text(section.label() + ":");
            row.appendText(" " + String.join(", ", section.skills()));
        }
    }

    private void registerFonts(PdfRendererBuilder builder) {
        for (Object[] f : FONT_FILES) {
            Path file = fontDir.resolve((String) f[0]);
            if (Files.isRegularFile(file)) {
                builder.useFont(file.toFile(), FONT_FAMILY, (Integer) f[1],
                        (Boolean) f[2] ? BaseRendererBuilder.FontStyle.ITALIC : BaseRendererBuilder.FontStyle.NORMAL,
                        true);
            }
        }
    }

    private static String readResource(String path) {
        try (InputStream in = ResumePdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
