package com.aih.highlike.service;

import com.aih.highlike.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 用户服务
 */
public interface UserService extends IService<User> {

    /**
     * 获取当前登录用户
     *
     * @param request HTTP请求
     * @return 当前登录用户，未登录返回null
     */
    User getLoginUser(HttpServletRequest request);
}
