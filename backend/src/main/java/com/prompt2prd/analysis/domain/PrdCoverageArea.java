package com.prompt2prd.analysis.domain;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Minimum PRD information areas the clarification loop should converge toward. */
public enum PrdCoverageArea {
    PRODUCT_CONTEXT("productContext", "产品背景与目标", RequirementDimension.PRODUCT_SCOPE,
            "why this product exists, target outcome, non-goals, success signal"),
    ROLES_SCENARIOS("rolesScenarios", "用户角色与使用场景", RequirementDimension.ROLES_PERMISSIONS,
            "user roles, permissions, usage scenarios, entry points"),
    FEATURE_SCOPE_PRIORITIES("featureScopePriorities", "功能范围与优先级", RequirementDimension.FEATURES,
            "in-scope features, out-of-scope features, MVP priority"),
    CORE_BUSINESS_FLOW("coreBusinessFlow", "核心业务流程", RequirementDimension.CORE_FLOW,
            "main happy path, state changes, handoffs between roles"),
    USER_STORIES("userStories", "用户故事", RequirementDimension.CORE_FLOW,
            "user value stories and concise functional summaries"),
    RULES_EXCEPTIONS("rulesExceptions", "业务规则与异常场景", RequirementDimension.BUSINESS_RULES,
            "business rules, limits, responsibilities, exception handling"),
    PAGES_STATES("pagesStates", "页面清单", RequirementDimension.PAGES_APIS,
            "pages, page purpose, access roles, main actions, empty/loading/error/success states"),
    DATA_ENTITIES_FIELDS("dataEntitiesFields", "数据实体与字段", RequirementDimension.DATA_MODEL,
            "entities, important fields, relationships, lifecycle states"),
    ACCEPTANCE_CRITERIA("acceptanceCriteria", "验收标准", RequirementDimension.ACCEPTANCE,
            "business acceptance rules and Given/When/Then scenarios"),
    NON_FUNCTIONAL("nonFunctional", "非功能需求", RequirementDimension.ARCHITECTURE_CONSTRAINTS,
            "security, privacy, performance, compatibility, reliability constraints"),
    ASSUMPTIONS_RISKS_OPEN_ITEMS("assumptionsRisksOpenItems", "假设、风险与待确认事项", RequirementDimension.PRODUCT_SCOPE,
            "assumptions, risks, unresolved decisions, information still pending");

    private final String key;
    private final String label;
    private final RequirementDimension dimension;
    private final String guidance;

    PrdCoverageArea(String key, String label, RequirementDimension dimension, String guidance) {
        this.key = key;
        this.label = label;
        this.dimension = dimension;
        this.guidance = guidance;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public RequirementDimension dimension() {
        return dimension;
    }

    public String guidance() {
        return guidance;
    }

    public static String promptChecklist() {
        return Arrays.stream(values())
                .map(area -> "- " + area.key + " (" + area.label + "): " + area.guidance
                        + "; preferred dimension=" + area.dimension.name())
                .collect(Collectors.joining("\n"));
    }

    public static String targetFieldInstruction() {
        return Arrays.stream(values())
                .map(area -> area.key + ".*")
                .collect(Collectors.joining(", "));
    }
}
