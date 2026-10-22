package com.jobtracker.resume;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Keeps the single resume PDF on disk and reads its text. */
@Service
public class ResumeService {

    static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final ResumeRepository repo;
    private final Path dir;

    public ResumeService(ResumeRepository repo, @Value("${jobtracker.resume.dir:uploads/resume}") Path dir) {
        this.repo = repo;
        this.dir = dir;
    }

    public Optional<Resume> current() {
        return repo.findById(Resume.THE_ONE).filter(r -> Files.exists(path()));
    }

    /** Replaces the resume. Only a real PDF is accepted: the first bytes are checked, not the file name. */
    public Resume save(byte[] bytes, String originalName) {
        if (bytes == null || bytes.length == 0) throw new ResponseStatusException(BAD_REQUEST, "That file is empty.");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(BAD_REQUEST, "That file is too large (10 MB at most).");
        if (!startsWithPdfMagic(bytes)) throw new ResponseStatusException(BAD_REQUEST, "The resume has to be a PDF.");
        try {
            Files.createDirectories(dir);
            Files.write(path(), bytes);
        } catch (IOException e) {
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Could not save the resume: " + e.getMessage());
        }
        Resume r = repo.findById(Resume.THE_ONE).orElseGet(Resume::new);
        r.setFilename(cleanName(originalName));
        r.setSize(bytes.length);
        r.setUploadedAt(Instant.now());
        return repo.save(r);
    }

    public Path file() {
        if (current().isEmpty()) throw new ResponseStatusException(NOT_FOUND, "No resume has been added yet.");
        return path();
    }

    public void delete() {
        repo.deleteById(Resume.THE_ONE);
        try {
            Files.deleteIfExists(path());
        } catch (IOException ignored) {
            // a leftover file is harmless: it is not listed once the row is gone
        }
    }

    /** The resume's text, or an empty string for a scan with no text layer. */
    public String text() {
        try (PDDocument doc = Loader.loadPDF(file().toFile())) {
            return textWithLinks(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "That PDF could not be read: " + e.getMessage());
        }
    }

    /** The text of a PDF that is only being read, not kept. Same checks as when one is saved. */
    public String textOf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new ResponseStatusException(BAD_REQUEST, "That file is empty.");
        if (bytes.length > MAX_BYTES) throw new ResponseStatusException(BAD_REQUEST, "That file is too large (10 MB at most).");
        if (!startsWithPdfMagic(bytes)) throw new ResponseStatusException(BAD_REQUEST, "The resume has to be a PDF.");
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            return textWithLinks(doc);
        } catch (IOException e) {
            throw new ResponseStatusException(BAD_REQUEST, "That PDF could not be read: " + e.getMessage());
        }
    }

    /**
     * The visible text, then the web addresses hidden behind links. A resume shows "Source Code" and keeps the address in the
     * link itself, so the text alone would lose it. Each is listed as "visible words -> address".
     */
    static String textWithLinks(PDDocument doc) throws IOException {
        String text = new PDFTextStripper().getText(doc).strip();
        List<String> links = new ArrayList<>();
        for (PDPage page : doc.getPages()) {
            for (PDAnnotation a : page.getAnnotations()) {
                if (!(a instanceof PDAnnotationLink link) || !(link.getAction() instanceof PDActionURI uri) || uri.getURI() == null) continue;
                String words = wordsUnder(page, link.getRectangle());
                String line = (words.isBlank() ? "(link)" : words) + " -> " + uri.getURI().strip();
                if (!links.contains(line)) links.add(line);
            }
        }
        return links.isEmpty() ? text : text + "\n\nLINKS IN THE DOCUMENT (visible words -> address):\n" + String.join("\n", links);
    }

    private static String wordsUnder(PDPage page, PDRectangle r) {
        if (r == null) return "";
        try {
            PDFTextStripperByArea area = new PDFTextStripperByArea();
            float top = page.getCropBox().getHeight() - r.getUpperRightY();
            area.addRegion("link", new Rectangle2D.Float(r.getLowerLeftX(), top, r.getWidth(), r.getHeight()));
            area.extractRegions(page);
            return area.getTextForRegion("link").replaceAll("\\s+", " ").strip();
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    private Path path() {
        return dir.resolve("resume.pdf");
    }

    static boolean startsWithPdfMagic(byte[] b) {
        if (b.length < PDF_MAGIC.length) return false;
        for (int i = 0; i < PDF_MAGIC.length; i++) if (b[i] != PDF_MAGIC[i]) return false;
        return true;
    }

    /** Just the file name, never a path, and never empty. */
    static String cleanName(String name) {
        if (name == null || name.isBlank()) return "resume.pdf";
        String n = name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).strip();
        return n.isEmpty() ? "resume.pdf" : (n.length() > 150 ? n.substring(0, 150) : n);
    }
}
