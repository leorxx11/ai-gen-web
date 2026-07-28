package com.leo.aigenweb.service;

import com.leo.aigenweb.model.dto.user.UserQueryRequest;
import com.leo.aigenweb.model.vo.LoginUserVO;
import com.leo.aigenweb.model.vo.UserVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.leo.aigenweb.model.entity.User;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

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

    LoginUserVO getLoginUserVO(User user);
    UserVO getUserVO(User user);
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 分页查询请求 -> 数据库查询条件
     * @param userQueryRequest 查询请求
     * @return
     */
    QueryWrapper getQueryWrapper(UserQueryRequest userQueryRequest);

    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    User getLoginUser(HttpServletRequest request);

    Boolean userLogout(HttpServletRequest request);


    /**
     * 获取加密后的密码
     * @param userPassword 原始密码
     * @return 加密后的密码
     */
    String getEncryptPassword(String userPassword);
}
