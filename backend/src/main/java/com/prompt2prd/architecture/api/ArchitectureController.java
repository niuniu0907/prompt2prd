package com.prompt2prd.architecture.api;

import com.prompt2prd.architecture.application.ArchitectureRecommender;
import com.prompt2prd.architecture.domain.ArchitectureCandidate;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/architecture")
public final class ArchitectureController {

    private final ArchitectureRecommender recommender;

    public ArchitectureController(ArchitectureRecommender recommender) {
        this.recommender = recommender;
    }

    @PostMapping("/recommend")
    public ArchitectureRecommendationResponse recommend(
            @Valid @RequestBody TechnicalConstraintsRequest request) {
        return new ArchitectureRecommendationResponse(
                recommender.recommend(request), request.pendingFields());
    }

    public record ArchitectureRecommendationResponse(
            List<ArchitectureCandidate> candidates,
            List<String> pendingFields) {
        public ArchitectureRecommendationResponse {
            candidates = List.copyOf(candidates);
            pendingFields = List.copyOf(pendingFields);
        }
    }
}
