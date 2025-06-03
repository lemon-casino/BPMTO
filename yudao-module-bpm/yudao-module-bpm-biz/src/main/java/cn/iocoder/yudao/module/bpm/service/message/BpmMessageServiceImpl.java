package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.framework.web.config.WebProperties;
import cn.iocoder.yudao.module.bpm.convert.message.BpmMessageConvert;
import cn.iocoder.yudao.module.bpm.enums.message.BpmMessageEnum;
import cn.iocoder.yudao.module.bpm.service.message.dto.*;
import cn.iocoder.yudao.module.system.api.sms.SmsSendApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * BPM 消息 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Validated
@Slf4j
public class BpmMessageServiceImpl implements BpmMessageService {

    @Resource
    private SmsSendApi smsSendApi;

    @Resource
    private WebProperties webProperties;

    @Override
    public void sendMessageWhenProcessInstanceApprove(BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO, Boolean isMultiInstance) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.PROCESS_INSTANCE_APPROVE);
        templateParams.put("isMultiInstance", isMultiInstance);
        sendMessage(reqDTO.getStartUserId(), BpmMessageEnum.PROCESS_INSTANCE_APPROVE, templateParams);
    }

    @Override
    public void sendMessageWhenProcessInstanceReject(BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.PROCESS_INSTANCE_REJECT);
        templateParams.put("reason", reqDTO.getReason());
        sendMessage(reqDTO.getStartUserId(), BpmMessageEnum.PROCESS_INSTANCE_REJECT, templateParams);
    }

    @Override
    public void sendMessageWhenTaskAssigned(BpmMessageSendWhenTaskCreatedReqDTO reqDTO, Boolean isMultiInstance,Integer is_delegate) {
        //如果is_delegate 为空
// 获取消息类型
        BpmMessageEnum messageType = determineMessageType(is_delegate);

// 创建模板参数
        Map<String, Object> templateParams = createBaseTemplateParams(
                reqDTO.getProcessInstanceName(),
                reqDTO.getProcessInstanceId(),
                messageType
        );
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname());
        templateParams.put("isMultiInstance", isMultiInstance);

// 发送消息
        sendMessage(reqDTO.getAssigneeUserId(), messageType, templateParams);

    }

    @Override
    public void sendMessageWhenTaskTimeout(BpmMessageSendWhenTaskTimeoutReqDTO reqDTO) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.TASK_TIMEOUT);
        templateParams.put("taskName", reqDTO.getTaskName());
        sendMessage(reqDTO.getAssigneeUserId(), BpmMessageEnum.TASK_TIMEOUT, templateParams);
    }

    @Override
    public void sendMessageWhenTaskCopy(BpmMessageSendWhenTaskCopyReqDTO reqDTO) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.TASK_COPY);
        sendMessage(reqDTO.getUserId(), BpmMessageEnum.TASK_COPY, templateParams);
    }

    @Override
    public void sendMessageWhenProcessInstanceCancel(BpmMessageSendWhenProcessInstanceCancelReqDTO reqDTO) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.TASK_CANCEL);
        sendMessage(reqDTO.getStartUserId(), BpmMessageEnum.TASK_CANCEL, templateParams);
    }

    @Override
    public void sendMessageCountersign(BpmMessageSendWhenTaskCreatedReqDTO reqDTO, Boolean isMultiInstance) {
        Map<String, Object> templateParams = createBaseTemplateParams(reqDTO.getProcessInstanceName(), 
                reqDTO.getProcessInstanceId(), BpmMessageEnum.THE_COUNTERSIGNATURE_PASSED);
        templateParams.put("taskName", reqDTO.getTaskName());
        templateParams.put("startUserNickname", reqDTO.getStartUserNickname());
        templateParams.put("isMultiInstance", isMultiInstance);
        sendMessage(reqDTO.getAssigneeUserId(), BpmMessageEnum.THE_COUNTERSIGNATURE_PASSED, templateParams);
    }

    /**
     * 创建基本的模板参数
     *
     * @param processInstanceName 流程实例名称
     * @param processInstanceId 流程实例ID
     * @param messageEnum 消息枚举
     * @return 包含基本参数的Map
     */
    private Map<String, Object> createBaseTemplateParams(String processInstanceName, String processInstanceId, BpmMessageEnum messageEnum) {
        Map<String, Object> templateParams = new HashMap<>();
        templateParams.put("processInstanceName", processInstanceName);
        templateParams.put("detailUrl", getProcessInstanceDetailUrl(processInstanceId));
        templateParams.put("reason", messageEnum.getStatus());
        return templateParams;
    }

    /**
     * 发送消息
     *
     * @param userId 用户ID
     * @param messageEnum 消息枚举
     * @param templateParams 模板参数
     */
    private void sendMessage(Long userId, BpmMessageEnum messageEnum, Map<String, Object> templateParams) {
        smsSendApi.sendSingleSmsToAdmin(BpmMessageConvert.INSTANCE.convert(userId,
                messageEnum.getSmsTemplateCode(), templateParams));
    }

    private String getProcessInstanceDetailUrl(String taskId) {
        return webProperties.getAdminUi().getUrl() + "/bpm/process-instance/detail?id=" + taskId;
    }
    // 辅助方法提取
    private BpmMessageEnum determineMessageType(Integer isDelegate) {
        if (isDelegate == null) {
            return BpmMessageEnum.TASK_ASSIGNED;
        }
        return switch (isDelegate) {
            case 1 -> BpmMessageEnum.TASK_DELEGATE;
            case 2 -> BpmMessageEnum.TASK_TRANSFER;
            default -> throw new IllegalArgumentException(
                    "Invalid delegate value: " + isDelegate);
        };
    }
}
