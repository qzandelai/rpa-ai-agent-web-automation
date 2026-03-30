package com.rpaai.service;

import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Point;
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

    public Optional<int[]> locateElement(String screenshotBase64, String templateBase64) {
        return locateElement(screenshotBase64, templateBase64, 0.8);
    }

    public Optional<int[]> locateElement(String screenshotBase64, String templateBase64, double threshold) {
        try {
            byte[] screenshotBytes = Base64.getDecoder().decode(screenshotBase64);
            byte[] templateBytes = Base64.getDecoder().decode(templateBase64);

            Mat screenshot = bytesToMat(screenshotBytes);
            Mat template = bytesToMat(templateBytes);

            if (screenshot.empty() || template.empty()) {
                log.error("图像解码失败");
                return Optional.empty();
            }

            Mat result = new Mat();
            int matchMethod = opencv_imgproc.TM_CCOEFF_NORMED;

            opencv_imgproc.matchTemplate(screenshot, template, result, matchMethod);

            DoublePointer minVal = new DoublePointer(1);
            DoublePointer maxVal = new DoublePointer(1);
            Point minLoc = new Point();
            Point maxLoc = new Point();

            opencv_core.minMaxLoc(result, minVal, maxVal, minLoc, maxLoc, null);

            double confidence = maxVal.get(0);
            log.info("图像匹配置信度: {}", confidence);

            if (confidence >= threshold) {
                int centerX = maxLoc.x() + template.cols() / 2;
                int centerY = maxLoc.y() + template.rows() / 2;

                log.info("✅ 图像匹配成功，坐标: ({}, {})，置信度: {}", centerX, centerY, confidence);

                screenshot.release();
                template.release();
                result.release();

                return Optional.of(new int[]{centerX, centerY});
            } else {
                log.warn("❌ 图像匹配置信度过低: {} < {}", confidence, threshold);
            }

            screenshot.release();
            template.release();
            result.release();

        } catch (Exception e) {
            log.error("图像识别失败: {}", e.getMessage(), e);
        }

        return Optional.empty();
    }

    private Mat bytesToMat(byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                return new Mat();
            }

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
            log.info("调试图像已保存: {}", path);
        } catch (IOException e) {
            log.error("保存调试图像失败", e);
        }
    }
}