package cn.iocoder.yudao.module.bpm.service.task;

import org.flowable.engine.runtime.ProcessInstance;

import java.util.List;

/**
 * BPM 任务跳转 Service 接口
 *
 * 提供各种任务跳转、动态路由的能力
 *
 * @author 芋道源码
 */
public interface BpmTaskJumpService {

    /**
     * 特定执行实例跳转到特定任务
     *
     * @param executionId 执行实例ID
     * @param targetTaskKey 目标任务定义Key
     * @param reason 跳转原因
     */
    void jumpExecutionToActivity(String executionId, String targetTaskKey, String reason);

    /**
     * 多个执行实例跳转到单个任务
     *
     * @param executionIds 执行实例ID列表
     * @param targetTaskKey 目标任务定义Key
     * @param reason 跳转原因
     */
    void jumpMultiExecutionsToActivity(List<String> executionIds, ProcessInstance processInstance,String targetTaskKey, String reason);



    /**
     * 多任务跳转到单个任务
     *
     * @param processInstanceId 流程实例ID
     * @param sourceTaskKeys 源任务定义Key列表
     * @param targetTaskKey 目标任务定义Key
     * @param reason 跳转原因
     */
    void jumpMultiToSingle(String processInstanceId, List<String> sourceTaskKeys, String targetTaskKey, String reason);

    /**
     * 单个任务跳转到多个任务
     *
     * @param processInstanceId 流程实例ID
     * @param sourceTaskKey 源任务定义Key
     * @param targetTaskKeys 目标任务定义Key列表
     * @param reason 跳转原因
     */
    void jumpSingleToMulti(String processInstanceId, String sourceTaskKey, List<String> targetTaskKeys, String reason);

    /**
     * 特定执行实例跳转到多个任务
     *
     * @param executionId 执行实例ID
     * @param targetTaskKeys 目标任务定义Key列表
     * @param reason 跳转原因
     */
    void jumpExecutionToMultiActivities(String executionId, List<String> targetTaskKeys, String reason);

}