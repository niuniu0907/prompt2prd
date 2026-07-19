package com.prompt2prd.generation.prd;

import com.prompt2prd.analysis.domain.RequirementItem;
import com.prompt2prd.analysis.domain.RequirementStatus;
import com.prompt2prd.analysis.domain.RequirementType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Analyzes PRD section edits for potential back-sync to requirements.
 * Only runs on explicit save or exit-edit; never on every keystroke.
 */
public final class PrdChangeAnalyzer {

    private PrdChangeAnalyzer() {
    }

    public static PrdChangeReport analyze(
            String sectionKey,
            String oldContent,
            String newContent,
            List<RequirementItem> currentRequirements) {

        Objects.requireNonNull(sectionKey, "sectionKey");
        Objects.requireNonNull(oldContent, "oldContent");
        Objects.requireNonNull(newContent, "newContent");
        Objects.requireNonNull(currentRequirements, "currentRequirements");

        if (oldContent.equals(newContent)) {
            return new PrdChangeReport(List.of(), List.of(), List.of());
        }

        List<PrdChangeReport.SyncedChange> synced = new ArrayList<>();
        List<PrdChangeReport.PendingChange> pending = new ArrayList<>();
        List<PrdChangeReport.ConflictWarning> conflicts = new ArrayList<>();

        List<FactChange> facts = extractFactChanges(oldContent, newContent);

        for (FactChange fact : facts) {
            List<RequirementItem> matches = findMatches(fact, currentRequirements);

            if (matches.isEmpty()) {
                pending.add(new PrdChangeReport.PendingChange(
                        fact.field(), fact.oldValue(), fact.newValue(),
                        "未找到可唯一映射的需求项"));
                continue;
            }

            if (matches.size() > 1) {
                pending.add(new PrdChangeReport.PendingChange(
                        fact.field(), fact.oldValue(), fact.newValue(),
                        "匹配到多个需求项（" + matches.size() + " 个），无法自动同步"));
                continue;
            }

            RequirementItem target = matches.getFirst();
            if (target.locked()) {
                conflicts.add(new PrdChangeReport.ConflictWarning(
                        target.id(), target.title(), fact.field(),
                        fact.oldValue(), fact.newValue(),
                        "目标需求项已锁定，不能自动覆盖"));
                continue;
            }

            synced.add(new PrdChangeReport.SyncedChange(
                    target.id(), target.title(), fact.field(),
                    fact.oldValue(), fact.newValue()));
        }

        return new PrdChangeReport(synced, pending, conflicts);
    }

    private static List<FactChange> extractFactChanges(String oldContent, String newContent) {
        List<FactChange> changes = new ArrayList<>();

        List<String> oldFacts = extractNumberedFacts(oldContent);
        List<String> newFacts = extractNumberedFacts(newContent);

        int maxLen = Math.max(oldFacts.size(), newFacts.size());
        for (int i = 0; i < maxLen; i++) {
            String oldFact = i < oldFacts.size() ? oldFacts.get(i) : "";
            String newFact = i < newFacts.size() ? newFacts.get(i) : "";
            if (!oldFact.equals(newFact) && !newFact.isBlank()) {
                changes.add(new FactChange("fact-" + (i + 1), oldFact, newFact));
            }
        }

        List<String> oldNumbers = extractNumericValues(oldContent);
        List<String> newNumbers = extractNumericValues(newContent);
        for (int i = 0; i < Math.min(oldNumbers.size(), newNumbers.size()); i++) {
            if (!oldNumbers.get(i).equals(newNumbers.get(i))) {
                changes.add(new FactChange("numeric-value", oldNumbers.get(i), newNumbers.get(i)));
            }
        }

        return changes;
    }

    private static List<String> extractNumberedFacts(String content) {
        List<String> facts = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.matches("^\\d+[.)]\\s.*") || trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                facts.add(trimmed.replaceFirst("^[-*]\\s+", "").replaceFirst("^\\d+[.)]\\s+", ""));
            }
        }
        return facts;
    }

    private static List<String> extractNumericValues(String content) {
        List<String> values = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(\\d+)\\s*(小时|天|分钟|次|元|%|个)").matcher(content);
        while (matcher.find()) {
            values.add(matcher.group());
        }
        return values;
    }

    private static List<RequirementItem> findMatches(FactChange fact, List<RequirementItem> requirements) {
        String oldLower = fact.oldValue().toLowerCase();
        List<RequirementItem> confirmed = requirements.stream()
                .filter(item -> item.status() == RequirementStatus.CONFIRMED)
                .toList();

        List<RequirementItem> byContent = confirmed.stream()
                .filter(item -> item.content().toLowerCase().contains(oldLower)
                        || oldLower.contains(item.content().toLowerCase()))
                .toList();

        if (byContent.size() == 1) return byContent;

        List<RequirementItem> byTitle = confirmed.stream()
                .filter(item -> {
                    String title = item.title().toLowerCase();
                    String old = oldLower;
                    return title.contains(old.substring(0, Math.min(old.length(), title.length() / 2)))
                            || old.contains(title);
                })
                .toList();

        return byTitle.isEmpty() ? byContent : byTitle;
    }

    private record FactChange(String field, String oldValue, String newValue) {
        FactChange {
            Objects.requireNonNull(field);
            oldValue = oldValue == null ? "" : oldValue;
            newValue = newValue == null ? "" : newValue;
        }
    }

    public record PrdChangeReport(
            List<SyncedChange> syncedChanges,
            List<PendingChange> pendingChanges,
            List<ConflictWarning> conflictWarnings) {

        public PrdChangeReport {
            syncedChanges = List.copyOf(syncedChanges);
            pendingChanges = List.copyOf(pendingChanges);
            conflictWarnings = List.copyOf(conflictWarnings);
        }

        public boolean hasChanges() {
            return !syncedChanges.isEmpty() || !pendingChanges.isEmpty() || !conflictWarnings.isEmpty();
        }

        public record SyncedChange(
                UUID requirementId, String requirementTitle, String field,
                String oldValue, String newValue) {
            public SyncedChange {
                Objects.requireNonNull(requirementId, "requirementId");
                Objects.requireNonNull(requirementTitle, "requirementTitle");
                Objects.requireNonNull(field, "field");
            }
        }

        public record PendingChange(
                String field, String oldValue, String newValue, String reason) {
            public PendingChange {
                Objects.requireNonNull(field, "field");
                Objects.requireNonNull(reason, "reason");
            }
        }

        public record ConflictWarning(
                UUID requirementId, String requirementTitle, String field,
                String oldValue, String newValue, String reason) {
            public ConflictWarning {
                Objects.requireNonNull(requirementId, "requirementId");
                Objects.requireNonNull(requirementTitle, "requirementTitle");
                Objects.requireNonNull(field, "field");
                Objects.requireNonNull(reason, "reason");
            }
        }
    }
}
