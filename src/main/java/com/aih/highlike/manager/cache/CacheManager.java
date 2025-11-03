package com.aih.highlike.manager.cache;

import cn.hutool.core.util.ObjectUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存管理器
 * <p>
 * 功能：
 * 1. 管理本地缓存（Caffeine）
 * 2. 管理热点检测器（HeavyKeeper）
 * 3. 实现多级缓存查询
 * 4. 定期衰减热点数据
 */
@Slf4j
@Component
public class CacheManager {
    
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    
    /**
     * 热点检测器
     */
    private TopK hotKeyDetector;
    
    /**
     * 本地缓存
     */
    private Cache<String, Object> localCache;
    
    /**
     * 初始化热点检测器
     */
    @Bean
    public TopK getHotKeyDetector() {
        hotKeyDetector = new HeavyKeeper(
                100,      // 监控 Top 100 Key
                100000,   // 宽度
                5,        // 深度
                0.92,     // 衰减系数
                10        // 最小出现 10 次才记录
        );
        return hotKeyDetector;
    }
    
    /**
     * 初始化本地缓存
     */
    @Bean
    public Cache<String, Object> localCache() {
        localCache = Caffeine.newBuilder()
                .maximumSize(1000)                          // 最多缓存 1000 个元素
                .expireAfterWrite(5, TimeUnit.MINUTES)      // 写入后 5 分钟过期
                .build();
        return localCache;
    }
    
    /**
     * 构造复合 Key
     * <p>
     * 格式：hashKey:key
     */
    private String buildCacheKey(String hashKey, String key) {
        return hashKey + ":" + key;
    }
    
    /**
     * 多级缓存查询
     * <p>
     * 查询流程：
     * 1. 查询本地缓存
     * 2. 本地缓存未命中，查询 Redis
     * 3. 记录访问频率
     * 4. 如果是热点 Key，缓存到本地
     *
     * @param hashKey Redis Hash 的 Key
     * @param key     Redis Hash 的 Field
     * @return 值
     */
    public Object get(String hashKey, String key) {
        String compositeKey = buildCacheKey(hashKey, key);
        
        // 1. 查询本地缓存
        Object value = localCache.getIfPresent(compositeKey);
        if (ObjectUtil.isNotNull(value)) {
            // 记录访问次数
            hotKeyDetector.add(key, 1);
            return value;
        }
        
        // 2. 本地缓存未命中，查询 Redis
        Object redisValue = redisTemplate.opsForHash().get(hashKey, key);
        if (redisValue == null) {
            return null;
        }
        
        // 3. 记录访问频率
        AddResult addResult = hotKeyDetector.add(key, 1);
        
        // 4. 如果是热点 Key，缓存到本地
        if (addResult.isHotKey()) {
            localCache.put(compositeKey, redisValue);
        }
        return redisValue;
    }
    
    /**
     * 如果本地缓存存在，则更新
     *
     * @param hashKey Redis Hash 的 Key
     * @param key     Redis Hash 的 Field
     * @param value   新值
     */
    public void putIfPresent(String hashKey, String key, Object value) {
        String compositeKey = buildCacheKey(hashKey, key);
        Object existing = localCache.getIfPresent(compositeKey);
        if (existing != null) {
            localCache.put(compositeKey, value);
        }
    }
    
    /**
     * 定时清理过期的热点数据
     * <p>
     * 每 20 秒执行一次，对所有计数进行衰减
     */
    @Scheduled(fixedRate = 20, timeUnit = TimeUnit.SECONDS)
    public void cleanHotKeys() {
        hotKeyDetector.fading();
        log.debug("热点数据衰减完成，总访问次数：{}", hotKeyDetector.total());
    }
}
