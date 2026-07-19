package com.prompt2prd.architecture.application;

import com.prompt2prd.architecture.api.TechnicalConstraintsRequest;
import com.prompt2prd.architecture.domain.ArchitectureCandidate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureRecommenderTests {

    @Test
    void prioritizesExistingVueJavaSkillsAndKeepsATypeScriptAlternative() {
        var request = new TechnicalConstraintsRequest(UUID.randomUUID(),
                List.of("Vue 3", "Java", "Spring Boot"), null,
                TechnicalConstraintsRequest.TargetPlatform.WEB,
                TechnicalConstraintsRequest.TeamSize.SOLO,
                TechnicalConstraintsRequest.UserScale.SMALL,
                new TechnicalConstraintsRequest.CriticalCapabilities(false, false, false, true, true),
                TechnicalConstraintsRequest.DataSensitivity.PERSONAL,
                TechnicalConstraintsRequest.Deployment.MONOLITHIC_DOCKER,
                TechnicalConstraintsRequest.Budget.MINIMAL,
                TechnicalConstraintsRequest.Timeline.RAPID,
                TechnicalConstraintsRequest.MaintenanceCapacity.LOW);

        List<ArchitectureCandidate> candidates = new ArchitectureRecommender().recommend(request);

        assertThat(candidates).hasSizeBetween(2, 3);
        assertThat(candidates).filteredOn(ArchitectureCandidate::recommended).singleElement()
                .extracting(ArchitectureCandidate::name).isEqualTo("Vue 3 + Spring Boot 单体");
        assertThat(candidates).extracting(ArchitectureCandidate::name)
                .contains("Vue 3 + NestJS 全栈 TypeScript");
        assertThat(candidates).allSatisfy(candidate -> {
            assertThat(candidate.stack()).containsKeys("frontend", "backend", "storage", "authentication",
                    "fileStorage", "ai", "deployment", "testing");
            assertThat(candidate.scores()).hasSize(7);
            assertThat(candidate.responsibilities()).isNotEmpty();
            assertThat(candidate.limitations()).isNotEmpty();
            assertThat(candidate.unselectedReasons()).isNotEmpty();
        });
    }
}
