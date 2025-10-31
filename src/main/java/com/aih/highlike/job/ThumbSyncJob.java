package com.aih.highlike.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.text.StrPool;
import com.aih.highlike.mapper.BlogMapper;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.model.enums.ThumbOperationType;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.util.RedisKeyUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 点赞数据同步定时任务
 * <p>
 * 功能：将 Redis 临时点赞记录批量同步到数据库
 * <p>
 * 执行频率：每 10 秒
 */
@Slf4j
@Component
public class ThumbSyncJob {

    @Resource(name = "thumbServiceAsync")
    private ThumbService thumbService;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 定时同步任务（每 10 秒执行一次）
     * <p>
     * 处理上一个时间片的数据，避免与正在写入的数据冲突
     */
    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void syncThumbToDatabase() {
        try {
            // 计算上一个时间片
            String previousTimeSlice = calculatePreviousTimeSlice();
            
            log.info("开始同步点赞数据，时间片：{}", previousTimeSlice);
            
            // 同步指定时间片的数据
            syncByTimeSlice(previousTimeSlice);
            
            log.info("点赞数据同步完成，时间片：{}", previousTimeSlice);
        } catch (Exception e) {
            log.error("点赞数据同步失败", e);
            throw e;
        }
    }

    /**
     * 同步指定时间片的点赞数据
     *
     * @param timeSlice 时间片（格式：HH:mm:ss）
     */
    public void syncByTimeSlice(String timeSlice) {
        // 根据key 获取所有的 Field -> Value 映射关系
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        Map<Object, Object> tempThumbMap = redisTemplate.opsForHash().entries(tempThumbKey);
        if (CollUtil.isEmpty(tempThumbMap)) {
            log.info("时间片 {} 无数据需要同步", timeSlice);
            return;
        }
        log.info("时间片 {} 有 {} 条记录需要同步", timeSlice, tempThumbMap.size());

        // 用于批量插入的点赞记录
        List<Thumb> thumbsToInsert = new ArrayList<>();
        // 用于批量删除的查询条件
        LambdaQueryWrapper<Thumb> deleteWrapper = new LambdaQueryWrapper<>();
        boolean hasDeleteCondition = false;
        // 用于批量更新博客点赞数
        Map<Long, Long> blogThumbCountMap = new HashMap<>();

        // 解析临时记录
        for (Map.Entry<Object, Object> entry : tempThumbMap.entrySet()) {
            Object key = entry.getKey();
            String userIdBlogId = key.toString();
            int operationType = Integer.parseInt(entry.getValue().toString());

            // 解析 userId 和 blogId
            String[] parts = userIdBlogId.split(StrPool.COLON);
            if (parts.length != 2) {
                log.warn("无效的临时记录格式：{}", userIdBlogId);
                continue;
            }
            Long userId = Long.valueOf(parts[0]);
            Long blogId = Long.valueOf(parts[1]);

            // 根据操作类型执行相应逻辑
            ThumbOperationType type = ThumbOperationType.fromValue(operationType);
            switch (type) {
                case THUMB:
                    // 点赞：准备插入记录
                    Thumb thumb = new Thumb();
                    thumb.setUserId(userId);
                    thumb.setBlogId(blogId);
                    thumbsToInsert.add(thumb);
                    break;

                case CANCEL:
                    // 取消点赞：准备删除记录
                    hasDeleteCondition = true;
                    deleteWrapper.or()
                            .eq(Thumb::getUserId, userId)
                            .eq(Thumb::getBlogId, blogId);
                    break;

                case NONE:
                    // 无变化：跳过
                    log.debug("用户 {} 对博客 {} 的操作无变化", userId, blogId);
                    continue;

                default:
                    log.warn("未知的操作类型：{}, userId={}, blogId={}", operationType, userId, blogId);
                    continue;
            }
            // 累加博客点赞数变化量
            // blogThumbCountMap.put(blogId, blogThumbCountMap.getOrDefault(blogId, 0L) + thumbType);
            blogThumbCountMap.merge(blogId, (long) type.getValue(), Long::sum);
        }

        // 批量插入点赞记录
        if (!thumbsToInsert.isEmpty()) {
            thumbService.saveBatch(thumbsToInsert);
            log.info("批量插入 {} 条点赞记录", thumbsToInsert.size());
        }

        // 批量删除取消点赞记录
        if (hasDeleteCondition) {
            int deleteCount = thumbService.getBaseMapper().delete(deleteWrapper);
            log.info("批量删除 {} 条点赞记录", deleteCount);
        }

        // 批量更新博客点赞数
        if (!blogThumbCountMap.isEmpty()) {
            blogMapper.batchUpdateThumbCount(blogThumbCountMap);
            log.info("批量更新 {} 个博客的点赞数", blogThumbCountMap.size());
        }

        // 异步删除已处理的临时记录
        Thread.startVirtualThread(() -> {
            try {
                redisTemplate.delete(tempThumbKey);
                log.debug("已删除临时记录，时间片：{}", timeSlice);
            } catch (Exception e) {
                log.error("删除临时记录失败，时间片：{}", timeSlice, e);
            }
        });
    }

    /**
     * 计算上一个时间片
     * <p>
     * 示例：14:30:15 -> 14:30:00，14:30:05 -> 14:29:50
     *
     * @return 上一个时间片（格式：HH:mm:ss）
     */
    private String calculatePreviousTimeSlice() {
        Date now = new Date();
        int second = DateUtil.second(now);
        
        // 计算上一个时间片的秒数
        int sliceSecond = (second / 10 - 1) * 10;
        
        // 如果秒数为负数，说明需要回到上一分钟
        if (sliceSecond < 0) {
            sliceSecond = 50;
            now = DateUtil.offsetMinute(now, -1);
        }
        
        return DateUtil.format(now, "HH:mm:") + String.format("%02d", sliceSecond);
    }
}
