package com.aih.highlike.controller;

import com.aih.highlike.common.BaseResponse;
import com.aih.highlike.common.ResultUtils;
import com.aih.highlike.constant.UserConstant;
import com.aih.highlike.exception.BusinessException;
import com.aih.highlike.exception.ErrorCode;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.service.ThumbService;
import com.aih.highlike.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户控制器
 */
@RestController
@RequestMapping("/user")
@Tag(name = "用户接口")
public class UserController {

    @Resource
    private UserService userService;

    @Resource(name = "thumbServiceAsync")
    private ThumbService thumbService;

    /**
     * 用户登录（简化版，仅用于测试）
     */
    @GetMapping("/login")
    @Operation(summary = "用户登录")
    public BaseResponse<User> login(@RequestParam Long userId, HttpServletRequest request) {
        if (userId == null || userId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID无效");
        }

        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 设置登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);

        // 同步用户点赞记录到 Redis
        thumbService.syncUserThumbsToRedis(userId);

        return ResultUtils.success(user);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    @Operation(summary = "获取当前登录用户")
    public BaseResponse<User> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return ResultUtils.success(loginUser);
    }

    /**
     * 用户登出
     */
    @GetMapping("/logout")
    @Operation(summary = "用户登出")
    public BaseResponse<Boolean> logout(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        if (loginUser != null) {
            // 清除用户点赞缓存
            thumbService.clearUserThumbCache(loginUser.getId());
        }
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return ResultUtils.success(true);
    }
}
