package com.prompt2prd.generation.prd;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PrdValidatorTests {

    @Test
    void rejectsEmptySections() {
        var report = PrdValidator.validate(Map.of(), null);
        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(error -> error.contains("至少需要一个章节"));
    }

    @Test
    void requiresAllSectionsPresent() {
        Map<String, String> sections = new HashMap<>();
        sections.put("product-background-goals", "内容");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(error -> error.contains("缺少必需章节"));
    }

    @Test
    void detectsMissingStableIds() {
        Map<String, String> sections = allSectionsWith("");
        sections.put("user-stories", "用户故事章节，但没有 US-xxx 编号。");
        sections.put("business-rules", "业务规则章节，但没有 BR-xxx 编号。");
        sections.put("acceptance-criteria", "验收：Given 用户登录 When 点击按钮 Then 显示结果。");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.errors()).anyMatch(error -> error.contains("US-xxx"));
        assertThat(report.errors()).anyMatch(error -> error.contains("BR-xxx"));
    }

    @Test
    void validDocumentPasses() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nPAGE-001 页面\nAC-001 验收条件");
        sections.put("acceptance-criteria",
                "AC-001 Given 用户已登录 When 用户点击提交按钮 Then 系统显示成功消息。");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void warnsOnCandidateArchitectureLeak() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nPAGE-001 页面\nAC-001 验收条件");
        sections.put("acceptance-criteria",
                "AC-001 Given 用户已登录 When 点击按钮 Then 显示结果。");
        sections.put("risks-assumptions-open-items", "该需求不包含备选架构正文。");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.warnings()).anyMatch(w -> w.contains("技术方案"));
    }

    @Test
    void emptyTechnicalDecisionSummaryIsAllowed() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nPAGE-001 页面\nAC-001 验收条件");
        sections.put("acceptance-criteria",
                "AC-001 Given 用户已登录 When 点击按钮 Then 显示结果。");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void warnsWhenTechnicalSummaryContainsDetailedArchitecture() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nPAGE-001 页面\nAC-001 验收条件");
        sections.put("acceptance-criteria",
                "AC-001 Given 用户已登录 When 点击按钮 Then 显示结果。");
        sections.put("risks-assumptions-open-items", "Vue 3 + Spring Boot，总分 30/35，学习成本 5/5，未选择原因：其他方案不匹配。");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.warnings()).anyMatch(warning -> warning.contains("技术方案"));
    }

    private Map<String, String> allSectionsWith(String content) {
        Map<String, String> sections = new HashMap<>();
        for (PrdDefinition.Section section : PrdDefinition.sections()) {
            sections.put(section.key().wireName(), content);
        }
        return sections;
    }
}
