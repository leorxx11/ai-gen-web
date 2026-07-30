package com.leo.aigenweb.service;

import com.leo.aigenweb.model.dto.app.AppQueryRequest;
import com.leo.aigenweb.model.entity.User;
import com.leo.aigenweb.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.leo.aigenweb.model.entity.App;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 应用 服务层。
 *
 * @author leo
 */
public interface AppService extends IService<App> {

    /**
     * 获取应用封装类（包含创建用户信息）
     *
     * @param app 应用
     * @return 应用封装类
     */
    AppVO getAppVO(App app);

    /**
     * 获取应用封装类列表（包含创建用户信息）
     *
     * @param appList 应用列表
     * @return 应用封装类列表
     */
    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 分页查询请求 -> 数据库查询条件
     *
     * @param appQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    /**
     * 与 AI 聊天生成代码
     *
     * @param appId 应用 ID
     * @param message 提示词
     * @param loginUser 当前登录用户
     * @return 生成的代码流
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);
}
