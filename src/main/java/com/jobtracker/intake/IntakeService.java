package com.jobtracker.intake;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Accepts job descriptions as text, a link or an image and queues them. Nothing here waits for Claude. */
@Service
public class IntakeService {

    static final int MAX_TEXT_CHARS = 60_000;
    private static final Pattern JUST_A_LINK = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Path IMAGE_DIR = Path.of("uploads", "intake");

    private final IntakeRepository intakes;

    public IntakeService(IntakeRepository intakes) {
        this.intakes = intakes;
    }

    /** A single web address is a LINK; anything else is the posting's TEXT. */
    public Intake addText(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) throw new ResponseStatusException(BAD_REQUEST, "Paste a job description or a link first.");
        if (text.length() > MAX_TEXT_CHARS) {
            throw new ResponseStatusException(BAD_REQUEST, "That text is too long. Paste just the job description.");
        }
        boolean link = JUST_A_LINK.matcher(text).matches();
        return intakes.save(new Intake(link ? Intake.Kind.LINK : Intake.Kind.TEXT, text, null, label(text, link)));
    }

    public Intake addImage(byte[] bytes, String originalName) {
        String ext = imageExtension(bytes);
        if (ext == null) {
            throw new ResponseStatusException(BAD_REQUEST, "That file is not a PNG, JPEG, GIF or WebP image.");
        }
        try {
            Files.createDirectories(IMAGE_DIR);
            Path file = IMAGE_DIR.resolve(UUID.randomUUID() + "." + ext);
            Files.write(file, bytes);
            String name = originalName == null || originalName.isBlank() ? "Screenshot" : originalName;
            return intakes.save(new Intake(Intake.Kind.IMAGE, null, file.toAbsolutePath().toString(), "Image: " + shorten(name, 60)));
        } catch (IOException e) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "Could not save the image: " + e.getMessage());
        }
    }

    /** Everything still of interest to the user: waiting, being read, failed or a duplicate. Newest first. */
    public List<Intake> active() {
        return intakes.findByStatusNotInOrderByCreatedAtDesc(Set.of(Intake.Status.DONE));
    }

    public Intake retry(Long id) {
        Intake i = find(id);
        if (i.getStatus() != Intake.Status.FAILED) throw new ResponseStatusException(BAD_REQUEST, "Only a failed item can be retried.");
        i.setStatus(Intake.Status.PENDING);
        i.setMessage(null);
        return intakes.save(i);
    }

    /** Removes an item from the list (and its saved image, if it has one). */
    public void dismiss(Long id) {
        Intake i = find(id);
        if (i.getImagePath() != null) {
            try {
                Files.deleteIfExists(Path.of(i.getImagePath()));
            } catch (IOException ignored) {
                // a leftover file is harmless
            }
        }
        intakes.deleteById(id);
    }

    private Intake find(Long id) {
        return intakes.findById(id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "item " + id + " not found"));
    }

    /** Looks at the first bytes, not the file name: a name can lie. Package-private for tests. */
    static String imageExtension(byte[] b) {
        if (b == null || b.length < 12) return null;
        if ((b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') return "png";
        if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "jpg";
        if (b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8') return "gif";
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "webp";
        return null;
    }

    private static String label(String text, boolean link) {
        return link ? shorten(text, 120) : shorten(text.replaceAll("\\s+", " "), 100);
    }

    private static String shorten(String s, int max) {
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
