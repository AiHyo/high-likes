package com.aih.highlike.service;

import com.aih.highlike.model.entity.Blog;
import com.aih.highlike.model.vo.BlogVO;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * 博客服务
 */
public interface BlogService extends IService<Blog> {

    /**
     * 根据ID获取博客视图对象
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 博客视图对象
     */
    BlogVO getBlogVO(Long blogId, HttpServletRequest request);

    /**
     * 批量获取博客视图对象列表
     *
     * @param blogList 博客列表
     * @param request  HTTP请求
     * @return 博客视图对象列表
     */
    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);

    /**
     * 增加博客点赞数
     *
     * @param blogId 博客ID
     * @return 是否成功
     */
    boolean incrementThumbCount(Long blogId);

    /**
     * 减少博客点赞数
     *
     * @param blogId 博客ID
     * @return 是否成功
     */
    boolean decrementThumbCount(Long blogId);
}
