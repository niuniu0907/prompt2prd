package com.prompt2prd.architecture.application;

import com.prompt2prd.architecture.api.TechnicalConstraintsRequest;
import com.prompt2prd.architecture.domain.ArchitectureCandidate;
import com.prompt2prd.architecture.domain.ArchitectureCandidate.ScoreDimension;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public final class ArchitectureRecommender {

    public List<ArchitectureCandidate> recommend(TechnicalConstraintsRequest constraints) {
        List<ArchitectureCandidate> candidates = new ArrayList<>();
        candidates.add(vueSpring(constraints));
        candidates.add(fullStackTypeScript(constraints));
        if (constraints.userScale() == TechnicalConstraintsRequest.UserScale.LARGE
                || constraints.deployment() == TechnicalConstraintsRequest.Deployment.MULTI_INSTANCE) {
            candidates.add(cloudModular(constraints));
        } else {
            candidates.add(serverRenderedJava(constraints));
        }
        UUID selectedId = candidates.stream()
                .max(Comparator.comparingInt(ArchitectureCandidate::totalScore)
                        .thenComparing(candidate -> candidate.name().equals("Vue 3 + Spring Boot 单体") ? 1 : 0))
                .orElseThrow()
                .id();
        return candidates.stream().map(candidate -> candidate.recommended(candidate.id().equals(selectedId))).toList();
    }

    private ArchitectureCandidate vueSpring(TechnicalConstraintsRequest c) {
        EnumMap<ScoreDimension, Integer> scores = baseScores(4, 4, 5, 4, 5, 4, 5);
        adjust(scores, ScoreDimension.LEARNING_COST, knowsAny(c, "vue", "java", "spring") ? 1 : -1);
        adjust(scores, ScoreDimension.DEPLOYMENT_SIMPLICITY,
                c.deployment() == TechnicalConstraintsRequest.Deployment.MONOLITHIC_DOCKER ? 1 : 0);
        adjust(scores, ScoreDimension.MAINTAINABILITY,
                c.teamSize() == TechnicalConstraintsRequest.TeamSize.SOLO ? 1 : 0);
        return candidate(c, "vue-spring", "Vue 3 + Spring Boot 单体", scores,
                stack("Vue 3 + TypeScript", "Spring Boot WebFlux", "IndexedDB / PostgreSQL 按需求选择", "会话或 Token 鉴权", "本地文件或对象存储适配", "Spring AI", "单 JAR / 单体 Docker", "Vitest + JUnit + Playwright"),
                List.of("Vue 负责交互与本地状态", "Spring Boot 负责 API、AI 调用与业务校验", "单体制品负责同源部署"),
                List.of("贴合 Vue、Java 与 Spring Boot 技能", "单体部署和本地调试路径清晰", "coding agent 对两端生态支持成熟"),
                List.of("需要维护前后端两种语言", "复杂实时协作需要额外设计"),
                List.of("不适合一开始就拆微服务", "多实例共享状态需要外部存储"),
                List.of("若团队只会 TypeScript，可选择全栈 TypeScript 方案"));
    }

    private ArchitectureCandidate fullStackTypeScript(TechnicalConstraintsRequest c) {
        EnumMap<ScoreDimension, Integer> scores = baseScores(3, 5, 4, 4, 4, 4, 5);
        adjust(scores, ScoreDimension.LEARNING_COST, knowsAny(c, "typescript", "node", "nestjs") ? 1 : -1);
        adjust(scores, ScoreDimension.MAINTAINABILITY, c.knows("java") && !c.knows("node") ? -1 : 0);
        return candidate(c, "fullstack-typescript", "Vue 3 + NestJS 全栈 TypeScript", scores,
                stack("Vue 3 + TypeScript", "NestJS", "PostgreSQL / IndexedDB", "Passport / Token", "对象存储适配", "OpenAI 兼容 SDK", "Node.js Docker", "Vitest + Playwright"),
                List.of("Vue 负责界面", "NestJS 负责 API 与模型编排", "共享 TypeScript 契约降低 DTO 重复"),
                List.of("前后端语言统一", "原型开发速度快", "npm 生态丰富"),
                List.of("Java 团队需要学习 Node 服务治理", "计算密集任务需额外隔离"),
                List.of("不因技术热度自动优先", "现有 Java 基础强时迁移收益有限"),
                List.of("当前约束包含 Java/Spring Boot 时，学习收益低于复用现有技能"));
    }

    private ArchitectureCandidate serverRenderedJava(TechnicalConstraintsRequest c) {
        EnumMap<ScoreDimension, Integer> scores = baseScores(4, 3, 5, 5, 4, 3, 4);
        adjust(scores, ScoreDimension.LEARNING_COST, c.knows("java") ? 1 : -1);
        adjust(scores, ScoreDimension.DEVELOPMENT_SPEED,
                c.targetPlatform() == TechnicalConstraintsRequest.TargetPlatform.WEB ? 1 : -1);
        return candidate(c, "server-rendered-java", "Spring Boot 服务端渲染", scores,
                stack("Thymeleaf + 少量 TypeScript", "Spring Boot MVC/WebFlux", "PostgreSQL / H2", "Spring Security（需要登录时）", "本地文件或对象存储", "Spring AI", "单 JAR / Docker", "JUnit + Playwright"),
                List.of("Spring Boot 同时渲染页面并提供业务接口", "服务端集中处理状态与模型调用"),
                List.of("部署单纯", "Java 开发者维护成本低", "运行资源可控"),
                List.of("复杂工作台交互开发效率较低", "前端生态能力受限"),
                List.of("不适合高度交互的离线优先产品", "移动端仍需独立客户端"),
                List.of("当前产品若需要复杂 Vue 工作台，服务端渲染体验不占优"));
    }

    private ArchitectureCandidate cloudModular(TechnicalConstraintsRequest c) {
        EnumMap<ScoreDimension, Integer> scores = baseScores(2, 2, 2, 2, 3, 5, 4);
        adjust(scores, ScoreDimension.SCALABILITY,
                c.deployment() == TechnicalConstraintsRequest.Deployment.MULTI_INSTANCE ? 0 : -1);
        return candidate(c, "cloud-modular", "Vue + Spring Boot 模块化云部署", scores,
                stack("Vue 3 + TypeScript", "Spring Boot 模块化单体", "PostgreSQL + Redis", "OIDC", "对象存储", "Spring AI", "多实例容器平台", "JUnit + Vitest + 契约测试"),
                List.of("前端静态部署", "后端模块化单体水平扩展", "外部存储提供多实例一致性"),
                List.of("扩展能力强", "适合较大用户量和多实例"),
                List.of("部署和排障成本高", "个人项目容易过度设计"),
                List.of("需要云平台、共享数据库和缓存", "预算极低时不合适"),
                List.of("非多实例约束下不应提前承担基础设施成本"));
    }

    private ArchitectureCandidate candidate(
            TechnicalConstraintsRequest constraints,
            String key,
            String name,
            EnumMap<ScoreDimension, Integer> scores,
            Map<String, String> stack,
            List<String> responsibilities,
            List<String> advantages,
            List<String> disadvantages,
            List<String> limitations,
            List<String> unselectedReasons) {
        int total = scores.values().stream().mapToInt(Integer::intValue).sum();
        UUID id = UUID.nameUUIDFromBytes((constraints.projectId() + ":" + key).getBytes(StandardCharsets.UTF_8));
        return new ArchitectureCandidate(id, name, stack, responsibilities, advantages, disadvantages,
                limitations, unselectedReasons, scores, total, false);
    }

    private Map<String, String> stack(String frontend, String backend, String storage, String auth,
                                      String file, String ai, String deployment, String testing) {
        LinkedHashMap<String, String> stack = new LinkedHashMap<>();
        stack.put("frontend", frontend);
        stack.put("backend", backend);
        stack.put("storage", storage);
        stack.put("authentication", auth);
        stack.put("fileStorage", file);
        stack.put("ai", ai);
        stack.put("deployment", deployment);
        stack.put("testing", testing);
        return stack;
    }

    private EnumMap<ScoreDimension, Integer> baseScores(int learning, int speed, int deployment,
                                                        int cost, int maintenance, int scale, int ai) {
        EnumMap<ScoreDimension, Integer> scores = new EnumMap<>(ScoreDimension.class);
        scores.put(ScoreDimension.LEARNING_COST, learning);
        scores.put(ScoreDimension.DEVELOPMENT_SPEED, speed);
        scores.put(ScoreDimension.DEPLOYMENT_SIMPLICITY, deployment);
        scores.put(ScoreDimension.RUNNING_COST, cost);
        scores.put(ScoreDimension.MAINTAINABILITY, maintenance);
        scores.put(ScoreDimension.SCALABILITY, scale);
        scores.put(ScoreDimension.AI_SUPPORT, ai);
        return scores;
    }

    private void adjust(EnumMap<ScoreDimension, Integer> scores, ScoreDimension dimension, int delta) {
        scores.computeIfPresent(dimension, (ignored, value) -> Math.max(1, Math.min(5, value + delta)));
    }

    private boolean knowsAny(TechnicalConstraintsRequest constraints, String... technologies) {
        for (String technology : technologies) if (constraints.knows(technology)) return true;
        return false;
    }
}
