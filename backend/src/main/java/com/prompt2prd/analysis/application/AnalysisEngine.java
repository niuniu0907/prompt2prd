package com.prompt2prd.analysis.application;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface AnalysisEngine {
    Mono<RequirementAnalyzer.AnalysisResult> analyze(RequirementAnalyzer.AnalysisCommand command);
}
