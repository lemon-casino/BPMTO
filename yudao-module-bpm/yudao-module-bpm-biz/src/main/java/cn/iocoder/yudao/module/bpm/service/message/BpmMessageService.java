package cn.iocoder.yudao.module.bpm.service.message;

import cn.iocoder.yudao.module.bpm.service.message.dto.*;
import jakarta.validation.Valid;

/**
 * BPM 消息 Service 接口
 *
 * TODO 芋艿：未来支持消息的可配置；不同的流程，在什么场景下，需要发送什么消息，消息的内容是什么；
 *
 * @author 芋道源码
 */
public interface BpmMessageService {

    /**
     * 发送流程实例被通过的消息
     *
     * @param reqDTO 发送信息
     */
    void sendMessageWhenProcessInstanceApprove(@Valid BpmMessageSendWhenProcessInstanceApproveReqDTO reqDTO,Boolean isMultiInstance);

    /**
     * 发送流程实例被不通过的消息
     *
     * @param reqDTO 发送信息
     */
    void sendMessageWhenProcessInstanceReject(@Valid BpmMessageSendWhenProcessInstanceRejectReqDTO reqDTO);

    /**
     * 发送任务被分配的消息
     *
     * @param reqDTO 发送信息
     * @param isMultiInstance 是否是多实例
     */
    void sendMessageWhenTaskAssigned(@Valid BpmMessageSendWhenTaskCreatedReqDTO reqDTO,Boolean isMultiInstance,Integer  is_delegate);

    /**
     * 发送任务审批超时的消息
     *
     * @param reqDTO 发送信息
     */
    void sendMessageWhenTaskTimeout(@Valid BpmMessageSendWhenTaskTimeoutReqDTO reqDTO);

    void sendMessageWhenTaskCopy(@Valid BpmMessageSendWhenTaskCopyReqDTO reqDTO);

    void sendMessageWhenProcessInstanceCancel(BpmMessageSendWhenProcessInstanceCancelReqDTO reqDTO);
    /*会签审批通知*/
    void  sendMessageCountersign(@Valid BpmMessageSendWhenTaskCreatedReqDTO reqDTO,Boolean isMultiInstance);


}
