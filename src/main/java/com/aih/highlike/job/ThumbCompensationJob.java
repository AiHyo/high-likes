package com.aih.highlike.job;

import cn.hutool.core.collection.CollUtil;
import com.aih.highlike.constant.ThumbConstant;
import com.aih.highlike.util.RedisKeyUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * 点赞数据补偿任务
 * <p>
 * 功能：处理未同步的临时点赞记录
 * <p>
 * 执行频率：每天凌晨 2 点
 */
@Slf4j
@Component
public class ThumbCompensationJob {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ThumbSyncJob thumbSyncJob;

    /**
     * 补偿任务（每天凌晨 2 点执行）
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void compensateThumbData() {
        log.info("开始执行点赞数据补偿任务");

        try {
            // 扫描所有临时点赞记录的 Key
            String pattern = RedisKeyUtil.getTempThumbKey("*");
            Set<String> thumbKeys = redisTemplate.keys(pattern);

            if (CollUtil.isEmpty(thumbKeys)) {
                log.info("没有需要补偿的临时数据");
                return;
            }

            log.info("发现 {} 个时间片的临时数据需要补偿", thumbKeys.size());

            // 提取时间片
            Set<String> timeSlices = new HashSet<>();
            String prefix = String.format(ThumbConstant.TEMP_THUMB_KEY_PREFIX, "");
            for (String key : thumbKeys) {
                if (key != null) {
                    String timeSlice = key.replace(prefix, "");
                    timeSlices.add(timeSlice);
                }
            }

            // 逐个处理时间片
            int successCount = 0;
            int failCount = 0;
            
            for (String timeSlice : timeSlices) {
                try {
                    log.info("补偿时间片：{}", timeSlice);
                    thumbSyncJob.syncByTimeSlice(timeSlice);
                    successCount++;
                } catch (Exception e) {
                    log.error("补偿时间片 {} 失败", timeSlice, e);
                    failCount++;
                }
            }

            log.info("点赞数据补偿任务完成，成功：{}，失败：{}", successCount, failCount);
        } catch (Exception e) {
            log.error("点赞数据补偿任务执行失败", e);
        }
    }
}
