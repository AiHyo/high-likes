package com.aih.highlike.service;

import com.aih.highlike.model.entity.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

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
}
