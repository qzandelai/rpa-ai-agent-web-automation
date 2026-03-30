package com.rpaai.controller;

import com.rpaai.entity.mongodb.DataExtractRecord;
import com.rpaai.service.DataExportService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*")
public class DataExportController {

    @Autowired
    private DataExportService dataExportService;

    /**
     * 获取任务提取的数据列表
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<List<DataExtractRecord>> getTaskData(@PathVariable Long taskId) {
        return ResponseEntity.ok(dataExportService.getDataByTask(taskId));
    }

    /**
     * 获取最近提取的数据
     */
    @GetMapping("/recent")
    public ResponseEntity<List<DataExtractRecord>> getRecentData(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(dataExportService.getRecentData(limit));
    }

    /**
     * 获取单条数据详情（第86行对应的方法）
     */
    @GetMapping("/record/{recordId}")
    public ResponseEntity<DataExtractRecord> getRecord(@PathVariable String recordId) {
        return dataExportService.getRecordById(recordId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 导出Excel
     */
    @GetMapping("/export/excel/{recordId}")
    public ResponseEntity<byte[]> exportExcel(@PathVariable String recordId,
                                              @RequestParam(required = false) String filename) throws IOException {
        byte[] excelBytes = dataExportService.exportToExcel(recordId);

        String fileName = (filename != null ? filename : "extracted_data_" + recordId) + ".xlsx";
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(excelBytes);
    }

    /**
     * 导出CSV
     */
    @GetMapping("/export/csv/{recordId}")
    public void exportCsv(@PathVariable String recordId,
                          @RequestParam(required = false) String filename,
                          HttpServletResponse response) throws IOException {
        String csvContent = dataExportService.exportToCsv(recordId);

        String fileName = (filename != null ? filename : "extracted_data_" + recordId) + ".csv";
        fileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"");

        // 添加BOM以支持Excel打开中文CSV
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        response.getOutputStream().write(csvContent.getBytes(StandardCharsets.UTF_8));
    }
}