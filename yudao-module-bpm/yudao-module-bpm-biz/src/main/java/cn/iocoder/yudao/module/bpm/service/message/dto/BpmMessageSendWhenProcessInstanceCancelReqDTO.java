package cn.iocoder.yudao.module.bpm.service.message.dto;

import lombok.Data;

/**
 * 流程实例被取消时的消息 DTO
 */
@Data
public class BpmMessageSendWhenProcessInstanceCancelReqDTO {

    /**
     * 流程实例的编号
     */
    private String processInstanceId;
    /**
     * 流程实例的名字
     */
    private String processInstanceName;
    /**
     * 取消原因
     */
    private String reason;
    /**
     * 发起人的用户编号
     */
    private Long startUserId;

} 