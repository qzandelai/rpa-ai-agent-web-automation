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

    @Autowired(required = false)  // 改为非强制依赖
    private ExceptionCaseRepository exceptionCaseRepository;

    @Autowired(required = false)  // 改为非强制依赖
    private ElementPatternRepository elementPatternRepository;

    /**
     * 根据异常查找解决方案（添加容错处理）
     */
    public Optional<String> findSolution(Exception exception, RpaStep failedStep, String currentUrl) {
        // 如果Neo4j未配置，直接返回空
        if (exceptionCaseRepository == null || elementPatternRepository == null) {
            log.warn("⚠️ 知识图谱服务未初始化，跳过智能诊断");
            return Optional.empty();
        }

        try {
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
        } catch (Exception e) {
            log.error("❌ 知识图谱查询失败（不影响主流程）: {}", e.getMessage());
            return Optional.empty();  // 关键：不要让知识图谱错误影响主流程
        }
    }

    /**
     * 记录成功的解决方案（添加容错）
     */
    public void recordSuccessSolution(Exception exception, RpaStep step,
                                      String solution, String currentUrl) {
        if (exceptionCaseRepository == null) return;

        try {
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
        } catch (Exception e) {
            log.error("❌ 记录解决方案失败: {}", e.getMessage());
        }
    }

    /**
     * 记录元素使用模式（添加容错）
     */
    public void recordElementPattern(String pageUrl, String action,
                                     String successfulSelector, List<String> alternatives) {
        if (elementPatternRepository == null) return;

        try {
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
        } catch (Exception e) {
            log.error("❌ 记录元素模式失败: {}", e.getMessage());
        }
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

    public Map<String, Object> getKnowledgeStats() {
        Map<String, Object> stats = new HashMap<>();

        if (exceptionCaseRepository == null || elementPatternRepository == null) {
            stats.put("exceptionCases", 0);
            stats.put("elementPatterns", 0);
            stats.put("topSolutions", new ArrayList<>());
            stats.put("status", "未连接");
            return stats;
        }

        try {
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
            stats.put("status", "运行中");
        } catch (Exception e) {
            log.error("获取知识图谱统计失败: {}", e.getMessage());
            stats.put("exceptionCases", 0);
            stats.put("elementPatterns", 0);
            stats.put("topSolutions", new ArrayList<>());
            stats.put("status", "错误: " + e.getMessage());
        }

        return stats;
    }
}