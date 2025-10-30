package com.aih.highlike.service.impl;

import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.mapper.ThumbMapper;
import com.aih.highlike.model.entity.Thumb;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.service.BlogService;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 点赞服务实现
 */
@Slf4j
@Service
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb> implements ThumbService {

    @Resource
    private UserService userService;

    @Resource
    private BlogService blogService;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Override
    public boolean doThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 使用用户ID作为锁，防止同一用户并发点赞
        synchronized (String.valueOf(loginUser.getId()).intern()) {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // 检查是否已点赞
                boolean exists = this.lambdaQuery()
                        .eq(Thumb::getUserId, loginUser.getId())
                        .eq(Thumb::getBlogId, blogId)
                        .exists();

                if (exists) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "已点赞，请勿重复操作");
                }

                // 增加博客点赞数
                boolean updateSuccess = blogService.incrementThumbCount(blogId);
                if (!updateSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败");
                }

                // 保存点赞记录
                // 创建点赞记录
                Thumb thumb = new Thumb();
                thumb.setUserId(loginUser.getId());
                thumb.setBlogId(blogId);
                boolean saveSuccess = this.save(thumb);

                if (!saveSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "点赞失败");
                }

                return true;
            }));
        }
    }

    @Override
    public boolean cancelThumb(Long blogId, HttpServletRequest request) {
        if (blogId == null || blogId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "博客ID无效");
        }

        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }

        // 使用用户ID作为锁，防止同一用户并发取消点赞
        synchronized (String.valueOf(loginUser.getId()).intern()) {
            return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
                // 查询点赞记录
                Thumb thumb = this.lambdaQuery()
                        .eq(Thumb::getUserId, loginUser.getId())
                        .eq(Thumb::getBlogId, blogId)
                        .one();

                if (thumb == null) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "未点赞，无法取消");
                }

                // 减少博客点赞数
                boolean updateSuccess = blogService.decrementThumbCount(blogId);
                if (!updateSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
                }

                // 删除点赞记录
                boolean removeSuccess = this.removeById(thumb.getId());
                if (!removeSuccess) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "取消点赞失败");
                }

                return true;
            }));
        }
    }
}
