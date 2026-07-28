package com.leo.aigenweb.service;

import com.leo.aigenweb.model.dto.app.AppQueryRequest;
import com.leo.aigenweb.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.leo.aigenweb.model.entity.App;

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
}
