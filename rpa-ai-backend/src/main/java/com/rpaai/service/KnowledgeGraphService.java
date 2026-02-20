package com.rpaai.service;

import com.rpaai.entity.RpaStep;
import com.rpaai.entity.neo4j.ExceptionCase;
import com.rpaai.entity.neo4j.ElementPattern;
import com.rpaai.repository.neo4j.ExceptionCaseRepository;
import com.rpaai.repository.neo4j.ElementPatternRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class KnowledgeGraphService {

    @Autowired
    private ExceptionCaseRepository exceptionCaseRepository;

    @Autowired
    private ElementPatternRepository elementPatternRepository;

    /**
     * 根据异常查找解决方案
     */
    public Optional<String> findSolution(Exception exception, RpaStep failedStep, String currentUrl) {
        String errorType = exception.getClass().getSimpleName();
        String errorMessage = exception.getMessage();

        log.info("🔍 在知识图谱中查找解决方案: {} - {}", errorType, errorMessage);

        // 1. 精确匹配：相同异常类型 + 相同操作
        List<ExceptionCase> similarCases = exceptionCaseRepository.findSimilarCases(
                errorType,
                failedStep.getAction()
        );

        if (!similarCases.isEmpty()) {
            ExceptionCase bestCase = similarCases.get(0);
            bestCase.setLastUsedTime(LocalDateTime.now());
            exceptionCaseRepository.save(bestCase);

            log.info("✅ 找到历史解决方案 (使用{}次): {}",
                    bestCase.getSuccessCount(), bestCase.getSolution());
            return Optional.of(bestCase.getSolution());
        }

        // 2. 模糊匹配：异常信息关键词
        String keyword = extractKeyword(errorMessage);
        if (keyword != null && !keyword.isEmpty()) {
            List<ExceptionCase> fuzzyCases = exceptionCaseRepository.searchByKeyword(keyword);
            if (!fuzzyCases.isEmpty()) {
                log.info("✅ 找到模糊匹配方案: {}", fuzzyCases.get(0).getSolution());
                return Optional.of(fuzzyCases.get(0).getSolution());
            }
        }

        // 3. 根据页面类型和元素类型查找成功模式
        String pageType = inferPageType(currentUrl);
        Optional<ElementPattern> pattern = elementPatternRepository
                .findByPageTypeAndElementType(pageType, failedStep.getAction());

        if (pattern.isPresent()) {
            ElementPattern pat = pattern.get();
            if (pat.getSuccessRate() > 0.7) {  // 成功率>70%
                log.info("✅ 找到可靠元素模式 (成功率{}%): {}",
                        pat.getSuccessRate() * 100, pat.getSuccessfulSelector());
                return Optional.of("尝试使用备选定位: " + pat.getSuccessfulSelector());
            }
        }

        log.warn("❌ 知识图谱中未找到解决方案");
        return Optional.empty();
    }

    /**
     * 记录成功的解决方案
     */
    public void recordSuccessSolution(Exception exception, RpaStep step,
                                      String solution, String currentUrl) {
        String errorType = exception != null ? exception.getClass().getSimpleName() : "UNKNOWN";

        ExceptionCase newCase = new ExceptionCase();
        newCase.setErrorType(errorType);
        newCase.setErrorMessage(exception != null ? exception.getMessage() : "");
        newCase.setPageUrl(currentUrl);
        newCase.setAction(step.getAction());
        newCase.setTarget(step.getTarget());
        newCase.setSolution(solution);
        newCase.setSuccessCount(1);
        newCase.setLastUsedTime(LocalDateTime.now());

        exceptionCaseRepository.save(newCase);
        log.info("💾 记录新的解决方案到知识图谱: {}", solution);
    }

    /**
     * 记录元素使用模式
     */
    public void recordElementPattern(String pageUrl, String action,
                                     String successfulSelector, List<String> alternatives) {
        String pageType = inferPageType(pageUrl);

        Optional<ElementPattern> existing = elementPatternRepository
                .findByPageTypeAndElementType(pageType, action);

        if (existing.isPresent()) {
            // 更新现有模式
            ElementPattern pattern = existing.get();
            pattern.setUsageCount(pattern.getUsageCount() + 1);
            pattern.setLastSuccessTime(LocalDateTime.now());
            // 更新成功率
            double newRate = (pattern.getSuccessRate() * (pattern.getUsageCount() - 1) + 1)
                    / pattern.getUsageCount();
            pattern.setSuccessRate(newRate);
            elementPatternRepository.save(pattern);
        } else {
            // 创建新模式
            ElementPattern pattern = new ElementPattern();
            pattern.setPageType(pageType);
            pattern.setElementType(action);
            pattern.setSuccessfulSelector(successfulSelector);
            pattern.setAlternativeSelectors(String.join(",", alternatives));
            pattern.setUsageCount(1);
            pattern.setSuccessRate(1.0);
            pattern.setLastSuccessTime(LocalDateTime.now());
            elementPatternRepository.save(pattern);
        }
    }

    /**
     * 获取智能建议的备选定位
     */
    public List<String> suggestAlternativeLocators(String pageUrl, String action, String originalTarget) {
        String pageType = inferPageType(pageUrl);

        Optional<ElementPattern> pattern = elementPatternRepository
                .findByPageTypeAndElementType(pageType, action);

        if (pattern.isPresent()) {
            String alts = pattern.get().getAlternativeSelectors();
            if (alts != null && !alts.isEmpty()) {
                return Arrays.asList(alts.split(","));
            }
        }

        // 默认备选策略
        return generateDefaultAlternatives(originalTarget);
    }

    /**
     * 统计知识图谱数据
     */
    public Map<String, Object> getKnowledgeStats() {
        Map<String, Object> stats = new HashMap<>();

        long exceptionCount = exceptionCaseRepository.count();
        long patternCount = elementPatternRepository.count();

        List<ExceptionCase> topSolutions = exceptionCaseRepository.findTopSolutions();

        stats.put("exceptionCases", exceptionCount);
        stats.put("elementPatterns", patternCount);
        stats.put("topSolutions", topSolutions.stream()
                .map(c -> Map.of(
                        "errorType", c.getErrorType(),
                        "solution", c.getSolution(),
                        "successCount", c.getSuccessCount()
                ))
                .collect(Collectors.toList()));

        return stats;
    }

    // ============ 私有工具方法 ============

    private String extractKeyword(String errorMessage) {
        if (errorMessage == null) return "";

        // 提取关键错误信息
        if (errorMessage.contains("no such element")) return "no such element";
        if (errorMessage.contains("timeout")) return "timeout";
        if (errorMessage.contains("stale element")) return "stale element";
        if (errorMessage.contains("click intercepted")) return "click intercepted";
        if (errorMessage.contains("unable to locate")) return "unable to locate";

        return errorMessage.length() > 50 ?
                errorMessage.substring(0, 50) : errorMessage;
    }

    private String inferPageType(String url) {
        if (url == null) return "unknown";

        String lower = url.toLowerCase();
        if (lower.contains("login") || lower.contains("signin")) return "login";
        if (lower.contains("search") || lower.contains("query")) return "search";
        if (lower.contains("form") || lower.contains("submit")) return "form";
        if (lower.contains("baidu")) return "search";
        if (lower.contains("google")) return "search";

        return "general";
    }

    private List<String> generateDefaultAlternatives(String original) {
        List<String> alternatives = new ArrayList<>();

        // CSS选择器变种
        if (original.startsWith("#")) {
            String id = original.substring(1);
            alternatives.add("[id='" + id + "']");
            alternatives.add("*[id='" + id + "']");
        }

        // 添加通用备选
        alternatives.add("input[type='submit']");
        alternatives.add("button[type='submit']");
        alternatives.add("form button");

        return alternatives;
    }
}