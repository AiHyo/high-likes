package com.aih.highlike.constant;

/**
 * 点赞常量
 *
 * @date 2025/10/30
 * @author zengliqiang
 */
public interface ThumbConstant {

    /**
     * 用户点赞记录 Redis Key 前缀
     * Redis Hash 结构：thumb:user:{userId} -> {blogId: thumbId}
     * 示例：thumb:user:1001 -> {"1": 5001, "2": 5002}
     */
    String USER_THUMB_KEY_PREFIX = "thumb:user:";

    /**
     * 临时点赞记录 Redis Key 前缀（用于异步批量同步）
     * Redis Hash 结构：thumb:temp:{timeSlice} -> {userId:blogId: operationType}
     * 示例：thumb:temp:14:30:20 -> {"1001:1": 1, "1001:2": -1}
     * <p>
     * 说明：
     * - timeSlice: 时间片，格式为 HH:mm:ss，按10秒分片
     * - operationType: 1=点赞，-1=取消点赞，0=无变化
     */
    String TEMP_THUMB_KEY_PREFIX = "thumb:temp:%s";

    /**
     * 未点赞标识
     * <p>
     * 用于本地缓存中标识用户未点赞状态
     * 当值为 0 时表示用户已取消点赞，避免频繁查询 Redis
     */
    Long UN_THUMB_CONSTANT = 0L;
}
