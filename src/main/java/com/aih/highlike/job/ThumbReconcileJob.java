package com.aih.highlike.job;

import com.aih.highlike.constant.ThumbConstant;
import com.aih.highlike.listener.thumb.msg.ThumbEvent;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.service.ThumbService;
import com.google.common.collect.Sets;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 对账任务，以保证数据一致性
 * </p>
 *
 * @author zeng.liqiang
 * @date 2025/11/4
 */
@Service
@Slf4j
public class ThumbReconcileJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbService thumbService;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    /**
     * 定时任务入口 - 每天2点执行
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void run(){
        long staTime = System.currentTimeMillis();
        String pattern = ThumbConstant.USER_THUMB_KEY_PREFIX + "*";
        // 分批扫描 Redis 键：使用 Redis 的 SCAN 命令代替 KEYS，避免阻塞 Redis 主线程
        Set<Long> userIds = new HashSet<>();
        Cursor<String> cursor = redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(1000).build());
        while (cursor.hasNext()) {
            String key = cursor.next();
            userIds.add(Long.valueOf(key.replace(ThumbConstant.USER_THUMB_KEY_PREFIX, "")));
        }
        // 根据每个userId，对账
        userIds.forEach(userId -> {
            Set<Long> redisBlogIds = redisTemplate.opsForHash().keys(ThumbConstant.USER_THUMB_KEY_PREFIX + userId)
                    .stream().map(o -> Long.valueOf(o.toString())).collect(Collectors.toSet());
            Set<Long> dbBlogIds = thumbService.lambdaQuery().eq(Thumb::getUserId, userId)
                    .select(Thumb::getBlogId).list().stream().map(Thumb::getBlogId).collect(Collectors.toSet());
            // 返回在第一个set中存在，但在第二个set中不存在元素的SetView
            Sets.SetView<Long> difference = Sets.difference(redisBlogIds, dbBlogIds);
            sendCompensationMessage(userId, difference);
        });
    }

    /**
     * 发送补偿消息
     * @param userId 用户ID
     * @param difference redis中未同步到数据库的blogId
     */
    private void sendCompensationMessage(Long userId, Sets.SetView<Long> difference) {
        difference.forEach(blogId -> {
            ThumbEvent thumbEvent = new ThumbEvent(userId, blogId, ThumbEvent.EventType.INCR, LocalDateTime.now());
            pulsarTemplate.sendAsync("thumb-topic", thumbEvent)
                    .exceptionally(ex->{
                        log.error("发送补偿消息失败：userId={}, blogId={}", userId, blogId, ex);
                        return null;
                    });
        });


    }

}
