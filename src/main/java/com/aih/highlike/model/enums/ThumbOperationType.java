package com.aih.highlike.model.enums;

import lombok.Getter;

/**
 * 点赞操作类型枚举
 */
@Getter
public enum ThumbOperationType {
    
    /**
     * 点赞操作（增加）
     */
    THUMB(1, "点赞"),
    
    /**
     * 取消点赞操作（减少）
     */
    CANCEL(-1, "取消点赞"),
    
    /**
     * 无变化（用于标识重复操作被过滤）
     */
    NONE(0, "无变化");
    
    private final int value;
    private final String description;
    
    ThumbOperationType(int value, String description) {
        this.value = value;
        this.description = description;
    }
    
    /**
     * 根据值获取枚举
     */
    public static ThumbOperationType fromValue(int value) {
        for (ThumbOperationType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        return NONE;
    }
}
