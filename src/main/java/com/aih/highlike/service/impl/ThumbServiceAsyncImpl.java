package com.aih.highlike.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.aih.highlike.constant.RedisLuaScript;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
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
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * 点赞服务异步实现
 * <p>
 * 核心特点：
 * 1. 使用 Lua 脚本保证原子性，无需加锁和事务
 * 2. 点赞操作只写 Redis，定时任务异步批量同步到数据库
 * 3. 使用时间分片策略，便于批量处理
 */
@Slf4j
@Service("thumbServiceAsync")
public class ThumbServiceAsyncImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 点赞（异步版本）
     * <p>
     * 使用 Lua 脚本原子性地完成：检查状态 -> 记录点赞 -> 写入临时记录
     * <p>
     * 定时任务会批量同步临时记录到数据库
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

        Long userId = loginUser.getId();
        
        // 计算当前时间片（按10秒分片）
        String timeSlice = calculateTimeSlice();
        
        // 构建 Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                RedisLuaScript.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId,
                blogId
        );

        // 判断执行结果
        if (LuaExecutionStatus.isFailure(result)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "已点赞，请勿重复操作");
        }

        log.debug("用户 {} 点赞博客 {} 成功，时间片：{}", userId, blogId, timeSlice);
        return LuaExecutionStatus.isSuccess(result);
    }

    /**
     * 取消点赞（异步版本）
     * <p>
     * 使用 Lua 脚本原子性地完成：检查状态 -> 删除点赞 -> 写入临时记录
     * <p>
     * 定时任务会批量同步临时记录到数据库
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

        Long userId = loginUser.getId();
        
        // 计算当前时间片（按10秒分片）
        String timeSlice = calculateTimeSlice();
        
        // 构建 Redis Key
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);

        // 执行 Lua 脚本
        Long result = redisTemplate.execute(
                RedisLuaScript.CANCEL_THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                userId,
                blogId
        );

        // 判断执行结果
        if (LuaExecutionStatus.isFailure(result)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未点赞，无法取消");
        }

        log.debug("用户 {} 取消点赞博客 {} 成功，时间片：{}", userId, blogId, timeSlice);
        return LuaExecutionStatus.isSuccess(result);
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        if (blogId == null || userId == null) {
            return false;
        }
        String userThumbKey = RedisKeyUtil.getUserThumbKey(userId);
        return redisTemplate.opsForHash().hasKey(userThumbKey, blogId.toString());
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

        java.util.Map<String, Object> thumbMap = new java.util.HashMap<>();
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
        log.info("用户 {} 的点赞缓存已清除", userId);
    }

    /**
     * 计算当前时间片（按10秒分片）
     * <p>
     * 示例：14:30:23 -> 14:30:20
     *
     * @return 时间片字符串（格式：HH:mm:ss）
     */
    private String calculateTimeSlice() {
        Date now = new Date();
        int second = DateUtil.second(now);
        // 向下取整到10的倍数
        int sliceSecond = (second / 10) * 10;
        return DateUtil.format(now, "HH:mm:") + String.format("%02d", sliceSecond);
    }
}
