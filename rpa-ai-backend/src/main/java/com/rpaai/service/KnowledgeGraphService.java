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

    @Autowired(required = false)
    private ExceptionCaseRepository exceptionCaseRepository;

    @Autowired(required = false)
    private ElementPatternRepository elementPatternRepository;

    /**
     * 强制测试连接，返回详细错误信息
     */
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();

        if (exceptionCaseRepository == null) {
            result.put("status", "not_configured");
            result.put("message", "知识图谱服务未启用（缺少 Repository 配置）");
            return result;
        }

        try {
            // 强制触发实际连接（会立即尝试连接 Neo4j）
            long count = exceptionCaseRepository.count();
            result.put("status", "connected");
            result.put("exceptionCount", count);
            result.put("message", "连接成功");
        } catch (Exception e) {
            // 打印完整错误到控制台
            log.error("Neo4j 连接详细错误: ", e);
            result.put("status", "error");
            result.put("errorType", e.getClass().getSimpleName());
            result.put("message", e.getMessage());

            // 如果是认证错误，给提示
            if (e.getMessage() != null && (e.getMessage().contains("auth") || e.getMessage().contains("credentials"))) {
                result.put("hint", "认证失败！检查 application.yml 中的 password 是否和 Docker 设置一致");
            }
        }

        return result;
    }

    /**
     * 获取知识图谱统计（修复版：避免实体映射错误）
     */
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
            // 只用 count() 避免映射问题
            long exceptionCount = exceptionCaseRepository.count();
            long patternCount = elementPatternRepository.count();

            stats.put("exceptionCases", exceptionCount);
            stats.put("elementPatterns", patternCount);
            stats.put("status", "运行中");

            // 为避免实体映射错误（如 similarCases 关系映射失败），
            // stats 接口不再返回完整实体列表，前端请调用 /api/kg/cases 获取真实案例
            stats.put("topSolutions", new ArrayList<>());

        } catch (Exception e) {
            log.error("获取知识图谱统计失败: {}", e.getMessage());
            stats.put("exceptionCases", 0);
            stats.put("elementPatterns", 0);
            stats.put("topSolutions", new ArrayList<>());
            stats.put("status", "错误: " + e.getMessage());
        }

        return stats;
    }

    /**
     * 获取所有元素模式
     */
    public List<ElementPattern> getAllPatterns() {
        if (elementPatternRepository == null) return new ArrayList<>();
        try {
            return elementPatternRepository.findAllPatterns();
        } catch (Exception e) {
            log.error("获取元素模式失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 获取所有异常案例（最近50条）
     */
    public List<ExceptionCase> getAllCases() {
        if (exceptionCaseRepository == null) return new ArrayList<>();
        try {
            // 由于 findTopSolutions 是 LIMIT 10，这里用自定义查询或直接用 findAll 再截断
            List<ExceptionCase> all = new ArrayList<>();
            exceptionCaseRepository.findAll().forEach(all::add);
            all.sort((a, b) -> {
                LocalDateTime t1 = a.getLastUsedTime() != null ? a.getLastUsedTime() : a.getCreateTime();
                LocalDateTime t2 = b.getLastUsedTime() != null ? b.getLastUsedTime() : b.getCreateTime();
                if (t1 == null) return 1;
                if (t2 == null) return -1;
                return t2.compareTo(t1);
            });
            return all.stream().limit(50).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("获取异常案例失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 删除元素模式
     */
    public boolean deletePattern(String id) {
        if (elementPatternRepository == null) return false;
        try {
            elementPatternRepository.deleteById(id);
            return true;
        } catch (Exception e) {
            log.error("删除元素模式失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 查找元素模式（供 AI 解析时注入经验）
     */
    public Optional<ElementPattern> findPattern(String pageType, String action) {
        if (elementPatternRepository == null) return Optional.empty();
        try {
            return elementPatternRepository.findByPageTypeAndElementType(pageType, action);
        } catch (Exception e) {
            log.error("查找元素模式失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

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

            // 3. 根据页面类型和元素类型查找成功模式（优先视觉定位）
            String pageType = inferPageType(currentUrl);

            // 3.1 先查有图像模板的成功模式
            Optional<ElementPattern> visualPattern = elementPatternRepository
                    .findVisualPattern(pageType, failedStep.getAction());
            if (visualPattern.isPresent()) {
                ElementPattern pat = visualPattern.get();
                if (pat.getSuccessRate() > 0.6) {
                    log.info("✅ 找到视觉定位模式 (成功率{}%): 页面={}, 动作={}",
                            pat.getSuccessRate() * 100, pageType, failedStep.getAction());
                    return Optional.of("视觉定位:IMG:" + pat.getImageTemplate() +
                            ":THR:" + (pat.getImageThreshold() != null ? pat.getImageThreshold() : 0.8));
                }
            }

            // 3.2 再查普通选择器模式
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
     * 记录元素使用模式（添加容错，支持图像模板）
     */
    public void recordElementPattern(String pageUrl, String action,
                                     String successfulSelector, List<String> alternatives,
                                     String imageTemplate, Double imageThreshold) {
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
                // 如果有新的图像模板，更新它
                if (imageTemplate != null && !imageTemplate.isEmpty()) {
                    pattern.setImageTemplate(imageTemplate);
                    pattern.setImageThreshold(imageThreshold != null ? imageThreshold : 0.8);
                }
                elementPatternRepository.save(pattern);
                log.info("📚 更新知识图谱元素模式: {}-{} (使用率{}次, 成功率{}%)",
                        pageType, action, pattern.getUsageCount(), Math.round(newRate * 100));
            } else {
                // 创建新模式
                ElementPattern pattern = new ElementPattern();
                pattern.setPageType(pageType);
                pattern.setElementType(action);
                pattern.setSuccessfulSelector(successfulSelector);
                pattern.setAlternativeSelectors(alternatives != null ? String.join(",", alternatives) : "");
                pattern.setUsageCount(1);
                pattern.setSuccessRate(1.0);
                pattern.setLastSuccessTime(LocalDateTime.now());
                pattern.setImageTemplate(imageTemplate);
                pattern.setImageThreshold(imageThreshold != null ? imageThreshold : 0.8);
                elementPatternRepository.save(pattern);
                log.info("📚 记录新元素模式到知识图谱: {}-{} (含图像={})",
                        pageType, action, imageTemplate != null);
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
        if (errorMessage.contains("未找到")) return "未找到";

        return errorMessage.length() > 50 ?
                errorMessage.substring(0, 50) : errorMessage;
    }

    public String inferPageType(String url) {
        if (url == null) return "unknown";

        String lower = url.toLowerCase();
        if (lower.contains("login") || lower.contains("signin") || lower.contains("登录")) return "login";
        if (lower.contains("search") || lower.contains("query") || lower.contains("s?wd=")) return "search";
        if (lower.contains("form") || lower.contains("submit")) return "form";
        if (lower.contains("baidu")) return "search";
        if (lower.contains("google")) return "search";
        if (lower.contains("bing")) return "search";
        if (lower.contains("taobao") || lower.contains("tmall") || lower.contains("淘宝")) return "ecommerce";
        if (lower.contains("jd") || lower.contains("京东")) return "ecommerce";
        if (lower.contains("bilibili") || lower.contains("b23.tv")) return "video";
        if (lower.contains("github")) return "dev";
        if (lower.contains("zhihu")) return "social";
        if (lower.contains("weibo")) return "social";

        return "general";
    }
}
