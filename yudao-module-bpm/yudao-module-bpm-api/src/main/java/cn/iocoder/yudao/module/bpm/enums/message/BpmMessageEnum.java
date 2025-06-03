package cn.iocoder.yudao.module.bpm.enums.message;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Bpm 消息的枚举
 *
 * @author 芋道源码
 */
@AllArgsConstructor
@Getter
public enum BpmMessageEnum {

    PROCESS_INSTANCE_APPROVE("bpm_process_instance_approve", "已通过"), // 流程任务被审批通过时，发送给申请人
    PROCESS_INSTANCE_REJECT("bpm_process_instance_reject", "已拒绝"), // 流程任务被审批不通过时，发送给申请人
    TASK_ASSIGNED("bpm_task_assigned", "待审批"), // 任务被分配时，发送给审批人
    TASK_TIMEOUT("bpm_task_timeout", "已超时"), // 任务审批超时时，发送给审批人
    TASK_COPY("bpm_task_copy","任务抄送"), //任务审批抄送时，发送给抄送人
    //取消
    TASK_CANCEL("bpm_task_cancel","任务取消"), //任务审批取消时，发送给审批人
    THE_COUNTERSIGNATURE_PASSED("bpm_task_count","会签过程"),//会签过程中
    //转办 transfer
    TASK_TRANSFER("bpm_task_assigned","转办"),  //任务转办或委派时，发送给转办或委派人
    //委派 delegate
    TASK_DELEGATE("bpm_task_assigned","委派");
    /**
     * 短信模板的标识
     *
     * 关联 SmsTemplateDO 的 code 属性
     */
    private final String smsTemplateCode;

    /**
     * 状态
     */
    private final String status;

}
