package org.example.service.coverletter;

import java.util.List;
import java.util.function.Function;

/** Returns canned paragraphs (or throws) instead of shelling out to Claude. */
public class FakeCoverLetterWriter implements CoverLetterWriter {

    private final Function<CoverLetterRequest, List<String>> answer;

    public FakeCoverLetterWriter(List<String> fixedParagraphs) {
        this(r -> fixedParagraphs);
    }

    public FakeCoverLetterWriter(Function<CoverLetterRequest, List<String>> answer) {
        this.answer = answer;
    }

    public static FakeCoverLetterWriter failing(String message) {
        return new FakeCoverLetterWriter(r -> {
            throw new CoverLetterWriteException(message);
        });
    }

    @Override
    public List<String> write(CoverLetterRequest request) {
        return answer.apply(request);
    }
}
