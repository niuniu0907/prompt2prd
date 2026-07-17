package com.prompt2prd.architecture.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record ArchitectureCandidate(
        UUID id,
        String name,
        Map<String, String> stack,
        List<String> responsibilities,
        List<String> advantages,
        List<String> disadvantages,
        List<String> limitations,
        List<String> unselectedReasons,
        Map<ScoreDimension, Integer> scores,
        int totalScore,
        boolean recommended) {

    public ArchitectureCandidate {
        Objects.requireNonNull(id, "id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        stack = Map.copyOf(stack);
        responsibilities = List.copyOf(responsibilities);
        advantages = List.copyOf(advantages);
        disadvantages = List.copyOf(disadvantages);
        limitations = List.copyOf(limitations);
        unselectedReasons = List.copyOf(unselectedReasons);
        scores = Map.copyOf(scores);
        if (scores.size() != ScoreDimension.values().length || scores.values().stream().anyMatch(value -> value < 1 || value > 5)) {
            throw new IllegalArgumentException("all architecture scores must be present and between 1 and 5");
        }
    }

    public ArchitectureCandidate recommended(boolean selected) {
        return new ArchitectureCandidate(id, name, stack, responsibilities, advantages, disadvantages,
                limitations, unselectedReasons, scores, totalScore, selected);
    }

    public enum ScoreDimension {
        LEARNING_COST,
        DEVELOPMENT_SPEED,
        DEPLOYMENT_SIMPLICITY,
        RUNNING_COST,
        MAINTAINABILITY,
        SCALABILITY,
        AI_SUPPORT
    }
}
