package com.aih.highlike.manager.cache;

import lombok.Data;

/**
 * HeavyKeeper add 操作返回结果
 */
@Data
public class AddResult {

    /**
     * 被挤出 TopK 的 key
     */
    private final String expelledKey;

    /**
     * 当前 key 是否进入 TopK
     */
    private final boolean isHotKey;

    /**
     * 当前操作的 key
     */
    private final String currentKey;

    public AddResult(String expelledKey, boolean isHotKey, String currentKey) {
        this.expelledKey = expelledKey;
        this.isHotKey = isHotKey;
        this.currentKey = currentKey;
    }
}
