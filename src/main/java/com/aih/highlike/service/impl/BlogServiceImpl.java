package com.aih.highlike.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.mapper.BlogMapper;
import com.aih.highlike.model.entity.Blog;
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
import java.util.stream.Collectors;

/**
 * 博客服务实现
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {

    @Resource
    private UserService userService;

    @Resource(name = "thumbServiceRedis")
    @Lazy
    private ThumbService thumbService;

    /**
     * 根据ID获取博客视图对象
     * <p>
     * 包含博客基本信息和当前用户的点赞状态
     *
     * @param blogId  博客ID
     * @param request HTTP请求
     * @return 博客视图对象
     */
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

    /**
     * 批量获取博客视图对象列表
     * <p>
     * 性能优化：
     * 1. 使用 Redis HMGET 批量查询点赞状态，避免 N+1 查询问题
     * 2. 一次性获取所有博客的点赞状态，而非循环查询
     * 3. 在内存中构建 Map，快速判断每个博客的点赞状态
     * <p>
     * 查询流程：
     * 1. 提取所有博客ID列表
     * 2. 使用 multiGet 批量从 Redis 获取点赞记录
     * 3. 将点赞信息存入 Map（blogId -> hasThumb）
     * 4. 遍历博客列表，从 Map 中获取点赞状态
     *
     * @param blogList 博客列表
     * @param request  HTTP请求
     * @return 博客视图对象列表
     */
    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        if (CollUtil.isEmpty(blogList)) {
            return List.of();
        }

        User loginUser = userService.getLoginUser(request);
        Map<Long, Boolean> thumbMap = new HashMap<>();

        // 批量从 Redis 查询当前用户的点赞记录
        if (loginUser != null) {
            // 提取所有博客ID，转为字符串列表（Redis Hash 的 field 是字符串）
            List<Object> blogIdList = blogList.stream()
                    .map(blog -> blog.getId().toString())
                    .collect(Collectors.toList());

            // 使用 multiGet 批量获取点赞记录
            // Redis 操作：HMGET thumb:user:{userId} {blogId1} {blogId2} ...
            // 返回的列表与 blogIdList 顺序一一对应
            List<Object> thumbList = thumbService.multiGetThumbs(loginUser.getId(), blogIdList);

            // 将点赞信息存入 map，方便后续快速查询
            // thumbList[i] 对应 blogIdList[i]
            // 如果 thumbList[i] 不为 null，说明该博客已点赞
            for (int i = 0; i < thumbList.size(); i++) {
                if (thumbList.get(i) != null) {
                    Long blogId = Long.valueOf(blogIdList.get(i).toString());
                    thumbMap.put(blogId, true);
                }
            }
        }

        // 转换为 BlogVO 列表，并设置点赞状态
        return blogList.stream()
                .map(blog -> {
                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
                    // 从 Map 中获取点赞状态，默认为 false（未点赞）
                    blogVO.setHasThumb(thumbMap.getOrDefault(blog.getId(), false));
                    return blogVO;
                })
                .collect(Collectors.toList());
    }

    /**
     * 增加博客点赞数
     * <p>
     * 使用数据库层面的原子操作，避免并发问题
     * SQL: UPDATE blog SET thumbCount = thumbCount + 1 WHERE id = ?
     *
     * @param blogId 博客ID
     * @return 是否成功
     */
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

    /**
     * 减少博客点赞数
     * <p>
     * 使用数据库层面的原子操作，避免并发问题
     * 使用 GREATEST 函数确保点赞数不会小于 0
     * SQL: UPDATE blog SET thumbCount = GREATEST(thumbCount - 1, 0) WHERE id = ?
     *
     * @param blogId 博客ID
     * @return 是否成功
     */
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
     * <p>
     * 转换内容：
     * 1. 复制博客基本信息
     * 2. 如果用户已登录，从 Redis 查询点赞状态
     * 3. 如果用户未登录，点赞状态设为 false
     *
     * @param blog      博客实体
     * @param loginUser 当前登录用户
     * @return 博客视图对象
     */
    private BlogVO convertToBlogVO(Blog blog, User loginUser) {
        BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);

        if (loginUser != null) {
            // 从 Redis 查询是否已点赞
            // Redis 操作：HEXISTS thumb:user:{userId} {blogId}
            Boolean hasThumb = thumbService.hasThumb(blog.getId(), loginUser.getId());
            blogVO.setHasThumb(hasThumb);
        } else {
            blogVO.setHasThumb(false);
        }

        return blogVO;
    }
}
