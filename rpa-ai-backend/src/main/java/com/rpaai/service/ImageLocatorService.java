package com.rpaai.service;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
import org.bytedeco.opencv.opencv_core.Size;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Service
public class ImageLocatorService {

    // 降低默认阈值，淘宝动态页面用 0.65 更合适
    public Optional<int[]> locateElement(String screenshotBase64, String templateBase64) {
        return locateElement(screenshotBase64, templateBase64, 0.65);
    }

    public Optional<int[]> locateElement(String screenshotBase64, String templateBase64, double threshold) {
        try {
            // 保存调试图片（调试用，确认图片正常）
            saveDebugImage(screenshotBase64, "screenshot");
            saveDebugImage(templateBase64, "template");

            byte[] screenshotBytes = Base64.getDecoder().decode(screenshotBase64);
            byte[] templateBytes = Base64.getDecoder().decode(templateBase64);

            Mat screenshot = bytesToMat(screenshotBytes);
            Mat template = bytesToMat(templateBytes);

            if (screenshot.empty() || template.empty()) {
                log.error("图像解码失败: screenshot={}, template={}",
                        screenshot.empty(), template.empty());
                return Optional.empty();
            }

            log.info("开始图像匹配: 截图尺寸 {}x{}, 模板尺寸 {}x{}",
                    screenshot.cols(), screenshot.rows(),
                    template.cols(), template.rows());

            // 多尺度匹配（应对不同分辨率/缩放）
            Optional<int[]> result = matchMultiScale(screenshot, template, threshold);

            screenshot.release();
            template.release();

            return result;

        } catch (Exception e) {
            log.error("图像识别失败: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    /**
     * 多尺度模板匹配（0.5x 到 2.0x）
     */
    private Optional<int[]> matchMultiScale(Mat screenshot, Mat template, double threshold) {
        Mat result = new Mat();
        int matchMethod = opencv_imgproc.TM_CCOEFF_NORMED;

        double bestConfidence = 0;
        int bestX = 0, bestY = 0;
        double bestScale = 1.0;

        // 尝试不同缩放比例
        double[] scales = {1.0, 0.9, 1.1, 0.8, 1.2, 0.7, 1.3};

        for (double scale : scales) {
            Mat resizedTemplate = new Mat();
            Size newSize = new Size(
                    (int)(template.cols() * scale),
                    (int)(template.rows() * scale)
            );

            // 如果缩放后比截图还大，跳过
            if (newSize.width() > screenshot.cols() || newSize.height() > screenshot.rows()) {
                resizedTemplate.release();
                continue;
            }

            opencv_imgproc.resize(template, resizedTemplate, newSize);

            // 执行匹配
            opencv_imgproc.matchTemplate(screenshot, resizedTemplate, result, matchMethod);

            DoublePointer minVal = new DoublePointer(1);
            DoublePointer maxVal = new DoublePointer(1);
            Point minLoc = new Point();
            Point maxLoc = new Point();

            opencv_core.minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

            double confidence = maxVal.get(0);

            if (confidence > bestConfidence) {
                bestConfidence = confidence;
                bestX = maxLoc.x() + (int)(resizedTemplate.cols() / 2);
                bestY = maxLoc.y() + (int)(resizedTemplate.rows() / 2);
                bestScale = scale;
            }

            resizedTemplate.release();
        }

        result.release();

        log.info("最佳匹配: 置信度={}, 坐标=({}, {}), 缩放比例={}",
                bestConfidence, bestX, bestY, bestScale);

        if (bestConfidence >= threshold) {
            log.info("✅ 图像匹配成功，返回坐标: ({}, {})", bestX, bestY);
            return Optional.of(new int[]{bestX, bestY});
        } else {
            log.warn("❌ 图像匹配置信度过低: {} < {}", bestConfidence, threshold);
            return Optional.empty();
        }
    }

    private Mat bytesToMat(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                log.error("无法读取图像数据");
                return new Mat();
            }

            // 转换为 OpenCV Mat
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();

            return opencv_imgcodecs.imdecode(new Mat(pngBytes), opencv_imgcodecs.IMREAD_COLOR);
        } catch (IOException e) {
            log.error("图像转换失败", e);
            return new Mat();
        }
    }

    public void saveDebugImage(String base64Image, String filename) {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64Image);
            Path path = Path.of("logs/debug_" + filename + "_" + System.currentTimeMillis() + ".png");
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
            log.info("调试图像已保存: {}", path.toAbsolutePath());
        } catch (IOException e) {
            log.error("保存调试图像失败", e);
        }
    }
}