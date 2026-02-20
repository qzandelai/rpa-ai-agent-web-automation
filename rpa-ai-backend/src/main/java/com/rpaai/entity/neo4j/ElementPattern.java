package com.rpaai.entity.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

import java.time.LocalDateTime;

@Data
@Node("ElementPattern")
public class ElementPattern {

    @Id
    private String id;

    @Property("pageType")
    private String pageType;  // 页面类型：login, search, form等

    @Property("elementType")
    private String elementType;  // 元素类型：button, input, link等

    @Property("successfulSelector")
    private String successfulSelector;  // 成功的选择器

    @Property("alternativeSelectors")
    private String alternativeSelectors;  // 备选选择器（JSON数组）

    @Property("usageCount")
    private Integer usageCount = 0;

    @Property("successRate")
    private Double successRate = 1.0;

    @Property("lastSuccessTime")
    private LocalDateTime lastSuccessTime;

    public ElementPattern() {
        this.id = "PAT_" + System.currentTimeMillis();
    }
}