package org.example.service.fit;

/**
 * @param percentage 0-100
 * @param summary    one short sentence explaining the number
 */
public record FitScoreResult(int percentage, String summary) {
}
