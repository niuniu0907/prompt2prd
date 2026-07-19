package com.prompt2prd.stream;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamEventSequenceTests {

    @Test
    void definesExactlyTheFifteenDocumentedEventTypes() {
        assertThat(StreamEventType.values()).extracting(StreamEventType::wireName)
                .containsExactly(
                        "analysis_started", "analysis_progress", "requirement_patch",
                        "question_created", "conflict_detected", "completeness_changed",
                        "architecture_candidate", "architecture_confirmed", "section_delta",
                        "section_started", "section_completed", "section_failed",
                        "generation_aborted", "generation_completed", "generation_failed");
    }

    @Test
    void createsStrictlyIncreasingEventsForOneRequest() {
        String requestId = UUID.randomUUID().toString();
        StreamEventSequence sequence = new StreamEventSequence(
                requestId, Clock.fixed(Instant.parse("2026-07-17T10:00:00Z"), ZoneOffset.UTC));

        StreamEvent first = sequence.next(StreamEventType.ANALYSIS_STARTED, Map.of("phase", "analysis"));
        StreamEvent second = sequence.next(StreamEventType.ANALYSIS_PROGRESS,
                Map.of("progress", 25, "message", "正在提取需求"));

        assertThat(List.of(first.eventId(), second.eventId())).containsExactly(1L, 2L);
        assertThat(first.requestId()).isEqualTo(requestId);
        assertThat(second.timestamp()).isEqualTo(Instant.parse("2026-07-17T10:00:00Z"));
    }

    @Test
    void allowsExactlyOneTerminalEventAndNothingAfterIt() {
        StreamEventSequence sequence = new StreamEventSequence(UUID.randomUUID().toString());

        sequence.next(StreamEventType.GENERATION_COMPLETED,
                Map.of("nextStage", "questions", "finalState", Map.of("project", Map.of())));

        assertThatThrownBy(() -> sequence.next(StreamEventType.GENERATION_FAILED,
                Map.of("errorCode", "FAILED", "retryable", true)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> sequence.next(StreamEventType.ANALYSIS_PROGRESS,
                Map.of("progress", 100, "message", "late")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKnownEventWhenRequiredDataIsMissing() {
        StreamEventSequence sequence = new StreamEventSequence(UUID.randomUUID().toString());

        assertThatThrownBy(() -> sequence.next(
                StreamEventType.ANALYSIS_PROGRESS, Map.of("progress", 20)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }
}
