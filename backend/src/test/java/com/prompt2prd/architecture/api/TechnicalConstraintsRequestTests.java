package com.prompt2prd.architecture.api;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TechnicalConstraintsRequestTests {

    @Test
    void tracksUnansweredCriticalConstraintsWithoutInventingAnswers() {
        var request = new TechnicalConstraintsRequest(UUID.randomUUID(), List.of(), " Svelte ",
                null, null, null, null, null, null, null, null, null);

        assertThat(request.allTechnologies()).containsExactly("Svelte");
        assertThat(request.pendingFields()).contains("targetPlatform", "teamSize", "criticalCapabilities");
        assertThat(request.pendingFields()).doesNotContain("knownTechnologies");
    }

    @Test
    void projectIdIsRequired() {
        var request = new TechnicalConstraintsRequest(null, List.of("Vue"), null,
                TechnicalConstraintsRequest.TargetPlatform.WEB,
                TechnicalConstraintsRequest.TeamSize.SOLO,
                TechnicalConstraintsRequest.UserScale.SMALL,
                new TechnicalConstraintsRequest.CriticalCapabilities(false, false, false, true, true),
                TechnicalConstraintsRequest.DataSensitivity.PERSONAL,
                TechnicalConstraintsRequest.Deployment.MONOLITHIC_DOCKER,
                TechnicalConstraintsRequest.Budget.MINIMAL,
                TechnicalConstraintsRequest.Timeline.RAPID,
                TechnicalConstraintsRequest.MaintenanceCapacity.LOW);

        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(request)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("projectId");
    }
}
