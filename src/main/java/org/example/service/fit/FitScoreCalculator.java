package org.example.service.fit;

/**
 * Judges how well a job fits the candidate. The real implementation asks Claude; tests use a fake so they
 * never shell out or spend money.
 */
public interface FitScoreCalculator {

    /** @throws FitScoreCalculationException if Claude cannot be run, refuses, exceeds budget, or does not
     *                                         return a usable percentage */
    FitScoreResult calculate(FitScoreRequest request);
}
