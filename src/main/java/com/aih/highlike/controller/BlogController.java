package com.aih.highlike.controller;

import com.aih.highlike.common.BaseResponse;
import com.aih.highlike.common.ResultUtils;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.model.entity.Blog;
import com.aih.highlike.model.vo.BlogVO;
import com.aih.highlike.service.BlogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 博客控制器
 */
@RestController
@RequestMapping("/blog")
@Tag(name = "博客接口")
public class BlogController {

    @Resource
    private BlogService blogService;

    /**
     * 根据ID获取博客
     */
    @GetMapping("/get")
    @Operation(summary = "根据ID获取博客")
    public BaseResponse<BlogVO> getBlog(@RequestParam Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }
        BlogVO blogVO = blogService.getBlogVO(blogId, request);
        return ResultUtils.success(blogVO);
    }

    /**
     * 获取博客列表
     */
    @GetMapping("/list")
    @Operation(summary = "获取博客列表")
    public BaseResponse<List<BlogVO>> listBlogs(HttpServletRequest request) {
        List<Blog> blogList = blogService.list();
        List<BlogVO> blogVOList = blogService.getBlogVOList(blogList, request);
        return ResultUtils.success(blogVOList);
    }
}
