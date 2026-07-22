package com.leo.aigenweb.service;

import cn.hutool.http.server.HttpServerRequest;
import com.leo.aigenweb.model.vo.LoginUserVO;
import com.mybatisflex.core.service.IService;
import com.leo.aigenweb.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.net.http.HttpRequest;

/**
 * 用户 服务层。
 *
 * @author leo
 */
public interface UserService extends IService<User> {
    /**
     *
     * @param userAccount
     * @param userPassword
     * @param checkPassword
     * @return 新用户 id
     */
    long userRegister(String userAccount,String userPassword,String checkPassword);

    LoginUserVO getLoginUserVo(User user);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密后的密码
     * @param userPassword 原始密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);
}
