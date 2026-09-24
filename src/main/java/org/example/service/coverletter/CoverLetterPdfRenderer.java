package org.example.service.coverletter;

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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders the cover letter PDF: template.html with the date/greeting/paragraphs filled in, styled by
 * style.css. Both files come from the classpath ({@code templates/coverletter}) - deliberately separate
 * from the resume's own template/style, even though they share the same header treatment, so tuning one
 * never risks the other.
 */
public class CoverLetterPdfRenderer {

    private static final String TEMPLATE = "/templates/coverletter/template.html";
    private static final String STYLE = "/templates/coverletter/style.css";
    private static final String FONT_FAMILY = "Segoe UI";
    private static final int MAX_PAGES = 1;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MMMM d, yyyy");

    /** file name -> {weight, italic} for the Segoe UI files in the font directory - same set the resume uses. */
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
    public CoverLetterPdfRenderer(Path fontDir) {
        this.template = readResource(TEMPLATE);
        this.style = readResource(STYLE);
        this.fontDir = fontDir;
    }

    /**
     * @param company    used for the "Dear X Hiring Team," greeting - falls back to a plain greeting if blank
     * @param paragraphs the letter body, one {@code <p>} per entry, in order
     * @param email      shown (and used as the mailto: link) in the header contacts line
     * @throws IllegalStateException if the rendered letter does not fit on one page - shouldn't normally
     *                                happen with 3-4 short paragraphs, but this is the same non-negotiable
     *                                guarantee the resume renderer makes, kept for consistency
     */
    public void render(String company, List<String> paragraphs, String email, Path outputPdf) {
        Document doc = Jsoup.parse(template);
        inlineStyle(doc);
        setEmail(doc, email);

        Element dateEl = doc.getElementById("date");
        if (dateEl == null) {
            throw new IllegalStateException("template.html has no element with id=\"date\"");
        }
        dateEl.text(LocalDate.now().format(DATE_FORMAT));

        Element greetingEl = doc.getElementById("greeting");
        if (greetingEl == null) {
            throw new IllegalStateException("template.html has no element with id=\"greeting\"");
        }
        greetingEl.text((company == null || company.isBlank() ? "Dear Hiring Team," : "Dear " + company.strip() + " Hiring Team,"));

        Element bodyEl = doc.getElementById("body");
        if (bodyEl == null) {
            throw new IllegalStateException("template.html has no element with id=\"body\"");
        }
        bodyEl.empty();
        for (String paragraph : paragraphs) {
            bodyEl.appendElement("p").text(paragraph);
        }

        byte[] pdf = renderToBytes(doc);
        int pages = countPages(pdf);
        if (pages > MAX_PAGES) {
            throw new IllegalStateException(
                    "Cover letter does not fit on one page (" + pages + " pages); shorten the paragraphs");
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
            throw new IllegalStateException("Could not render the cover letter PDF: " + e.getMessage(), e);
        }
    }

    private int countPages(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return doc.getNumberOfPages();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read back the rendered PDF", e);
        }
    }

    private void setEmail(Document doc, String email) {
        Element el = doc.getElementById("email-link");
        if (el == null) {
            throw new IllegalStateException("template.html has no element with id=\"email-link\"");
        }
        el.text(email);
        el.attr("href", "mailto:" + email);
    }

    private void inlineStyle(Document doc) {
        doc.select("link[rel=stylesheet]").remove();
        Element styleTag = doc.head().appendElement("style");
        styleTag.appendChild(new DataNode(style));
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
        try (InputStream in = CoverLetterPdfRenderer.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
