package com.rpaai.repository.neo4j;

import com.rpaai.entity.neo4j.ExceptionCase;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExceptionCaseRepository extends Neo4jRepository<ExceptionCase, String> {

    /**
     * 根据异常类型查找
     */
    List<ExceptionCase> findByErrorType(String errorType);

    /**
     * 根据操作类型查找
     */
    List<ExceptionCase> findByAction(String action);

    /**
     * 模糊搜索异常信息
     */
    @Query("MATCH (e:ExceptionCase) " +
            "WHERE e.errorMessage CONTAINS $keyword " +
            "OR e.errorType CONTAINS $keyword " +
            "RETURN e ORDER BY e.successCount DESC LIMIT 5")
    List<ExceptionCase> searchByKeyword(String keyword);

    /**
     * 查找最相似的异常案例
     */
    @Query("MATCH (e:ExceptionCase) " +
            "WHERE e.errorType = $errorType " +
            "AND e.action = $action " +
            "RETURN e ORDER BY e.successCount DESC, e.lastUsedTime DESC LIMIT 3")
    List<ExceptionCase> findSimilarCases(String errorType, String action);

    /**
     * 获取高频解决方案
     */
    @Query("MATCH (e:ExceptionCase) " +
            "WHERE e.successCount > 0 " +
            "RETURN e ORDER BY e.successCount DESC LIMIT 10")
    List<ExceptionCase> findTopSolutions();
}