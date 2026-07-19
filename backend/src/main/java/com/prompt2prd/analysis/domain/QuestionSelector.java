package com.prompt2prd.analysis.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Fixed scoring, deterministic tie-breaking, and non-vector deduplication. */
public final class QuestionSelector {

    private static final int DEFAULT_LIMIT = 10;
    private static final Set<String> CRITICAL_KEYWORDS = Set.of(
            "交易", "支付", "退款", "权限", "认证", "鉴权", "安全", "责任",
            "状态", "验收", "接口", "页面", "数据", "异常", "风险", "性能",
            "transaction", "payment", "refund", "permission", "auth",
            "security", "state", "acceptance", "api", "page", "data",
            "exception", "risk", "performance", "productcontext",
            "rolesscenarios", "featurescopepriorities", "corebusinessflow",
            "userstories", "rulesexceptions", "pagesstates", "dataentitiesfields",
            "apirequirements", "acceptancecriteria", "nonfunctional",
            "assumptionsrisksopenitems");

    public List<ClarificationQuestion> select(List<QuestionCandidate> candidates) {
        return select(candidates, DEFAULT_LIMIT);
    }

    public List<ClarificationQuestion> select(
            List<QuestionCandidate> candidates,
            int limit) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (limit < 1 || limit > 10) {
            throw new IllegalArgumentException("limit must be between 1 and 10");
        }

        List<QuestionCandidate> ordered = candidates.stream()
                .sorted(Comparator
                        .comparingDouble(QuestionCandidate::weightedScore).reversed()
                        .thenComparing(Comparator.comparingInt(this::criticalRank).reversed())
                        .thenComparing(candidate -> normalize(candidate.question().semanticKey()))
                        .thenComparing(candidate -> candidate.question().id()))
                .toList();
        Set<String> primaryKeys = new HashSet<>();
        Set<String> normalizedTexts = new HashSet<>();
        List<ClarificationQuestion> selected = new ArrayList<>();
        for (QuestionCandidate candidate : ordered) {
            ClarificationQuestion question = candidate.question();
            String primaryKey = normalize(question.dimension().name()) + '|'
                    + normalize(question.targetField()) + '|'
                    + normalize(question.semanticKey());
            String textKey = normalize(question.text());
            if (!primaryKeys.add(primaryKey) || !normalizedTexts.add(textKey)) {
                continue;
            }
            selected.add(question.withPriority(candidate.weightedScore()));
            if (selected.size() == limit) {
                break;
            }
        }
        return List.copyOf(selected);
    }

    private int criticalRank(QuestionCandidate candidate) {
        ClarificationQuestion question = candidate.question();
        String source = (question.targetField() + ' '
                + question.semanticKey() + ' '
                + question.text()).toLowerCase(Locale.ROOT);
        return CRITICAL_KEYWORDS.stream().anyMatch(source::contains) ? 1 : 0;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]", "");
    }
}
