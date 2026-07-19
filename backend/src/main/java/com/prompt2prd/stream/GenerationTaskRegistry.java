package com.prompt2prd.stream;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-project registry that enforces one active generation task at a time.
 * <p>
 * When a new request for a project supersedes an older one, the old requestId
 * is marked stale. Any late result carrying a stale requestId must be rejected
 * by callers before persisting state — the registry itself never mutates
 * application state; it only tracks which request is the current active winner.
 * <p>
 * Analysis, architecture, flowchart, and PRD controllers each consult this
 * registry before merging AI results into the canonical requirement state.
 */
public final class GenerationTaskRegistry {

    private final Map<String, ActiveTask> tasks = new ConcurrentHashMap<>();

    /**
     * Registers a new generation task for the given project, returning the
     * requestId that was previously active (if any) so the caller can ignore
     * late arrivals from that superseded predecessor.
     *
     * @param projectId non-blank project identifier
     * @param requestId UUID of the incoming task
     * @return the previously-active requestId, or {@code null} if none existed
     */
    public String activate(String projectId, String requestId) {
        Objects.requireNonNull(projectId, "projectId");
        if (projectId.isBlank()) {
            throw new IllegalArgumentException("projectId must not be blank");
        }
        UUID.fromString(Objects.requireNonNull(requestId, "requestId"));

        ActiveTask previous = tasks.put(projectId, new ActiveTask(requestId));
        return previous != null ? previous.requestId() : null;
    }

    /**
     * Returns whether the given requestId is still the active generation task
     * for the associated project. Late arrivals from superseded requests must
     * be discarded.
     */
    public boolean isCurrent(String projectId, String requestId) {
        ActiveTask current = tasks.get(projectId);
        return current != null && current.requestId().equals(requestId);
    }

    /**
     * Removes tracking for a project once generation has cleanly finished or
     * been cancelled, so future requests do not falsely detect a stale predecessor.
     */
    public void release(String projectId, String requestId) {
        tasks.computeIfPresent(projectId, (key, current) ->
                current.requestId().equals(requestId) ? null : current);
    }

    /** Visible for test observers only. */
    int taskCount() {
        return tasks.size();
    }

    private record ActiveTask(String requestId) {
        ActiveTask {
            Objects.requireNonNull(requestId, "requestId");
        }
    }
}
