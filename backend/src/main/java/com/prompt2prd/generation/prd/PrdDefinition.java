package com.prompt2prd.generation.prd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Fixed AI-ready PRD structure and deterministic traceability rules. */
public final class PrdDefinition {

    private static final List<Section> SECTIONS = List.of(
            section(SectionKey.PRODUCT_BACKGROUND_GOALS, "产品背景与目标", 1),
            section(SectionKey.TARGET_USERS_SCENARIOS, "目标用户与使用场景", 2),
            section(SectionKey.PRODUCT_SCOPE, "产品范围", 3),
            section(SectionKey.FEATURE_MODULES_PRIORITY, "功能模块与优先级", 4),
            section(SectionKey.USER_STORIES, "用户故事", 5),
            section(SectionKey.BUSINESS_RULES, "业务规则", 6),
            section(SectionKey.EXCEPTION_SCENARIOS, "异常场景", 7),
            section(SectionKey.PAGE_LIST_STATES, "页面清单与页面状态", 8),
            section(SectionKey.DATA_REQUIREMENTS, "数据需求", 9),
            section(SectionKey.ACCEPTANCE_CRITERIA, "验收标准", 10),
            section(SectionKey.NON_FUNCTIONAL_REQUIREMENTS, "非功能需求", 11),
            section(SectionKey.RISKS_ASSUMPTIONS_OPEN_ITEMS, "风险、假设与待确认事项", 12));

    private PrdDefinition() {
    }

    public static List<Section> sections() {
        return SECTIONS;
    }

    public static Section requireSection(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("section key must not be blank");
        }
        String normalizedKey = normalizeSectionKey(key.trim());
        return SECTIONS.stream()
                .filter(section -> section.key().wireName().equals(normalizedKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown PRD section: " + key));
    }

    private static String normalizeSectionKey(String key) {
        return switch (key) {
            case "product-goals" -> "product-background-goals";
            case "user-roles" -> "target-users-scenarios";
            case "feature-scope" -> "product-scope";
            case "pages" -> "page-list-states";
            case "data-needs" -> "data-requirements";
            case "acceptance" -> "acceptance-criteria";
            case "non-functional" -> "non-functional-requirements";
            case "risks-open-items" -> "risks-assumptions-open-items";
            default -> key;
        };
    }

    public static String stableId(ArtifactType type, int sequence) {
        Objects.requireNonNull(type, "type");
        if (sequence < 1 || sequence > 999) {
            throw new IllegalArgumentException("sequence must be between 1 and 999");
        }
        return String.format(Locale.ROOT, "%s-%03d", type.prefix(), sequence);
    }

    public static TraceabilityReport validateTraceability(List<TraceLink> links) {
        List<String> errors = new ArrayList<>();
        if (links == null || links.isEmpty()) {
            errors.add("At least one core feature trace link is required");
        }
        Set<String> featureIds = new java.util.HashSet<>();
        for (TraceLink link : links == null ? List.<TraceLink>of() : links) {
            if (!matches(link.featureId(), ArtifactType.REQUIREMENT)) {
                errors.add("Invalid feature ID: " + link.featureId());
            } else if (!featureIds.add(link.featureId())) {
                errors.add("Duplicate feature ID: " + link.featureId());
            }
            if (link.userStoryId() == null || !link.userStoryId().matches("US-\\d{3}")) {
                errors.add("Feature " + link.featureId() + " must reference one user story");
            }
            if (link.businessRuleIds().isEmpty()
                    || link.businessRuleIds().stream().anyMatch(id -> !matches(id, ArtifactType.BUSINESS_RULE))) {
                errors.add("Feature " + link.featureId() + " must reference valid business rules");
            }
            if (link.acceptanceIds().isEmpty()
                    || link.acceptanceIds().stream().anyMatch(id -> !matches(id, ArtifactType.ACCEPTANCE))) {
                errors.add("Feature " + link.featureId() + " must reference valid acceptance criteria");
            }
        }
        return new TraceabilityReport(errors.isEmpty(), errors);
    }

    private static boolean matches(String value, ArtifactType type) {
        return value != null && value.matches(type.prefix() + "-\\d{3}");
    }

    private static Section section(SectionKey key, String title, int order) {
        return new Section(key, title, order);
    }

    public enum SectionKey {
        PRODUCT_BACKGROUND_GOALS("product-background-goals"),
        TARGET_USERS_SCENARIOS("target-users-scenarios"),
        PRODUCT_SCOPE("product-scope"), FEATURE_MODULES_PRIORITY("feature-modules-priority"),
        USER_STORIES("user-stories"),
        BUSINESS_RULES("business-rules"), EXCEPTION_SCENARIOS("exception-scenarios"),
        PAGE_LIST_STATES("page-list-states"), DATA_REQUIREMENTS("data-requirements"),
        ACCEPTANCE_CRITERIA("acceptance-criteria"),
        NON_FUNCTIONAL_REQUIREMENTS("non-functional-requirements"),
        RISKS_ASSUMPTIONS_OPEN_ITEMS("risks-assumptions-open-items");

        private final String wireName;

        SectionKey(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }
    }

    public enum DirectiveLevel { MUST, SHOULD, MUST_NOT }

    public enum ArtifactType {
        REQUIREMENT("REQ"), USER_STORY("US"), BUSINESS_RULE("BR"), API("API"), PAGE("PAGE"),
        ACCEPTANCE("AC"), IMPLEMENTATION_PHASE("PHASE");

        private final String prefix;

        ArtifactType(String prefix) {
            this.prefix = prefix;
        }

        public String prefix() {
            return prefix;
        }
    }

    public record Section(SectionKey key, String title, int order) {
        public Section {
            Objects.requireNonNull(key, "key");
            if (title == null || title.isBlank()) throw new IllegalArgumentException("title must not be blank");
            if (order < 1) throw new IllegalArgumentException("order must be positive");
            title = title.trim();
        }
    }

    public record TraceLink(
            String featureId,
            String userStoryId,
            List<String> businessRuleIds,
            List<String> acceptanceIds) {
        public TraceLink {
            businessRuleIds = List.copyOf(businessRuleIds == null ? List.of() : businessRuleIds);
            acceptanceIds = List.copyOf(acceptanceIds == null ? List.of() : acceptanceIds);
        }
    }

    public record TraceabilityReport(boolean valid, List<String> errors) {
        public TraceabilityReport {
            errors = List.copyOf(errors == null ? List.of() : errors);
            if (valid != errors.isEmpty()) throw new IllegalArgumentException("valid must match errors");
        }
    }
}
