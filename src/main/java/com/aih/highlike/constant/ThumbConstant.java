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
     * <p>
     * Redis Hash 结构：thumb:user:{userId} -> {blogId: thumbId}
     * <p>
     * 示例：thumb:user:1001 -> {"1": 5001, "2": 5002}
     */
    String USER_THUMB_KEY_PREFIX = "thumb:user:";
}
