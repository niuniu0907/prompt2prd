package com.prompt2prd.architecture.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record TechnicalConstraintsRequest(
        @NotNull UUID projectId,
        List<@NotBlank String> knownTechnologies,
        String customTechnology,
        TargetPlatform targetPlatform,
        TeamSize teamSize,
        UserScale userScale,
        @Valid CriticalCapabilities criticalCapabilities,
        DataSensitivity dataSensitivity,
        Deployment deployment,
        Budget budget,
        Timeline timeline,
        MaintenanceCapacity maintenanceCapacity) {

    public TechnicalConstraintsRequest {
        knownTechnologies = knownTechnologies == null ? List.of() : knownTechnologies.stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
        customTechnology = normalizeOptional(customTechnology);
    }

    public List<String> allTechnologies() {
        LinkedHashSet<String> technologies = new LinkedHashSet<>(knownTechnologies);
        if (customTechnology != null) {
            technologies.add(customTechnology);
        }
        return List.copyOf(technologies);
    }

    public boolean knows(String technology) {
        String expected = technology.trim().toLowerCase(Locale.ROOT);
        return allTechnologies().stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.equals(expected) || value.contains(expected));
    }

    public List<String> pendingFields() {
        List<String> fields = new ArrayList<>();
        if (allTechnologies().isEmpty()) fields.add("knownTechnologies");
        if (targetPlatform == null) fields.add("targetPlatform");
        if (teamSize == null) fields.add("teamSize");
        if (userScale == null) fields.add("userScale");
        if (criticalCapabilities == null) fields.add("criticalCapabilities");
        if (dataSensitivity == null) fields.add("dataSensitivity");
        if (deployment == null) fields.add("deployment");
        if (budget == null) fields.add("budget");
        if (timeline == null) fields.add("timeline");
        if (maintenanceCapacity == null) fields.add("maintenanceCapacity");
        return List.copyOf(fields);
    }

    private static String normalizeOptional(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    public record CriticalCapabilities(
            boolean login,
            boolean realtime,
            boolean payments,
            boolean fileUpload,
            boolean ai) {
    }

    public enum TargetPlatform { WEB, DESKTOP, MOBILE, CROSS_PLATFORM, API }
    public enum TeamSize { SOLO, SMALL_TEAM, LARGE_TEAM }
    public enum UserScale { PROTOTYPE, SMALL, MEDIUM, LARGE }
    public enum DataSensitivity { PUBLIC, INTERNAL, PERSONAL, SENSITIVE, HIGHLY_SENSITIVE }
    public enum Deployment { LOCAL, MONOLITHIC_DOCKER, CLOUD, MULTI_INSTANCE }
    public enum Budget { MINIMAL, LIMITED, FLEXIBLE }
    public enum Timeline { RAPID, STANDARD, LONG_TERM }
    public enum MaintenanceCapacity { LOW, MEDIUM, HIGH }
}
