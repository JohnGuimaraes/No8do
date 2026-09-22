package com.no8do.api.replay;

/**
 * Estimates the token cost of non-empty text for a caller-selected context.
 * Implementations must return a positive count and may be provider-specific;
 * this contract deliberately supplies no tokenizer implementation.
 */
@FunctionalInterface
public interface TextTokenEstimator {
    int estimate(String text);
}
