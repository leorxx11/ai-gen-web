package com.leo.aigenweb.service.impl;

import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.leo.aigenweb.model.entity.App;
import com.leo.aigenweb.mapper.AppMapper;
import com.leo.aigenweb.service.AppService;
import org.springframework.stereotype.Service;

/**
 * 应用 服务层实现。
 *
 * @author leo
 */
@Service
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{

}
