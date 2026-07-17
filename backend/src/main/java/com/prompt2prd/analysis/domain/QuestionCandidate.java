package com.prompt2prd.analysis.domain;

import java.util.Objects;

public record QuestionCandidate(
        ClarificationQuestion question,
        int businessImpact,
        int informationGap,
        int dependencyCount,
        int risk) {

    public QuestionCandidate {
        Objects.requireNonNull(question, "question");
        requireScore(businessImpact, "businessImpact");
        requireScore(informationGap, "informationGap");
        requireScore(dependencyCount, "dependencyCount");
        requireScore(risk, "risk");
    }

    public double weightedScore() {
        double value = businessImpact * 0.4
                + informationGap * 0.3
                + dependencyCount * 0.2
                + risk * 0.1;
        return Math.round(value * 10.0) / 10.0;
    }

    private static void requireScore(int value, String name) {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException(name + " must be between 1 and 5");
        }
    }
}
