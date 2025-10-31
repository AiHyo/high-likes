package com.aih.highlike.mapper;

import com.aih.highlike.model.entity.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
 * 博客数据访问层
 */
public interface BlogMapper extends BaseMapper<Blog> {

    /**
     * 批量更新博客点赞数
     * <p>
     * 使用 CASE WHEN 语句批量更新，避免循环更新
     * <p>
     * SQL 示例：
     * <pre>
     * UPDATE blog
     * SET thumbCount = thumbCount + CASE id
     *     WHEN 1 THEN 5
     *     WHEN 2 THEN -3
     *     WHEN 3 THEN 10
     * END
     * WHERE id IN (1, 2, 3)
     * </pre>
     *
     * @param countMap 博客ID -> 点赞数变化量的映射
     */
    void batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}
