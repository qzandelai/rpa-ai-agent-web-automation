package com.rpaai.service;

import com.rpaai.entity.mongodb.DataExtractRecord;
import com.rpaai.repository.mongodb.DataExtractRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class DataExportService {

    @Autowired
    private DataExtractRepository extractRepository;

    /**
     * 根据ID获取单条记录
     */
    public Optional<DataExtractRecord> getRecordById(String recordId) {
        return extractRepository.findById(recordId);
    }

    /**
     * 根据任务ID查询提取的数据
     */
    public List<DataExtractRecord> getDataByTask(Long taskId) {
        return extractRepository.findByTaskIdOrderByExtractTimeDesc(taskId);
    }

    /**
     * 获取最近提取的数据（修正：extractTime 而非 startTime）
     */
    public List<DataExtractRecord> getRecentData(int limit) {
        return extractRepository.findTop50ByOrderByExtractTimeDesc()
                .stream()
                .limit(limit)
                .toList();
    }

    /**
     * 导出为Excel
     */
    public byte[] exportToExcel(String recordId) throws IOException {
        DataExtractRecord record = extractRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("数据记录不存在"));

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("提取数据");

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // 写入表头
            Row headerRow = sheet.createRow(0);
            List<String> headers = record.getHeaders();
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            // 写入数据
            List<Map<String, Object>> rows = record.getRows();
            for (int i = 0; i < rows.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> rowData = rows.get(i);

                for (int j = 0; j < headers.size(); j++) {
                    Cell cell = row.createCell(j);
                    Object value = rowData.get(headers.get(j));
                    if (value != null) {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            // 自动调整列宽
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }

    /**
     * 导出为CSV
     */
    public String exportToCsv(String recordId) throws IOException {
        DataExtractRecord record = extractRepository.findById(recordId)
                .orElseThrow(() -> new RuntimeException("数据记录不存在"));

        StringWriter writer = new StringWriter();
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader(record.getHeaders().toArray(new String[0]))
                .build();

        try (CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {
            for (Map<String, Object> row : record.getRows()) {
                List<String> values = record.getHeaders().stream()
                        .map(h -> row.get(h) != null ? row.get(h).toString() : "")
                        .toList();
                csvPrinter.printRecord(values);
            }
        }

        return writer.toString();
    }

    /**
     * 保存提取的数据（供RpaTaskScheduler调用）
     */
    public DataExtractRecord saveExtractedData(Long taskId, String taskName, String executionId,
                                               String sourceUrl, String selector,
                                               List<String> headers, List<Map<String, Object>> rows) {
        DataExtractRecord record = new DataExtractRecord();
        record.setTaskId(taskId);
        record.setTaskName(taskName);
        record.setExecutionId(executionId);
        record.setSourceUrl(sourceUrl);
        record.setRawSelector(selector);
        record.setHeaders(headers);
        record.setRows(rows);
        record.setExtractTime(java.time.LocalDateTime.now());
        record.setExtractType(rows.size() > 1 ? "list" : "single");

        return extractRepository.save(record);
    }
}