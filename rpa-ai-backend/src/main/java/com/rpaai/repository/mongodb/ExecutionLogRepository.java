package com.rpaai.repository.mongodb;

import com.rpaai.entity.mongodb.ExecutionLogDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExecutionLogRepository extends MongoRepository<ExecutionLogDocument, String> {

    /**
     * 根据任务ID查询执行历史
     */
    List<ExecutionLogDocument> findByTaskIdOrderByStartTimeDesc(Long taskId);

    /**
     * 查询最近的执行记录
     */
    List<ExecutionLogDocument> findTop20ByOrderByStartTimeDesc();

    /**
     * 查询成功的记录
     */
    List<ExecutionLogDocument> findBySuccessTrueOrderByStartTimeDesc();

    /**
     * 查询失败的记录
     */
    List<ExecutionLogDocument> findBySuccessFalseOrderByStartTimeDesc();

    /**
     * 按时间范围查询
     */
    List<ExecutionLogDocument> findByStartTimeBetweenOrderByStartTimeDesc(
            LocalDateTime start, LocalDateTime end);

    /**
     * 统计任务执行次数
     */
    long countByTaskId(Long taskId);

    /**
     * 统计成功率
     */
    long countByTaskIdAndSuccessTrue(Long taskId);
}