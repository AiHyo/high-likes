package com.aih.highlike.manager.cache;

/**
 * TopK 元素项
 *
 * @param key   键
 * @param count 计数
 */
public record Item(String key, int count) {
}
