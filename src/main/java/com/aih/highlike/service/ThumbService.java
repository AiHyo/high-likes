package com.aih.highlike.service;

import com.aih.highlike.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 点赞服务
 */
public interface ThumbService extends IService<Thumb> {

    /**
     * 点赞
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    boolean doThumb(Long blogId, HttpServletRequest request);

    /**
     * 取消点赞
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 是否成功
     */
    boolean cancelThumb(Long blogId, HttpServletRequest request);

    /**
     * 判断用户是否已点赞
     *
     * @param blogId 博客ID
     * @param userId 用户ID
     * @return 是否已点赞
     */
    Boolean hasThumb(Long blogId, Long userId);

    /**
     * 批量获取用户的点赞记录
     *
     * @param userId     用户ID
     * @param blogIdList 博客ID列表
     * @return 点赞记录列表
     */
    List<Object> multiGetThumbs(Long userId, List<Object> blogIdList);

    /**
     * 将用户的点赞记录从数据库同步到 Redis
     * <p>
     * 使用场景：用户登录时初始化缓存
     *
     * @param userId 用户ID
     */
    void syncUserThumbsToRedis(Long userId);

    /**
     * 清除用户的点赞缓存
     * <p>
     * 使用场景：用户登出时清理缓存
     *
     * @param userId 用户ID
     */
    void clearUserThumbCache(Long userId);
}
