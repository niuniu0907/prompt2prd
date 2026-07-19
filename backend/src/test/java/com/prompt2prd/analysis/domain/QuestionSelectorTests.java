package com.prompt2prd.analysis.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionSelectorTests {

    private static final UUID PROJECT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID BATCH_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");
    private final QuestionSelector selector = new QuestionSelector();

    @Test
    void sortsByTheFixedWeightedFormula() {
        QuestionCandidate highImpact = candidate("high", "高影响问题", "field-a", 5, 3, 2, 2);
        QuestionCandidate highGap = candidate("gap", "高缺口问题", "field-b", 3, 5, 2, 2);
        QuestionCandidate low = candidate("low", "低分问题", "field-c", 1, 1, 1, 1);

        List<ClarificationQuestion> selected = selector.select(
                List.of(low, highGap, highImpact));

        assertThat(selected).extracting(ClarificationQuestion::semanticKey)
                .containsExactly("high", "gap", "low");
        assertThat(selected).extracting(ClarificationQuestion::priority)
                .containsExactly(3.5, 3.3, 1.0);
    }

    @Test
    void primaryKeyNormalizesDimensionTargetAndSemanticKey() {
        QuestionCandidate first = candidate("Provider-Type", "问题A", "roles.provider_type", 5, 5, 5, 5);
        QuestionCandidate duplicate = candidate(" provider type ", "问题B", "Roles / Provider Type", 1, 1, 1, 1);

        List<ClarificationQuestion> selected = selector.select(List.of(first, duplicate));

        assertThat(selected).singleElement().satisfies(question ->
                assertThat(question.text()).isEqualTo("问题A"));
    }

    @Test
    void normalizedExactTextIsTheSecondDeduplicationBarrier() {
        QuestionCandidate first = candidate("one", "是否支持在线支付？", "payments.enabled", 5, 4, 3, 5);
        QuestionCandidate duplicateText = candidate("two", " 是否支持 在线支付? ", "checkout.payment", 4, 4, 4, 4);

        assertThat(selector.select(List.of(first, duplicateText))).hasSize(1);
    }

    @Test
    void similarTextWithDifferentTargetsRemainsDistinct() {
        QuestionCandidate buyer = candidate("buyer-auth", "买家如何认证？", "buyer.auth", 4, 4, 4, 4);
        QuestionCandidate seller = candidate("seller-auth", "卖家如何认证？", "seller.auth", 4, 4, 4, 4);

        assertThat(selector.select(List.of(buyer, seller))).hasSize(2);
    }

    @Test
    void returnsAtMostTenButAllowsFewerThanFive() {
        ArrayList<QuestionCandidate> many = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            many.add(candidate("key-" + index, "问题" + index, "field-" + index,
                    5, 5, 5, 5));
        }

        assertThat(selector.select(many)).hasSize(10);
        assertThat(selector.select(many.subList(0, 3))).hasSize(3);
        assertThatThrownBy(() -> selector.select(many, 11))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void transactionPermissionSecurityAndCoreStateWinEqualScoreTies() {
        QuestionCandidate generic = candidate("color", "主题颜色是什么？", "ui.color", 4, 4, 4, 4);
        QuestionCandidate security = candidate("auth", "权限与安全校验如何处理？", "security.auth", 4, 4, 4, 4);
        QuestionCandidate payment = candidate("payment", "支付交易失败后订单状态是什么？", "order.payment.state", 4, 4, 4, 4);

        List<ClarificationQuestion> selected = selector.select(
                List.of(generic, security, payment));

        assertThat(selected.getFirst().semanticKey()).isIn("auth", "payment");
        assertThat(selected.getLast().semanticKey()).isEqualTo("color");
    }

    private QuestionCandidate candidate(
            String semanticKey,
            String text,
            String target,
            int impact,
            int gap,
            int dependencies,
            int risk) {
        ClarificationQuestion question = new ClarificationQuestion(
                UUID.randomUUID(), PROJECT_ID, BATCH_ID, 0, text, "reason",
                RequirementDimension.ROLES_PERMISSIONS, target, semanticKey,
                QuestionInputType.TEXT, List.of(), List.of(), 0, QuestionStatus.PENDING, NOW, NOW);
        return new QuestionCandidate(question, impact, gap, dependencies, risk);
    }
}
