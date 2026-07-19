package com.prompt2prd.analysis.domain;

public enum RequirementDimension {
    PRODUCT_SCOPE(10),
    ROLES_PERMISSIONS(8),
    CORE_FLOW(15),
    FEATURES(12),
    BUSINESS_RULES(10),
    EXCEPTIONS(8),
    DATA_MODEL(10),
    ARCHITECTURE_CONSTRAINTS(12),
    PAGES_APIS(8),
    ACCEPTANCE(7);

    private final int weight;

    RequirementDimension(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
