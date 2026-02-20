package com.rpaai.config;

import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Configuration
public class SeleniumConfig {

    @Value("${rpa.selenium.chrome-driver-path:}")
    private String chromeDriverPath;

    @Value("${rpa.selenium.headless:false}")
    private boolean headless;

    @Value("${rpa.selenium.extension-path:}")
    private String extensionPath;

    @Bean
    public ChromeOptions chromeOptions() {
        setupChromeDriver();

        ChromeOptions options = new ChromeOptions();

        // ✅ 不使用现有用户数据目录，避免与当前运行的 Chrome 冲突
        // 使用临时目录，每次启动都是全新的浏览器实例

        // 1. 加载扩展（关键！）
        loadExtensions(options);

        // 2. 基础配置
        if (headless) {
            options.addArguments("--headless=new");
            log.info("启用无头模式");
        }

        options.addArguments("--start-maximized");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // 3. 禁用自动化检测（避免被网站识别为爬虫）
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // 4. 允许远程访问（提高兼容性）
        options.addArguments("--remote-allow-origins=*");

        log.info("✅ ChromeOptions 配置完成（使用临时目录，无用户数据）");
        return options;
    }

    /**
     * 加载 Chrome 扩展
     */
    private void loadExtensions(ChromeOptions options) {
        // 方式1：从配置的路径加载
        if (extensionPath != null && !extensionPath.isEmpty()) {
            File extFile = new File(extensionPath);
            if (loadExtensionFromPath(options, extFile)) {
                return;
            }
        }

        // 方式2：自动检测常见扩展路径
        String[] autoDetectPaths = {
                "D:/vscode/chrome-extension",
                "D:/chrome-extension",
                "C:/chrome-extension",
                System.getProperty("user.home") + "/chrome-extension",
                System.getProperty("user.home") + "/vscode/chrome-extension"
        };

        for (String path : autoDetectPaths) {
            File dir = new File(path);
            if (loadExtensionFromPath(options, dir)) {
                return;
            }
        }

        log.warn("⚠️ 未找到可用的 Chrome 扩展，将不加载扩展运行");
    }

    /**
     * 从指定路径加载扩展
     */
    private boolean loadExtensionFromPath(ChromeOptions options, File extFile) {
        if (!extFile.exists()) {
            return false;
        }

        if (extFile.isDirectory()) {
            // 检查是否是有效的扩展目录（包含 manifest.json）
            File manifest = new File(extFile, "manifest.json");
            if (manifest.exists()) {
                options.addArguments("--load-extension=" + extFile.getAbsolutePath());
                log.info("✅ 加载扩展（文件夹）: {}", extFile.getAbsolutePath());
                return true;
            } else {
                log.warn("扩展目录缺少 manifest.json: {}", extFile.getAbsolutePath());
            }
        } else if (extFile.getName().endsWith(".crx")) {
            // 加载打包的扩展（.crx文件）
            options.addExtensions(extFile);
            log.info("✅ 加载扩展（CRX）: {}", extFile.getAbsolutePath());
            return true;
        }

        return false;
    }

    /**
     * 设置 ChromeDriver 路径
     */
    private void setupChromeDriver() {
        // 1. 优先使用配置的路径
        if (chromeDriverPath != null && !chromeDriverPath.isEmpty()) {
            File driverFile = new File(chromeDriverPath);
            if (driverFile.exists()) {
                System.setProperty("webdriver.chrome.driver", chromeDriverPath);
                log.info("使用配置的 ChromeDriver: {}", chromeDriverPath);
                return;
            } else {
                log.warn("配置的 ChromeDriver 不存在: {}", chromeDriverPath);
            }
        }

        // 2. 自动检测常见路径
        List<String> autoDetectPaths = List.of(
                "chromedriver",
                "chromedriver.exe",
                "C:/tools/chromedriver.exe",
                "D:/tools/chromedriver.exe",
                "E:/tools/chromedriver.exe",
                System.getProperty("user.home") + "/tools/chromedriver.exe"
        );

        for (String path : autoDetectPaths) {
            Path driverPath = Paths.get(path);
            if (Files.exists(driverPath)) {
                System.setProperty("webdriver.chrome.driver", path);
                log.info("✅ 自动检测到 ChromeDriver: {}", path);
                return;
            }
        }

        // 3. 尝试从 PATH 环境变量查找
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            String[] paths = pathEnv.split(File.pathSeparator);
            for (String dir : paths) {
                File driver = new File(dir, "chromedriver.exe");
                if (!driver.exists()) {
                    driver = new File(dir, "chromedriver");
                }
                if (driver.exists() && driver.canExecute()) {
                    System.setProperty("webdriver.chrome.driver", driver.getAbsolutePath());
                    log.info("✅ 从 PATH 检测到 ChromeDriver: {}", driver.getAbsolutePath());
                    return;
                }
            }
        }

        log.error("❌ 未找到 ChromeDriver！请配置 rpa.selenium.chrome-driver-path");
    }
}