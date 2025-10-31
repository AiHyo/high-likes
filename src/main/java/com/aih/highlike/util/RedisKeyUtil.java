package com.aih.highlike.util;

import com.aih.highlike.constant.ThumbConstant;

/**
 * Redis Key 工具类
 * <p>
 * 统一管理 Redis Key 的生成，避免硬编码
 */
public class RedisKeyUtil {

    /**
     * 获取用户点赞记录 Key
     * <p>
     * 格式：thumb:user:{userId}
     *
     * @param userId 用户ID
     * @return Redis Key
     */
    public static String getUserThumbKey(Long userId) {
        return ThumbConstant.USER_THUMB_KEY_PREFIX + userId;
    }

    /**
     * 获取临时点赞记录 Key
     * <p>
     * 格式：thumb:temp:{timeSlice}
     * <p>
     * 示例：thumb:temp:14:30:20
     *
     * @param timeSlice 时间片（格式：HH:mm:ss）
     * @return Redis Key
     */
    public static String getTempThumbKey(String timeSlice) {
        return String.format(ThumbConstant.TEMP_THUMB_KEY_PREFIX, timeSlice);
    }
}
