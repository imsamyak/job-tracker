package org.example.service.fit;

import java.util.function.Function;

/** Returns a canned {@link FitScoreResult} (or throws) instead of shelling out to Claude. */
public class FakeFitScoreCalculator implements FitScoreCalculator {

    private final Function<FitScoreRequest, FitScoreResult> answer;

    /** Convenient default for tests that don't care about the actual fit score. */
    public FakeFitScoreCalculator() {
        this(r -> new FitScoreResult(80, "Good overlap"));
    }

    public FakeFitScoreCalculator(FitScoreResult fixed) {
        this(r -> fixed);
    }

    public FakeFitScoreCalculator(Function<FitScoreRequest, FitScoreResult> answer) {
        this.answer = answer;
    }

    public static FakeFitScoreCalculator failing(String message) {
        return new FakeFitScoreCalculator(r -> {
            throw new FitScoreCalculationException(message);
        });
    }

    @Override
    public FitScoreResult calculate(FitScoreRequest request) {
        return answer.apply(request);
    }
}
