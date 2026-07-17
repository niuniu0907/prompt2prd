package com.prompt2prd.stream;

import java.util.Set;

public enum StreamEventType {
    ANALYSIS_STARTED("analysis_started", false, "phase"),
    ANALYSIS_PROGRESS("analysis_progress", false, "progress", "message"),
    REQUIREMENT_PATCH("requirement_patch", false, "path", "operation", "value"),
    QUESTION_CREATED("question_created", false, "question"),
    CONFLICT_DETECTED("conflict_detected", false, "conflict"),
    COMPLETENESS_CHANGED("completeness_changed", false, "previous", "current", "missingInformation"),
    ARCHITECTURE_CANDIDATE("architecture_candidate", false, "candidate"),
    ARCHITECTURE_CONFIRMED("architecture_confirmed", false, "architectureId"),
    SECTION_DELTA("section_delta", false, "sectionId", "delta"),
    SECTION_STARTED("section_started", false, "sectionId", "title"),
    SECTION_COMPLETED("section_completed", false, "sectionId", "status"),
    SECTION_FAILED("section_failed", false, "sectionId", "errorCode", "retryable"),
    GENERATION_ABORTED("generation_aborted", true, "reason", "completedStages"),
    GENERATION_COMPLETED("generation_completed", true, "nextStage", "finalState"),
    GENERATION_FAILED("generation_failed", true, "errorCode", "retryable");

    private final String wireName;
    private final boolean terminal;
    private final Set<String> requiredDataFields;

    StreamEventType(String wireName, boolean terminal, String... requiredDataFields) {
        this.wireName = wireName;
        this.terminal = terminal;
        this.requiredDataFields = Set.of(requiredDataFields);
    }

    public String wireName() {
        return wireName;
    }

    public boolean terminal() {
        return terminal;
    }

    public Set<String> requiredDataFields() {
        return requiredDataFields;
    }
}
