package com.aih.highlike.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * Redis Lua 脚本常量
 * <p>
 * 使用 Lua 脚本保证 Redis 操作的原子性
 */
public class RedisLuaScript {

    /**
     * 点赞 Lua 脚本
     * <p>
     * 功能：原子性地完成点赞操作
     * 1. 检查用户是否已点赞
     * 2. 记录点赞状态到用户点赞记录
     * 3. 写入临时点赞记录（用于后续批量同步到数据库）
     * <p>
     * 参数说明：
     * - KEYS[1]: 临时点赞记录 Key（thumb:temp:{timeSlice}）
     * - KEYS[2]: 用户点赞状态 Key（thumb:user:{userId}）
     * - ARGV[1]: 用户ID
     * - ARGV[2]: 博客ID
     * <p>
     * 返回值：
     * - 1: 操作成功
     * - -1: 已点赞，操作失败
     */
    public static final RedisScript<Long> THUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1]
            local userThumbKey = KEYS[2]
            local userId = ARGV[1]
            local blogId = ARGV[2]
            
            -- 检查是否已点赞
            if redis.call('HEXISTS', userThumbKey, blogId) == 1 then
                return -1
            end
            
            -- 获取临时记录的当前值（默认为0）
            local hashKey = userId .. ':' .. blogId
            local oldValue = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 计算新值（+1表示点赞）
            local newValue = oldValue + 1
            
            -- 原子性更新：写入临时记录 + 标记用户已点赞
            redis.call('HSET', tempThumbKey, hashKey, newValue)
            redis.call('HSET', userThumbKey, blogId, 1)
            
            return 1
            """, Long.class);

    /**
     * 取消点赞 Lua 脚本
     * <p>
     * 功能：原子性地完成取消点赞操作
     * 1. 检查用户是否已点赞
     * 2. 删除用户点赞记录
     * 3. 写入临时取消点赞记录（用于后续批量同步到数据库）
     * <p>
     * 参数说明：
     * - KEYS[1]: 临时点赞记录 Key（thumb:temp:{timeSlice}）
     * - KEYS[2]: 用户点赞状态 Key（thumb:user:{userId}）
     * - ARGV[1]: 用户ID
     * - ARGV[2]: 博客ID
     * <p>
     * 返回值：
     * - 1: 操作成功
     * - -1: 未点赞，操作失败
     */
    public static final RedisScript<Long> CANCEL_THUMB_SCRIPT = new DefaultRedisScript<>("""
            local tempThumbKey = KEYS[1]
            local userThumbKey = KEYS[2]
            local userId = ARGV[1]
            local blogId = ARGV[2]
            
            -- 检查用户是否已点赞
            if redis.call('HEXISTS', userThumbKey, blogId) ~= 1 then
                return -1
            end
            
            -- 获取临时记录的当前值（默认为0）
            local hashKey = userId .. ':' .. blogId
            local oldValue = tonumber(redis.call('HGET', tempThumbKey, hashKey) or 0)
            
            -- 计算新值（-1表示取消点赞）
            local newValue = oldValue - 1
            
            -- 原子性更新：写入临时记录 + 删除用户点赞标记
            redis.call('HSET', tempThumbKey, hashKey, newValue)
            redis.call('HDEL', userThumbKey, blogId)
            
            return 1
            """, Long.class);
}
