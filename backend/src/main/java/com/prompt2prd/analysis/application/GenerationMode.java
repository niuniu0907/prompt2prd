package com.prompt2prd.analysis.application;

/** Controls which part of the analysis pipeline is executed. */
public enum GenerationMode {
    /** Full analysis from scratch — extracts requirements, generates Round 1 questions. */
    INITIAL_ANALYSIS,
    /** Process submitted answers — extract/confirm requirements, detect conflicts. No questions generated. */
    ANSWER_PROCESSING,
    /** Pre-generate questions only for the next round. No requirement extraction. */
    PRE_GENERATE_ROUND
}
