package com.rpaai.entity.neo4j;

import lombok.Data;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Node("ExceptionCase")
public class ExceptionCase {

    @Id
    private String id;

    @Property("errorType")
    private String errorType;  // 异常类型：NoSuchElementException, TimeoutException等

    @Property("errorMessage")
    private String errorMessage;  // 异常详细信息

    @Property("pageUrl")
    private String pageUrl;  // 发生异常的页面URL

    @Property("action")
    private String action;  // 操作类型：click, input等

    @Property("target")
    private String target;  // 定位目标

    @Property("solution")
    private String solution;  // 解决方案

    @Property("successCount")
    private Integer successCount = 0;  // 成功解决次数

    @Property("createTime")
    private LocalDateTime createTime = LocalDateTime.now();

    @Property("lastUsedTime")
    private LocalDateTime lastUsedTime;

    @Relationship(type = "SIMILAR_TO", direction = Relationship.Direction.OUTGOING)
    private List<ExceptionCase> similarCases = new ArrayList<>();

    public ExceptionCase() {
        this.id = "EXC_" + System.currentTimeMillis();
    }
}