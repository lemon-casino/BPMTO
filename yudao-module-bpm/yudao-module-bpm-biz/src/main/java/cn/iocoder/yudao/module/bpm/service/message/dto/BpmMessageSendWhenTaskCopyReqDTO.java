package cn.iocoder.yudao.module.bpm.service.message.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * BPM 发送任务审批超时 Request DTO
 */
@Data
@Accessors(chain = true)
public class BpmMessageSendWhenTaskCopyReqDTO {

    /**
     * 流程实例的编号
     */
    @NotEmpty(message = "流程实例的编号不能为空")
    private String processInstanceId;
    /**
     * 流程实例的名字
     */
    @NotEmpty(message = "流程实例的名字不能为空")
    private String processInstanceName;
    /**
     * 抄送人的用户编号
     */
    @NotNull(message = "抄送人的用户编号不能为空")
    private Long userId;

}
