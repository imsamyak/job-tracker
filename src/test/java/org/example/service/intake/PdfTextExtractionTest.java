package org.example.service.intake;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The text path of intake depends on getting real text out of a posting's PDF - and on knowing when it can't. */
class PdfTextExtractionTest {

    @TempDir
    Path dir;

    private Path pdfWith(String text) throws Exception {
        Path file = dir.resolve("posting.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);
            if (text != null) {
                try (PDPageContentStream out = new PDPageContentStream(doc, page)) {
                    out.beginText();
                    out.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    out.newLineAtOffset(50, 700);
                    out.showText(text);
                    out.endText();
                }
            }
            doc.save(file.toFile());
        }
        return file;
    }

    @Test
    void extractsTheTextOfATextPdf() throws Exception {
        String text = ClaudeCliJdExtractor.pdfText(pdfWith("Senior Java Engineer at Acme - Spring Boot, Kafka"));

        assertTrue(text.contains("Senior Java Engineer at Acme"), text);
    }

    @Test
    void aPdfWithNoTextComesBackTooShortToTrust() throws Exception {
        String text = ClaudeCliJdExtractor.pdfText(pdfWith(null));

        assertTrue(text != null && text.length() < ClaudeCliJdExtractor.MIN_TEXT_CHARS,
                "an image-only PDF must fall below the threshold so intake falls back to reading the PDF");
    }

    @Test
    void anUnreadableFileReturnsNullInsteadOfThrowing() throws Exception {
        Path notAPdf = Files.writeString(dir.resolve("broken.pdf"), "this is not a pdf");

        assertNull(ClaudeCliJdExtractor.pdfText(notAPdf));
    }
}
