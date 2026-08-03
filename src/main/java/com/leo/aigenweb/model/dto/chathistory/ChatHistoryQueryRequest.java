package com.leo.aigenweb.model.dto.chathistory;

import com.leo.aigenweb.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Data
public class ChatHistoryQueryRequest extends PageRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 消息
     */
    private String message;

    /**
     * 消息类型（user/ai）
     */
    private String messageType;

    /**
     * 应用id
     */
    private Long appId;

    /**
     * 创建用户id
     */
    private Long userId;

    /**
     * 游标查询 - 上一页最后一条记录的创建时间（用于向前加载更多历史消息）
     */
    private LocalDateTime lastCreateTime;

    private static final long serialVersionUID = 1L;
}
