package com.rpaai.repository.neo4j;

import com.rpaai.entity.neo4j.ElementPattern;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ElementPatternRepository extends Neo4jRepository<ElementPattern, String> {

    /**
     * 根据页面类型和元素类型查找
     */
    Optional<ElementPattern> findByPageTypeAndElementType(String pageType, String elementType);

    /**
     * 查找成功率高的模式
     */
    @Query("MATCH (p:ElementPattern) " +
            "WHERE p.successRate >= $minRate " +
            "RETURN p ORDER BY p.successRate DESC, p.usageCount DESC")
    List<ElementPattern> findReliablePatterns(Double minRate);

    /**
     * 更新使用统计
     */
    @Query("MATCH (p:ElementPattern {id: $id}) " +
            "SET p.usageCount = p.usageCount + 1, " +
            "p.lastSuccessTime = datetime() " +
            "RETURN p")
    ElementPattern incrementUsage(String id);

    /**
     * 查找有视觉定位的模式
     */
    @Query("MATCH (p:ElementPattern) " +
            "WHERE p.pageType = $pageType " +
            "AND p.elementType = $elementType " +
            "AND p.imageTemplate IS NOT NULL " +
            "RETURN p ORDER BY p.successRate DESC, p.usageCount DESC LIMIT 1")
    Optional<ElementPattern> findVisualPattern(String pageType, String elementType);

    /**
     * 获取所有模式（按成功率排序）
     */
    @Query("MATCH (p:ElementPattern) RETURN p ORDER BY p.successRate DESC, p.usageCount DESC")
    List<ElementPattern> findAllPatterns();

    /**
     * 根据ID删除
     */
    @Query("MATCH (p:ElementPattern {id: $id}) DELETE p")
    void deleteById(String id);
}