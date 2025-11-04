package com.aih.highlike.service.impl;

import com.aih.highlike.constant.RedisLuaScript;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.listener.thumb.msg.ThumbEvent;
import com.aih.highlike.mapper.ThumbMapper;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.model.enums.LuaExecutionStatus;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.service.UserService;
import com.aih.highlike.util.RedisKeyUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 点赞服务实现 - MQ
 */
@Slf4j
@Service("thumbServiceMQ")
public class ThumbServiceMQImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private PulsarTemplate<ThumbEvent> pulsarTemplate;

    /**
     * 点赞
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    @Override
    public boolean doThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 构建 Redis Key: thumb:user:{userId}
        Long userId = loginUser.getId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 执行 lua 脚本，点赞存入 redis
        Long result = redisTemplate.execute(
                RedisLuaScript.THUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        if (LuaExecutionStatus.isFailure(result)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已点赞，请勿重复操作");
        }

        ThumbEvent event = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.INCR)
                .eventTime(LocalDateTime.now())
                .build();

        pulsarTemplate.sendAsync("thumb-topic", event).exceptionally(ex -> {
            redisTemplate.opsForHash().delete(userThumbKey, blogId, true);
            log.error("点赞事件发送失败：userId={}, blogId={}", userId, blogId, ex);
            return null;
        });
        return true;
    }

    /**
     * 取消点赞
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    @Override
    public boolean cancelThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 构建 Redis Key: thumb:user:{userId}
        Long userId = loginUser.getId();
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 执行 lua 脚本，点赞存入 redis
        Long result = redisTemplate.execute(
                RedisLuaScript.UNTHUMB_SCRIPT_MQ,
                List.of(userThumbKey),
                blogId
        );
        if (LuaExecutionStatus.isFailure(result)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未点赞");
        }

        ThumbEvent event = ThumbEvent.builder()
                .userId(userId)
                .blogId(blogId)
                .type(ThumbEvent.EventType.DECR)
                .eventTime(LocalDateTime.now())
                .build();

        pulsarTemplate.sendAsync("thumb-topic", event).exceptionally(ex -> {
            redisTemplate.opsForHash().delete(userThumbKey, blogId, true);
            log.error("取消点赞事件发送失败：userId={}, blogId={}", userId, blogId, ex);
            return null;
        });
        return true;
    }


    /**
     * 判断用户是否已点赞
     *
     * @param blogId 博客ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
       return redisTemplate.opsForHash().hasKey(RedisKeyUtil.getUserThumbKey(userId), blogId);
    }

    @Override
    public List<Object> multiGetThumbs(Long userId, List<Object> blogIdList) {
        if (userId == null || blogIdList == null || blogIdList.isEmpty()) {
            return List.of();
        }
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        return redisTemplate.opsForHash().multiGet(userThumbKey, blogIdList);
    }

    @Override
    public void syncUserThumbsToRedis(Long userId) {
        if (userId == null) {
            return;
        }

        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        List<Thumb> thumbList = this.lambdaQuery()
                .eq(Thumb::getUserId, userId)
                .list();

        if (thumbList.isEmpty()) {
            log.info("用户 {} 暂无点赞记录", userId);
            return;
        }

        Map<String, Object> thumbMap = new HashMap<>();
        for (Thumb thumb : thumbList) {
            thumbMap.put(thumb.getBlogId().toString(), thumb.getId());
        }

        redisTemplate.opsForHash().putAll(userThumbKey, thumbMap);
        log.info("用户 {} 的 {} 条点赞记录已同步到 Redis", userId, thumbList.size());
    }

    @Override
    public void clearUserThumbCache(Long userId) {
        if (userId == null) {
            return;
        }
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        redisTemplate.delete(userThumbKey);
    }

}