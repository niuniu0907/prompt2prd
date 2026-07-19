package com.prompt2prd.analysis.application;

import com.prompt2prd.analysis.domain.RequirementItem;

import java.util.List;
import java.util.stream.Collectors;

/** Formats requirements compactly for model prompts, avoiding verbose record toString(). */
public final class RequirementFormatter {

    private RequirementFormatter() {}

    /** Returns one compact line per requirement: "[TYPE|STATUS] title: content". */
    public static String compactSummary(List<RequirementItem> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return "[]";
        }
        return requirements.stream()
                .map(RequirementFormatter::formatOne)
                .collect(Collectors.joining("\n", "[\n", "\n]"));
    }

    private static String formatOne(RequirementItem item) {
        return "[" + item.type().name() + "|" + item.status().name() + "] "
                + item.title() + ": " + item.content();
    }
}
