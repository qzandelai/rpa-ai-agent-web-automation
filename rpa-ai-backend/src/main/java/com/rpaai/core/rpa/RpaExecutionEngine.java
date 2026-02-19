package com.rpaai.core.rpa;

import com.rpaai.entity.RpaStep;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Slf4j
@Component
public class RpaExecutionEngine {

    private WebDriver driver;
    private WebDriverWait wait;

    /**
     * 初始化浏览器
     */
    public void initBrowser() {
        log.info("🚀 初始化Chrome浏览器");

        ChromeOptions options = new ChromeOptions();
        // 开发环境显示浏览器，生产环境可启用无头模式
        // options.addArguments("--headless=new");

        options.addArguments("--start-maximized");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));

        log.info("✅ 浏览器初始化完成");
    }

    /**
     * 执行单步操作
     */
    public RpaStepResult executeStep(RpaStep step) {
        log.info("执行步骤 {}: {} - {}", step.getStepId(), step.getAction(), step.getDescription());

        try {
            return switch (step.getAction()) {
                case "open_url" -> executeOpenUrl(step);
                case "input" -> executeInput(step);
                case "click" -> executeClick(step);
                case "wait" -> executeWait(step);
                case "scroll" -> executeScroll(step);
                case "extract" -> executeExtract(step);
                default -> throw new UnsupportedOperationException("未知操作: " + step.getAction());
            };
        } catch (Exception e) {
            log.error("❌ 步骤 {} 执行失败: {}", step.getStepId(), e.getMessage());
            return RpaStepResult.fail(step.getStepId(), e.getMessage());
        }
    }

    /**
     * 执行完整任务
     */
    public RpaExecutionResult executeTask(List<RpaStep> steps) {
        log.info("🎯 开始执行任务，共 {} 步", steps.size());

        RpaExecutionResult result = new RpaExecutionResult();
        result.setTotalSteps(steps.size());

        // 初始化浏览器
        initBrowser();

        try {
            for (RpaStep step : steps) {
                RpaStepResult stepResult = executeStep(step);
                result.getStepResults().add(stepResult);

                if (!stepResult.isSuccess() && step.getRequired()) {
                    result.setSuccess(false);
                    result.setErrorMessage("步骤 " + step.getStepId() + " 失败: " + stepResult.getErrorMessage());
                    break;
                }
            }

            result.setSuccess(true);
            log.info("✅ 任务执行完成");

        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            log.error("❌ 任务执行异常", e);

        } finally {
            // 可选：保持浏览器打开便于调试，或自动关闭
            // closeBrowser();
        }

        return result;
    }

    // ============ 具体执行方法 ============

    private RpaStepResult executeOpenUrl(RpaStep step) {
        String url = step.getTarget();
        if (!url.startsWith("http")) {
            url = "https://" + url;
        }

        log.debug("打开URL: {}", url);
        driver.get(url);

        return RpaStepResult.success(step.getStepId(), "已打开: " + url);
    }

    private RpaStepResult executeInput(RpaStep step) {
        By locator = parseLocator(step.getTarget());

        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        element.clear();
        element.sendKeys(step.getValue());

        return RpaStepResult.success(step.getStepId(), "已输入: " + step.getValue());
    }

    private RpaStepResult executeClick(RpaStep step) {
        By locator = parseLocator(step.getTarget());

        WebElement element = wait.until(ExpectedConditions.elementToBeClickable(locator));
        element.click();

        return RpaStepResult.success(step.getStepId(), "已点击元素");
    }

    private RpaStepResult executeWait(RpaStep step) {
        int waitTime = step.getWaitTime() != null ? step.getWaitTime() : 2;

        try {
            Thread.sleep(waitTime * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return RpaStepResult.success(step.getStepId(), "等待 " + waitTime + " 秒");
    }

    private RpaStepResult executeScroll(RpaStep step) {
        JavascriptExecutor js = (JavascriptExecutor) driver;

        if ("down".equals(step.getTarget())) {
            js.executeScript("window.scrollBy(0, 500);");
        } else {
            js.executeScript("window.scrollBy(0, -500);");
        }

        return RpaStepResult.success(step.getStepId(), "已滚动页面");
    }

    private RpaStepResult executeExtract(RpaStep step) {
        By locator = parseLocator(step.getTarget());

        WebElement element = wait.until(ExpectedConditions.presenceOfElementLocated(locator));
        String text = element.getText();

        return RpaStepResult.success(step.getStepId(), "提取数据: " + text.substring(0, Math.min(50, text.length())));
    }

    /**
     * 解析定位器（支持CSS选择器和XPath）
     */
    private By parseLocator(String target) {
        if (target.startsWith("//") || target.startsWith("./")) {
            return By.xpath(target);
        } else if (target.startsWith("#")) {
            return By.id(target.substring(1));
        } else if (target.startsWith(".")) {
            return By.className(target.substring(1));
        } else {
            return By.cssSelector(target);
        }
    }

    /**
     * 关闭浏览器
     */
    public void closeBrowser() {
        if (driver != null) {
            log.info("关闭浏览器");
            driver.quit();
            driver = null;
        }
    }

    /**
     * 获取当前页面截图（用于调试）
     */
    public byte[] takeScreenshot() {
        if (driver instanceof TakesScreenshot) {
            return ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
        }
        return null;
    }
}