package com.aih.highlike.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.mapper.BlogMapper;
import com.aih.highlike.model.entity.Blog;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.model.vo.BlogVO;
import com.aih.highlike.service.BlogService;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 博客服务实现
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;

    @Override
    public BlogVO getBlogVO(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        Blog blog = this.getById(blogId);
        if (blog == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "博客不存在");
        }

        User loginUser = userService.getLoginUser(request);
        return convertToBlogVO(blog, loginUser);
    }

    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        if (CollUtil.isEmpty(blogList)) {
            return List.of();
        }

        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> thumbMap = new HashMap<>();

        // 批量查询当前用户的点赞记录
        if (loginUser != null) {
            Set<Long> blogIdSet = blogList.stream()
                    .map(Blog::getId)
                    .collect(Collectors.toSet());

            List<Thumb> thumbList = thumbService.lambdaQuery()
                    .eq(Thumb::getUserId, loginUser.getId())
                    .in(Thumb::getBlogId, blogIdSet)
                    .list();

            thumbList.forEach(thumb -> thumbMap.put(thumb.getBlogId(), true));
        }

        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    blogVO.setHasThumb(thumbMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean incrementThumbCount(Long blogId) {
        if (blogId == null || blogId <= 0) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(Blog::getId, blogId)
                .setSql("thumbCount = thumbCount + 1")
                .update();
    }

    @Override
    public boolean decrementThumbCount(Long blogId) {
        if (blogId == null || blogId <= 0) {
            return false;
        }
        return this.lambdaUpdate()
                .eq(Blog::getId, blogId)
                .setSql("thumbCount = GREATEST(thumbCount - 1, 0)")
                .update();
    }

    /**
     * 将Blog实体转换为BlogVO
     */
    private BlogVO convertToBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);

        if (loginUser != null) {
            boolean hasThumb = thumbService.lambdaQuery()
                    .eq(Thumb::getUserId, loginUser.getId())
                    .eq(Thumb::getBlogId, blog.getId())
                    .exists();
            blogVO.setHasThumb(hasThumb);
        } else {
            blogVO.setHasThumb(false);
        }

        return blogVO;
    }
}
