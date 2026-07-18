package com.prompt2prd.generation.prd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Fixed AI-ready PRD structure and deterministic traceability rules. */
public final class PrdDefinition {

    private static final List<Section> SECTIONS = List.of(
            section(SectionKey.CODING_AGENT_GUIDE, "Coding Agent 使用说明", 1),
            section(SectionKey.PRODUCT_CONTEXT, "产品背景、目标与非目标", 2),
            section(SectionKey.ROLES_PERMISSIONS, "用户角色与权限", 3),
            section(SectionKey.FEATURES_PRIORITIES, "功能模块及优先级", 4),
            section(SectionKey.USER_STORIES, "用户故事与功能摘要", 5),
            section(SectionKey.FLOWS_STATE_MACHINE, "核心业务流程与状态机", 6),
            section(SectionKey.RULES_EXCEPTIONS, "业务规则和异常场景", 7),
            section(SectionKey.ARCHITECTURE, "技术决策摘要与工程约束", 8),
            section(SectionKey.DATA_MODEL, "数据实体、字段、关系和状态", 9),
            section(SectionKey.PAGES, "页面清单与页面状态", 10),
            section(SectionKey.APIS, "接口契约、请求响应示例和错误码", 11),
            section(SectionKey.NON_FUNCTIONAL, "安全、性能和其他非功能需求", 12),
            section(SectionKey.ACCEPTANCE, "普通验收规则和 Given/When/Then 场景", 13),
            section(SectionKey.IMPLEMENTATION_PHASES, "分阶段实施计划", 14),
            section(SectionKey.TEST_STRATEGY, "测试策略与关键测试用例", 15),
            section(SectionKey.PROHIBITIONS, "明确禁止事项", 16),
            section(SectionKey.OPEN_ITEMS, "AI 假设、待确认事项和已知限制", 17));

    private PrdDefinition() {
    }

    public static List<Section> sections() {
        return SECTIONS;
    }

    public static Section requireSection(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("section key must not be blank");
        }
        return SECTIONS.stream()
                .filter(section -> section.key().wireName().equals(key.trim()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown PRD section: " + key));
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
        CODING_AGENT_GUIDE("coding-agent-guide"), PRODUCT_CONTEXT("product-context"),
        ROLES_PERMISSIONS("roles-permissions"), FEATURES_PRIORITIES("features-priorities"),
        USER_STORIES("user-stories"), FLOWS_STATE_MACHINE("flows-state-machine"),
        RULES_EXCEPTIONS("rules-exceptions"), ARCHITECTURE("architecture"),
        DATA_MODEL("data-model"), PAGES("pages"), APIS("apis"),
        NON_FUNCTIONAL("non-functional"), ACCEPTANCE("acceptance"),
        IMPLEMENTATION_PHASES("implementation-phases"), TEST_STRATEGY("test-strategy"),
        PROHIBITIONS("prohibitions"), OPEN_ITEMS("open-items");

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
