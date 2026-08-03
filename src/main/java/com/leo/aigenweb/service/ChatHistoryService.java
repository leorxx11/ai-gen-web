package com.leo.aigenweb.service;

import com.leo.aigenweb.model.dto.chathistory.ChatHistoryQueryRequest;
import com.leo.aigenweb.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.leo.aigenweb.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author leo
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    int loadChatHistoryToMemory(Long appId, MessageWindowChatMemory chatMemory, int maxCount);


    /**
     * 添加对话消息
     *
     * @param appId       应用 ID
     * @param message     消息内容
     * @param messageType 消息类型（user/ai）
     * @param userId      创建用户 ID
     * @return 是否添加成功
     */
    boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用 ID 删除对话历史（删除应用时关联删除）
     *
     * @param appId 应用 ID
     * @return 是否删除成功
     */
    boolean deleteByAppId(Long appId);

    /**
     * 游标分页查询某个应用的对话历史（仅应用创建者和管理员可见）
     *
     * @param appId          应用 ID
     * @param pageSize       每页数量
     * @param lastCreateTime 上一页最后一条记录的创建时间（首次加载传 null，查询最新消息）
     * @param loginUser      当前登录用户
     * @return 对话历史分页
     */
    Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                               LocalDateTime lastCreateTime,
                                               User loginUser);

    /**
     * 分页查询请求 -> 数据库查询条件
     *
     * @param chatHistoryQueryRequest 查询请求
     * @return 查询条件
     */
    QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest);
}
