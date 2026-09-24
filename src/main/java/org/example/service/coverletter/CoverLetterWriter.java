package org.example.service.coverletter;

import java.util.List;

/**
 * Writes the actual wording of a tailored cover letter. The real implementation asks Claude; tests use a
 * fake so they never shell out or spend money.
 */
public interface CoverLetterWriter {

    /**
     * @return 3-4 short paragraphs, in order, ready to drop into the letter body as-is
     * @throws CoverLetterWriteException if Claude cannot be run, refuses, exceeds budget, or does not
     *                                    return usable structured output
     */
    List<String> write(CoverLetterRequest request);
}
