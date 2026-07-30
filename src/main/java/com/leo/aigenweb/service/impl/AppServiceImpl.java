package com.leo.aigenweb.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.leo.aigenweb.core.AiCodeGeneratorFacade;
import com.leo.aigenweb.exception.BusinessException;
import com.leo.aigenweb.exception.ErrorCode;
import com.leo.aigenweb.exception.ThrowUtils;
import com.leo.aigenweb.model.dto.app.AppQueryRequest;
import com.leo.aigenweb.model.entity.User;
import com.leo.aigenweb.model.enums.CodeGenTypeEnum;
import com.leo.aigenweb.model.vo.AppVO;
import com.leo.aigenweb.model.vo.UserVO;
import com.leo.aigenweb.service.UserService;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.leo.aigenweb.model.entity.App;
import com.leo.aigenweb.mapper.AppMapper;
import com.leo.aigenweb.service.AppService;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 应用 服务层实现。
 *
 * @author leo
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

    @Resource
    private UserService userService;
    @Resource
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        // 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0,ErrorCode.PARAMS_ERROR,"应用 ID 不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(message),ErrorCode.PARAMS_ERROR,"提示词不能为空");
        // 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null,ErrorCode.PARAMS_ERROR,"应用不存在");

        // 权限校验，仅本人修改自己的应用
        if(!app.getUserId().equals(loginUser.getId())){
            throw  new BusinessException(ErrorCode.NO_AUTH_ERROR,"无权限访问该应用");
        }

        // 获取应用生成类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == null){
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,"不支持的代码生成类型");
        }

        // 调用 AI 生成代码
        return aiCodeGeneratorFacade.generateAndSaveCodeStream(message,codeGenTypeEnum,appId);
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtils.copyProperties(app, appVO);
        // 关联查询创建用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            appVO.setUser(userService.getUserVO(user));
        }
        return appVO;
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量查询创建用户信息，避免逐条查询
        Set<Long> userIdSet = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream()
                .map(app -> {
                    AppVO appVO = new AppVO();
                    BeanUtils.copyProperties(app, appVO);
                    appVO.setUser(userVOMap.get(app.getUserId()));
                    return appVO;
                })
                .collect(Collectors.toList());
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

}
