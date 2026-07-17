package com.prompt2prd.stream;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GenerationTaskRegistryTests {

    @Test
    void returnsNullForFirstActivation() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String previous = registry.activate("project-1", UUID.randomUUID().toString());
        assertNull(previous);
    }

    @Test
    void returnsPreviousRequestIdOnSupersedingActivation() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();

        assertNull(registry.activate("project-1", first));
        String superseded = registry.activate("project-1", second);
        assertEquals(first, superseded);
    }

    @Test
    void isCurrentReturnsTrueForActiveRequest() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String requestId = UUID.randomUUID().toString();
        registry.activate("project-1", requestId);

        assertTrue(registry.isCurrent("project-1", requestId));
    }

    @Test
    void isCurrentReturnsFalseForSupersededRequest() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        registry.activate("project-1", first);
        registry.activate("project-1", second);

        assertFalse(registry.isCurrent("project-1", first));
        assertTrue(registry.isCurrent("project-1", second));
    }

    @Test
    void isCurrentReturnsFalseForUnknownProject() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        assertFalse(registry.isCurrent("nonexistent", UUID.randomUUID().toString()));
    }

    @Test
    void releaseRemovesTrackingWhenRequestIdMatches() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String requestId = UUID.randomUUID().toString();
        registry.activate("project-1", requestId);
        registry.release("project-1", requestId);

        assertFalse(registry.isCurrent("project-1", requestId));
        assertEquals(0, registry.taskCount());
    }

    @Test
    void releaseDoesNotRemoveDifferentRequest() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String first = UUID.randomUUID().toString();
        String second = UUID.randomUUID().toString();
        registry.activate("project-1", first);
        registry.activate("project-1", second);

        registry.release("project-1", first);
        assertTrue(registry.isCurrent("project-1", second));
        assertEquals(1, registry.taskCount());
    }

    @Test
    void independentProjectsDoNotInterfere() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        String reqA = UUID.randomUUID().toString();
        String reqB = UUID.randomUUID().toString();

        registry.activate("project-a", reqA);
        registry.activate("project-b", reqB);

        assertTrue(registry.isCurrent("project-a", reqA));
        assertTrue(registry.isCurrent("project-b", reqB));
    }

    @Test
    void rejectsBlankProjectId() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                registry.activate("  ", UUID.randomUUID().toString()));
    }

    @Test
    void rejectsInvalidRequestId() {
        GenerationTaskRegistry registry = new GenerationTaskRegistry();
        assertThrows(IllegalArgumentException.class, () ->
                registry.activate("project-1", "not-a-uuid"));
    }
}
