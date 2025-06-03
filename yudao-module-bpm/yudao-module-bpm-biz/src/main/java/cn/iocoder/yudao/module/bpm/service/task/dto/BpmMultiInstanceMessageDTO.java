package cn.iocoder.yudao.module.bpm.service.task.dto;

import lombok.Data;

import java.util.List;

/**
 * BPM 多实例（会签）消息传输对象
 * 用于收集会签节点的审批信息，包括审批人和拒绝人
 */
@Data
public class BpmMultiInstanceMessageDTO {

    /**
     * 流程实例的编号
     */
    private String processInstanceId;
    
    /**
     * 流程实例的名称
     */
    private String processInstanceName;
    
    /**
     * 活动节点ID
     */
    private String activityId;
    
    /**
     * 活动节点名称
     */
    private String activityName;
    
    /**
     * 审批通过的用户ID列表
     */
    private List<Long> approveUserIds;
    
    /**
     * 审批拒绝的用户ID列表
     */
    private List<Long> rejectUserIds;
    
    /**
     * 审批原因/意见
     */
    private String reason;
    
} 