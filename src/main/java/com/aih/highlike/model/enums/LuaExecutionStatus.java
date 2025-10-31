package com.aih.highlike.model.enums;

import lombok.Getter;

/**
 * Lua 脚本执行状态枚举
 */
@Getter
public enum LuaExecutionStatus {
    
    /**
     * 执行成功
     */
    SUCCESS(1L, "成功"),
    
    /**
     * 执行失败
     */
    FAILURE(-1L, "失败");
    
    private final Long value;
    private final String description;
    
    LuaExecutionStatus(Long value, String description) {
        this.value = value;
        this.description = description;
    }
    
    /**
     * 判断是否成功
     */
    public static boolean isSuccess(Long result) {
        return SUCCESS.value.equals(result);
    }
    
    /**
     * 判断是否失败
     */
    public static boolean isFailure(Long result) {
        return FAILURE.value.equals(result);
    }
}
