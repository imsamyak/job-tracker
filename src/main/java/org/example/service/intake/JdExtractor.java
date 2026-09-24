package org.example.service.intake;

import java.nio.file.Path;

/**
 * Reads a job posting and extracts its details. The real implementation asks Claude; tests use a fake
 * so they never shell out or spend money.
 */
public interface JdExtractor {

    /**
     * @param jdPdf    the posting saved as a PDF (the primary source, since it is what the applicant saw)
     * @param pageText the page's plain text, given as supporting context for wording the PDF layout may
     *                 obscure
     * @throws JdExtractionException if Claude cannot be run, refuses, exceeds budget, or does not return
     *                                usable structured output
     */
    ExtractedJob extract(Path jdPdf, String pageText);
}
