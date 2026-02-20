package com.rpaai.core.rpa;

import com.rpaai.entity.RpaStep;
import com.rpaai.service.KnowledgeGraphService;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class RpaExecutionEngine {

    private WebDriver driver;
    private WebDriverWait wait;

    @Autowired
    private ChromeOptions chromeOptions;

    @Autowired
    private KnowledgeGraphService knowledgeGraphService;


    /**
     * 初始化浏览器
     */
    public void initBrowser() {
        // 如果已有浏览器实例，先关闭
        if (driver != null) {
            log.info("关闭旧的浏览器实例");
            try {
                driver.quit();
            } catch (Exception e) {
                // 忽略关闭错误
            }
            driver = null;
        }

        log.info("🚀 初始化Chrome浏览器");

        try {
            driver = new ChromeDriver(chromeOptions);
            wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            log.info("✅ 浏览器初始化完成");
        } catch (Exception e) {
            log.error("❌ 浏览器初始化失败: {}", e.getMessage());
            throw new RuntimeException("浏览器初始化失败，请检查ChromeDriver配置: " + e.getMessage(), e);
        }
    }

    /**
     * 执行单步操作（带知识图谱智能修复）
     */
    public RpaStepResult executeStep(RpaStep step) {
        log.info("执行步骤 {}: {} - {}", step.getStepId(), step.getAction(), step.getDescription());

        int maxRetries = step.getRetryCount() != null ? step.getRetryCount() : 3;
        int attempt = 0;
        Exception lastException = null;

        // 主定位策略：重试
        while (attempt < maxRetries) {
            try {
                if (attempt > 0) {
                    log.info("第 {} 次重试步骤 {}", attempt + 1, step.getStepId());
                }
                return doExecuteStep(step);
            } catch (Exception e) {
                lastException = e;
                attempt++;

                if (attempt < maxRetries) {
                    // 指数退避
                    long waitMs = (long) Math.pow(2, attempt - 1) * 1000;
                    log.warn("步骤 {} 失败 (尝试 {}/{}): {}，等待 {}ms 后重试",
                            step.getStepId(), attempt, maxRetries, e.getMessage(), waitMs);

                    try {
                        Thread.sleep(waitMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return RpaStepResult.fail(step.getStepId(), "重试被中断");
                    }
                }
            }
        }

        // 🔥 主定位失败，查询知识图谱获取智能建议
        String currentUrl = getCurrentUrl();
        Optional<String> kgSolution = knowledgeGraphService.findSolution(
                lastException, step, currentUrl);

        if (kgSolution.isPresent()) {
            log.info("🧠 知识图谱提供解决方案: {}", kgSolution.get());

            // 解析并应用解决方案
            RpaStep fixedStep = applySolution(step, kgSolution.get());
            if (fixedStep != null) {
                try {
                    RpaStepResult result = doExecuteStep(fixedStep);

                    // 记录成功修复
                    knowledgeGraphService.recordSuccessSolution(
                            lastException, step, kgSolution.get(), currentUrl);

                    log.info("✅ 知识图谱方案执行成功");
                    return result;
                } catch (Exception e) {
                    log.error("❌ 知识图谱方案也失败: {}", e.getMessage());
                }
            }
        }

        // 备用定位策略
        if (step.getFallbackTarget() != null && !step.getFallbackTarget().isEmpty()) {
            log.info("尝试配置的备用定位: {}", step.getFallbackTarget());
            try {
                RpaStep fallbackStep = copyStepWithNewTarget(step, step.getFallbackTarget());
                return doExecuteStep(fallbackStep);
            } catch (Exception e) {
                log.error("备用定位失败: {}", e.getMessage());
            }
        }

        // 全部失败，记录到知识图谱（待后续学习）
        knowledgeGraphService.recordSuccessSolution(lastException, step,
                "待解决: " + lastException.getMessage(), currentUrl);

        String errorMsg = String.format("步骤 %d 失败（重试%d次）: %s",
                step.getStepId(), maxRetries, lastException.getMessage());
        log.error(errorMsg);

        return RpaStepResult.fail(step.getStepId(), errorMsg);
    }

    /**
     * 应用知识图谱的解决方案
     */
    private RpaStep applySolution(RpaStep originalStep, String solution) {
        // 解析解决方案
        if (solution.contains("备选定位:")) {
            String newTarget = solution.substring(solution.indexOf(":") + 1).trim();
            return copyStepWithNewTarget(originalStep, newTarget);
        }

        if (solution.contains("等待")) {
            // 添加等待步骤
            RpaStep waitStep = new RpaStep();
            waitStep.setStepId(originalStep.getStepId());
            waitStep.setAction("wait");
            waitStep.setWaitTime(3);
            waitStep.setDescription("知识图谱建议的等待");
            waitStep.setRequired(false);
            return waitStep;
        }

        return null;
    }

    /**
     * 实际执行步骤
     */
    private RpaStepResult doExecuteStep(RpaStep step) throws Exception {
        // 确保浏览器已初始化（除了close操作）
        if (!"close".equals(step.getAction()) && driver == null) {
            initBrowser();
        }

        long startTime = System.currentTimeMillis();
        RpaStepResult result;

        try {
            result = switch (step.getAction()) {
                case "open_url" -> executeOpenUrl(step);
                case "input" -> executeInput(step);
                case "click" -> executeClick(step);
                case "wait" -> executeWait(step);
                case "scroll" -> executeScroll(step);
                case "extract" -> executeExtract(step);
                case "submit" -> executeSubmit(step);
                case "screenshot" -> executeScreenshot(step);
                case "close" -> executeClose(step);
                default -> throw new UnsupportedOperationException("未知操作类型: " + step.getAction());
            };
        } catch (Exception e) {
            throw e;
        }

        long duration = System.currentTimeMillis() - startTime;
        result.setExecutionTimeMs(duration);

        return result;
    }

    /**
     * 复制步骤并修改target
     */
    private RpaStep copyStepWithNewTarget(RpaStep original, String newTarget) {
        RpaStep copy = new RpaStep();
        copy.setStepId(original.getStepId());
        copy.setAction(original.getAction());
        copy.setTarget(newTarget);
        copy.setValue(original.getValue());
        copy.setWaitTime(original.getWaitTime());
        copy.setDescription(original.getDescription() + " [备用定位]");
        copy.setRequired(original.getRequired());
        copy.setRetryCount(1);
        copy.setFallbackTarget(null);
        return copy;
    }

    /**
     * 执行完整任务
     */
    public RpaExecutionResult executeTask(List<RpaStep> steps) {
        log.info("🎯 开始执行任务，共 {} 步", steps.size());

        // ✅ 检查浏览器是否可用，不可用则重新初始化
        if (driver == null || !isBrowserAlive()) {
            log.info("浏览器未初始化或已关闭，重新初始化");
            closeBrowser(); // 清理残留
            initBrowser();
        }

        RpaExecutionResult result = new RpaExecutionResult();
        result.setTotalSteps(steps.size());
        result.setStepResults(new ArrayList<>());

        // 记录开始时间
        long taskStartTime = System.currentTimeMillis();

        try {
            for (int i = 0; i < steps.size(); i++) {
                RpaStep step = steps.get(i);
                RpaStepResult stepResult = executeStep(step);
                result.getStepResults().add(stepResult);

                if (!stepResult.isSuccess()) {
                    // 步骤失败
                    result.setSuccess(false);
                    result.setCompletedSteps(i);
                    result.setErrorMessage(stepResult.getErrorMessage());

                    // 最后截图
                    result.setFinalScreenshot(takeScreenshot());

                    log.error("❌ 任务执行中断，步骤 {} 失败", step.getStepId());
                    return result;
                }

                result.setCompletedSteps(i + 1);
            }

            // 全部成功
            result.setSuccess(true);
            result.setCompletedSteps(steps.size());
            log.info("✅ 任务执行完成，全部 {} 步执行成功", steps.size());

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage("任务执行异常: " + e.getMessage());
            log.error("❌ 任务执行异常", e);
        } finally {
            result.setFinalScreenshot(takeScreenshot());
            log.info("⏱️ 任务总耗时: {}ms", System.currentTimeMillis() - taskStartTime);
        }

        return result;
    }

    // ============ 具体执行方法 ============

    /**
     * 检查浏览器是否仍然存活
     */
    private boolean isBrowserAlive() {
        try {
            driver.getCurrentUrl(); // 尝试获取当前URL
            return true;
        } catch (Exception e) {
            log.warn("浏览器 session 已失效: {}", e.getMessage());
            return false;
        }
    }

    private RpaStepResult executeOpenUrl(RpaStep step) {
        String url = step.getTarget();
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("URL不能为空");
        }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://" + url;
        }

        log.debug("打开URL: {}", url);
        driver.get(url);

        // 等待页面加载
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

        return RpaStepResult.success(step.getStepId(), "已打开: " + url);
    }

    private RpaStepResult executeInput(RpaStep step) {
        if (step.getTarget() == null || step.getTarget().isEmpty()) {
            throw new IllegalArgumentException("输入目标不能为空");
        }

        By locator = parseLocator(step.getTarget());
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));

        // 滚动到元素可见
        scrollToElement(element);

        element.clear();

        String value = step.getValue() != null ? step.getValue() : "";
        element.sendKeys(value);

        return RpaStepResult.success(step.getStepId(),
                "已输入: " + (value.length() > 20 ? value.substring(0, 20) + "..." : value));
    }

    private RpaStepResult executeClick(RpaStep step) {
        if (step.getTarget() == null || step.getTarget().isEmpty()) {
            throw new IllegalArgumentException("点击目标不能为空");
        }

        By locator = parseLocator(step.getTarget());
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));

        // 滚动到元素可见
        scrollToElement(element);

        element.click();

        return RpaStepResult.success(step.getStepId(), "已点击: " + step.getTarget());
    }

    private RpaStepResult executeWait(RpaStep step) {
        int waitTime = step.getWaitTime() != null ? step.getWaitTime() : 2;

        if (waitTime > 60) {
            log.warn("等待时间过长: {}秒，限制为60秒", waitTime);
            waitTime = 60;
        }

        try {
            Thread.sleep(waitTime * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("等待被中断");
        }

        return RpaStepResult.success(step.getStepId(), "等待 " + waitTime + " 秒");
    }

    private RpaStepResult executeScroll(RpaStep step) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        String direction = step.getTarget() != null ? step.getTarget().toLowerCase() : "down";
        int scrollAmount = step.getWaitTime() != null ? step.getWaitTime() * 100 : 500;

        switch (direction) {
            case "down" -> js.executeScript("window.scrollBy(0, arguments[0]);", scrollAmount);
            case "up" -> js.executeScript("window.scrollBy(0, -arguments[0]);", scrollAmount);
            case "bottom" -> js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
            case "top" -> js.executeScript("window.scrollTo(0, 0);");
            default -> js.executeScript("window.scrollBy(0, arguments[0]);", scrollAmount);
        }

        return RpaStepResult.success(step.getStepId(), "已滚动: " + direction);
    }

    private RpaStepResult executeExtract(RpaStep step) {
        if (step.getTarget() == null || step.getTarget().isEmpty()) {
            throw new IllegalArgumentException("提取目标不能为空");
        }

        By locator = parseLocator(step.getTarget());
        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));

        String text = element.getText();
        String value = element.getAttribute("value");
        String href = element.getAttribute("href");

        // 构建提取结果
        StringBuilder extractResult = new StringBuilder();
        extractResult.append("文本: ").append(text.length() > 100 ? text.substring(0, 100) + "..." : text);
        if (value != null && !value.isEmpty()) {
            extractResult.append(" | 值: ").append(value);
        }
        if (href != null && !href.isEmpty()) {
            extractResult.append(" | 链接: ").append(href);
        }

        return RpaStepResult.success(step.getStepId(), extractResult.toString());
    }

    private RpaStepResult executeSubmit(RpaStep step) {
        By locator = parseLocator(step.getTarget());
        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        element.submit();

        // 等待提交后的页面加载
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return RpaStepResult.success(step.getStepId(), "表单已提交: " + step.getTarget());
    }

    private RpaStepResult executeScreenshot(RpaStep step) {
        byte[] screenshot = takeScreenshot();
        String filename = saveScreenshot(screenshot, step.getStepId());

        return RpaStepResult.success(step.getStepId(),
                screenshot != null ? "截图已保存: " + filename : "截图失败");
    }

    private RpaStepResult executeClose(RpaStep step) {
        closeBrowser();
        return RpaStepResult.success(step.getStepId(), "浏览器已关闭");
    }

    // ============ 工具方法 ============

    /**
     * 解析定位器（支持CSS选择器、XPath、ID、ClassName）
     */
    private By parseLocator(String target) {
        if (target == null || target.isEmpty()) {
            throw new IllegalArgumentException("定位目标不能为空");
        }

        target = target.trim();

        // XPath
        if (target.startsWith("//") || target.startsWith("./") || target.startsWith("(//")) {
            return By.xpath(target);
        }

        // ID
        if (target.startsWith("#") && !target.contains(" ") && !target.contains("[")) {
            return By.id(target.substring(1));
        }

        // ClassName（简单类名，无空格）
        if (target.startsWith(".") && !target.contains(" ") && !target.contains("[")) {
            return By.className(target.substring(1));
        }

        // Name属性
        if (target.startsWith("[name=") || target.startsWith("name=")) {
            String name = target.replace("[name=", "").replace("name=", "").replace("]", "").replace("\"", "");
            return By.name(name);
        }

        // 默认CSS选择器
        return By.cssSelector(target);
    }

    /**
     * 滚动到元素可见
     */
    private void scrollToElement(WebElement element) {
        try {
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].scrollIntoView({behavior: 'smooth', block: 'center'});", element);
            Thread.sleep(500); // 等待滚动完成
        } catch (Exception e) {
            // 滚动失败不影响后续操作
            log.debug("滚动到元素失败: {}", e.getMessage());
        }
    }

    /**
     * 获取当前页面截图
     */
    public byte[] takeScreenshot() {
        if (driver == null) {
            return null;
        }

        try {
            if (driver instanceof TakesScreenshot) {
                return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            }
        } catch (Exception e) {
            log.error("截图失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 保存截图到文件
     */
    private String saveScreenshot(byte[] screenshot, Integer stepId) {
        if (screenshot == null) {
            return null;
        }

        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("screenshot_step%d_%s.png", stepId, timestamp);
            java.nio.file.Path path = java.nio.file.Paths.get("logs", "screenshots", filename);
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, screenshot);
            return path.toString();
        } catch (Exception e) {
            log.error("保存截图失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存错误截图
     */
    private String saveErrorScreenshot(Integer stepId) {
        byte[] screenshot = takeScreenshot();
        if (screenshot != null) {
            try {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String filename = String.format("error_step%d_%s.png", stepId, timestamp);
                java.nio.file.Path path = java.nio.file.Paths.get("logs", "screenshots", "errors", filename);
                java.nio.file.Files.createDirectories(path.getParent());
                java.nio.file.Files.write(path, screenshot);
                return path.toString();
            } catch (Exception e) {
                log.error("保存错误截图失败: {}", e.getMessage());
            }
        }
        return null;
    }

    /**
     * 关闭浏览器
     */
    public void closeBrowser() {
        if (driver != null) {
            try {
                log.info("关闭浏览器");
                driver.quit();
            } catch (Exception e) {
                log.error("关闭浏览器失败: {}", e.getMessage());
            } finally {
                driver = null;
                wait = null;
            }
        }
    }

    /**
     * 获取当前页面URL
     */
    public String getCurrentUrl() {
        return driver != null ? driver.getCurrentUrl() : null;
    }

    /**
     * 获取当前页面标题
     */
    public String getCurrentTitle() {
        return driver != null ? driver.getTitle() : null;
    }
}