package com.aih.highlike.service.impl;

import com.aih.highlike.constant.UserConstant;
import com.aih.highlike.mapper.UserMapper;
import com.aih.highlike.model.entity.User;
import com.aih.highlike.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public User getLoginUser(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        return (User) request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
    }
}
