package com.prompt2prd.analysis.api;

import com.prompt2prd.analysis.domain.ClarificationQuestion;

import java.util.List;

public record GenerateRoundResponse(
        boolean success,
        String errorCode,
        String errorMessage,
        int roundNo,
        List<ClarificationQuestion> questions,
        List<String> coverageCategories,
        String requestId) {

    public static GenerateRoundResponse success(
            int roundNo,
            List<ClarificationQuestion> questions,
            List<String> coverageCategories,
            String requestId) {
        return new GenerateRoundResponse(
                true, null, null, roundNo, List.copyOf(questions),
                List.copyOf(coverageCategories), requestId);
    }

    public static GenerateRoundResponse error(String errorCode, String errorMessage) {
        return new GenerateRoundResponse(
                false, errorCode, errorMessage, 0, List.of(), List.of(), "");
    }
}
