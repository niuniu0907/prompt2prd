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
        sections.put("coding-agent-guide", "内容");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.valid()).isFalse();
        assertThat(report.errors()).anyMatch(error -> error.contains("缺少必需章节"));
    }

    @Test
    void detectsMissingStableIds() {
        Map<String, String> sections = allSectionsWith("");
        sections.put("user-stories", "用户故事章节，但没有 US-xxx 编号。");
        sections.put("rules-exceptions", "业务规则章节，但没有 BR-xxx 编号。");
        sections.put("acceptance", "验收：Given 用户登录 When 点击按钮 Then 显示结果。");
        sections.put("implementation-phases", "PHASE-001 基础框架搭建");
        var report = PrdValidator.validate(sections, null);
        assertThat(report.errors()).anyMatch(error -> error.contains("US-xxx"));
        assertThat(report.errors()).anyMatch(error -> error.contains("BR-xxx"));
    }

    @Test
    void validDocumentPasses() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nAPI-001 接口\nPAGE-001 页面\nAC-001 验收条件\nPHASE-001 阶段一");
        sections.put("acceptance",
                "AC-001 Given 用户已登录 When 用户点击提交按钮 Then 系统显示成功消息。");
        sections.put("architecture", "使用 Vue 3 + Spring Boot 单体架构，ID: arch-1");
        sections.put("implementation-phases",
                "PHASE-001 基础框架搭建：搭建 Vue 3 + Spring Boot 项目骨架。");
        var report = PrdValidator.validate(sections, "arch-1");
        assertThat(report.valid()).isTrue();
        assertThat(report.errors()).isEmpty();
    }

    @Test
    void warnsOnCandidateArchitectureLeak() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nAPI-001 接口\nPAGE-001 页面\nAC-001 验收条件\nPHASE-001 阶段一");
        sections.put("acceptance",
                "AC-001 Given 用户已登录 When 点击按钮 Then 显示结果。");
        sections.put("architecture", "使用备选架构 Vue 3 + Spring Boot 单体。");
        sections.put("implementation-phases", "PHASE-001 基础框架搭建。");
        var report = PrdValidator.validate(sections, "arch-1");
        assertThat(report.warnings()).anyMatch(w -> w.contains("备选架构"));
    }

    @Test
    void emptyArchitectureSectionIsError() {
        Map<String, String> sections = allSectionsWith(
                "REQ-001 核心功能\nUS-001 用户故事\nBR-001 业务规则\nAPI-001 接口\nPAGE-001 页面\nAC-001 验收条件\nPHASE-001 阶段一");
        sections.put("acceptance",
                "AC-001 Given 用户已登录 When 点击按钮 Then 显示结果。");
        sections.put("architecture", "");
        sections.put("implementation-phases", "PHASE-001 基础框架搭建。");
        var report = PrdValidator.validate(sections, "arch-1");
        assertThat(report.errors()).anyMatch(error -> error.contains("架构章节为空"));
    }

    private Map<String, String> allSectionsWith(String content) {
        Map<String, String> sections = new HashMap<>();
        for (PrdDefinition.Section section : PrdDefinition.sections()) {
            sections.put(section.key().wireName(), content);
        }
        return sections;
    }
}
