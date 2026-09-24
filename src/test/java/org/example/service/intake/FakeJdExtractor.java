package org.example.service.intake;

import java.nio.file.Path;
import java.util.function.Function;

/** Returns a canned {@link ExtractedJob} (or throws) instead of shelling out to Claude. */
public class FakeJdExtractor implements JdExtractor {

    private final Function<Path, ExtractedJob> answer;

    public FakeJdExtractor(ExtractedJob fixed) {
        this(pdf -> fixed);
    }

    public FakeJdExtractor(Function<Path, ExtractedJob> answer) {
        this.answer = answer;
    }

    public static FakeJdExtractor failing(String message) {
        return new FakeJdExtractor(pdf -> {
            throw new JdExtractionException(message);
        });
    }

    @Override
    public ExtractedJob extract(Path jdPdf, String pageText) {
        return answer.apply(jdPdf);
    }
}
