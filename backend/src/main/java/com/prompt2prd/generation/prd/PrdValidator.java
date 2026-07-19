package com.prompt2prd.generation.prd;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic consistency checks for a complete PRD.
 * Validates stable IDs, cross-references, and section completeness.
 */
public final class PrdValidator {

    private static final Pattern STABLE_ID = Pattern.compile(
            "(REQ|US|BR|API|PAGE|AC|PHASE)-\\d{3}");
    private static final Pattern DETAILED_ARCHITECTURE_MARKER = Pattern.compile(
            "(详细架构|架构比较|评分矩阵|总分|/35|学习成本|开发速度|未选择原因|deployment topology|system design)",
            Pattern.CASE_INSENSITIVE);

    private PrdValidator() {
    }

    public static ValidationReport validate(Map<String, String> sections, String confirmedArchitectureId) {
        Objects.requireNonNull(sections, "sections");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (sections.isEmpty()) {
            errors.add("PRD 至少需要一个章节");
            return new ValidationReport(false, errors, warnings);
        }

        validateSectionsExist(sections, errors);
        List<String> allIds = collectStableIds(sections, errors);
        validateCrossReferences(allIds, sections, errors);
        validateAcceptanceSection(sections, errors);
        validateNoCandidateArchitectureLeak(sections, warnings);

        return new ValidationReport(errors.isEmpty(), errors, warnings);
    }

    private static void validateSectionsExist(Map<String, String> sections, List<String> errors) {
        List<PrdDefinition.Section> required = PrdDefinition.sections();
        Set<String> present = sections.keySet();
        for (PrdDefinition.Section section : required) {
            if (!present.contains(section.key().wireName())) {
                errors.add("缺少必需章节：" + section.title());
            }
        }
    }

    private static List<String> collectStableIds(Map<String, String> sections, List<String> errors) {
        List<String> allIds = new ArrayList<>();
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            Set<String> sectionSeen = new HashSet<>();
            Matcher matcher = STABLE_ID.matcher(entry.getValue());
            while (matcher.find()) {
                String id = matcher.group();
                allIds.add(id);
                if (!sectionSeen.add(id)) {
                    warnings(entry.getKey(), "同一章节内重复引用稳定 ID：" + id, errors);
                }
            }
        }
        return allIds;
    }

    private static void validateCrossReferences(
            List<String> allIds, Map<String, String> sections, List<String> errors) {
        Set<String> declared = new HashSet<>(allIds);

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            Matcher matcher = STABLE_ID.matcher(entry.getValue());
            while (matcher.find()) {
                String ref = matcher.group();
                if (!declared.contains(ref)) {
                    String prefix = ref.substring(0, ref.indexOf('-'));
                    boolean hasDeclaration = allIds.stream().anyMatch(id -> id.startsWith(prefix));
                    if (!hasDeclaration) {
                        errors.add("章节 " + entry.getKey() + " 引用了未声明的 ID：" + ref);
                    } else {
                        warnings(entry.getKey(), "引用了未在当前内容中找到的 ID：" + ref, errors);
                    }
                }
            }
        }

        Set<String> prefixes = new HashSet<>();
        for (String id : allIds) prefixes.add(id.substring(0, id.indexOf('-')));
        if (!prefixes.contains("US") && sections.containsKey("user-stories")
                && !sections.get("user-stories").isBlank()) {
            errors.add("缺少用户故事编号（US-xxx）");
        }
        if (!prefixes.contains("BR") && sections.containsKey("business-rules")
                && !sections.get("business-rules").isBlank()) {
            errors.add("缺少业务规则编号（BR-xxx）");
        }
        if (!prefixes.contains("AC") && sections.containsKey("acceptance-criteria")
                && !sections.get("acceptance-criteria").isBlank()) {
            errors.add("缺少验收条件编号（AC-xxx）");
        }
    }

    private static void validateAcceptanceSection(Map<String, String> sections, List<String> errors) {
        String acceptance = sections.getOrDefault("acceptance-criteria", "");
        if (acceptance.isBlank()) {
            errors.add("验收章节不能为空");
            return;
        }
        boolean hasGivenWhenThen = acceptance.contains("Given") || acceptance.contains("当")
                || acceptance.contains("假设");
        if (!hasGivenWhenThen) {
            errors.add("验收章节应包含 Given/When/Then 格式的场景");
        }
    }

    private static void validateNoCandidateArchitectureLeak(
            Map<String, String> sections, List<String> warnings) {
        for (Map.Entry<String, String> entry : sections.entrySet()) {
            if (entry.getValue().contains("备选架构") || entry.getValue().contains("alternative architecture")
                    || DETAILED_ARCHITECTURE_MARKER.matcher(entry.getValue()).find()) {
                warnings.add("章节 " + entry.getKey() + " 可能包含技术方案、架构比较或备选架构描述，应移至可选附件");
            }
        }
    }

    private static void warnings(String section, String message, List<String> errors) {
        errors.add("[" + section + "] " + message);
    }

    public record ValidationReport(boolean valid, List<String> errors, List<String> warnings) {
        public ValidationReport {
            errors = List.copyOf(errors);
            warnings = List.copyOf(warnings);
            if (valid != errors.isEmpty()) {
                throw new IllegalArgumentException("valid must match whether errors is empty");
            }
        }
    }
}
