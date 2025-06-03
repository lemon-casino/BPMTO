package cn.iocoder.yudao.module.bpm.service.task;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.TASK_TARGET_NODE_NOT_EXISTS;

/**
 * BPM 任务跳转 Service 实现类
 *
 * @author 芋道源码
 */
@Service
@Slf4j
public class BpmTaskJumpServiceImpl implements BpmTaskJumpService {

    @Resource
    private RuntimeService runtimeService;
    @Resource
    private TaskService taskService;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmModelService modelService;
    /**
     * 更新流程任务的 status 状态
     *
     * @param id     任务编号
     * @param status 状态
     */
    private void updateTaskStatus(String id, Integer status) {
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_STATUS, status);
    }

    /**
     * 更新流程任务的 status 状态、reason 理由
     *
     * @param id     任务编号
     * @param status 状态
     * @param reason 理由（审批通过、审批不通过的理由）
     */
    private void updateTaskStatusAndReason(String id, Integer status, String reason) {
        updateTaskStatus(id, status);
        taskService.setVariableLocal(id, BpmnVariableConstants.TASK_VARIABLE_REASON, reason);
    }

    /**
     * 获取流程实例和BPMN模型信息
     *
     * @param executionId 执行实例ID
     * @param targetTaskKey 目标任务Key
     * @return 包含流程实例、流程实例ID和BPMN模型的对象数组
     */
    private Object[] getProcessInstanceAndBpmnModel(String executionId, String targetTaskKey) {
        // 1. 获取执行实例
        Execution execution = runtimeService.createExecutionQuery()
                .executionId(executionId)
                .singleResult();

        if (execution == null) {
            log.warn("[getProcessInstanceAndBpmnModel][未找到执行实例] executionId:{}", executionId);
            return null;
        }

        // 2. 获取流程实例
        String processInstanceId = execution.getProcessInstanceId();
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 3. 获取流程模型
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, targetTaskKey);
        if (targetElement == null) {
            throw exception(TASK_TARGET_NODE_NOT_EXISTS);
        }

        return new Object[]{processInstance, processInstanceId, bpmnModel};
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jumpMultiToSingle(String processInstanceId, List<String> sourceTaskKeys, String targetTaskKey, String reason) {
        // 1. 校验流程实例存在
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 2. 获取流程模型
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, targetTaskKey);
        if (targetElement == null) {
            throw exception(TASK_TARGET_NODE_NOT_EXISTS);
        }

        // 3. 获取所有活动任务 - 修复方法调用
        List<Task> activeTasks = new ArrayList<>();
        TaskQuery query = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active();

        // 过滤出匹配的任务
        List<Task> allTasks = query.list();
        for (Task task : allTasks) {
            if (sourceTaskKeys.contains(task.getTaskDefinitionKey())) {
                activeTasks.add(task);
            }
        }

        if (CollUtil.isEmpty(activeTasks)) {
            log.warn("[jumpMultiToSingle][未找到活动任务] processInstanceId:{}, sourceTaskKeys:{}",
                    processInstanceId, sourceTaskKeys);
            return;
        }

        // 4. 更新任务状态并添加备注
        for (Task task : activeTasks) {
            taskService.addComment(task.getId(), processInstanceId,
                    BpmCommentTypeEnum.TIMEOUT_JUMP.getType(), "任务跳转：" + reason);
            updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.CANCEL.getStatus(), reason);
        }

        // 5. 执行跳转
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(processInstanceId)
                    .moveActivityIdsToSingleActivityId(sourceTaskKeys, targetTaskKey)
                    .changeState();
            log.info("[jumpMultiToSingle][跳转成功] 从节点:{} 跳转到节点:{}",
                    sourceTaskKeys, targetTaskKey);
        } catch (Exception e) {
            log.error("[jumpMultiToSingle][跳转失败] 从节点:{} 跳转到节点:{}, 错误信息:{}",
                    sourceTaskKeys, targetTaskKey, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jumpSingleToMulti(String processInstanceId, String sourceTaskKey, List<String> targetTaskKeys, String reason) {
        // 1. 校验流程实例存在
        ProcessInstance processInstance = processInstanceService.getProcessInstance(processInstanceId);
        if (processInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 2. 获取流程模型
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        for (String targetKey : targetTaskKeys) {
            FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, targetKey);
            if (targetElement == null) {
                throw exception(TASK_TARGET_NODE_NOT_EXISTS);
            }
        }

        // 3. 获取活动任务
        Task task = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(sourceTaskKey)
                .active()
                .singleResult();

        if (task == null) {
            log.warn("[jumpSingleToMulti][未找到活动任务] processInstanceId:{}, sourceTaskKey:{}",
                    processInstanceId, sourceTaskKey);
            return;
        }

        // 4. 更新任务状态并添加备注
        taskService.addComment(task.getId(), processInstanceId,
                BpmCommentTypeEnum.TIMEOUT_JUMP.getType(), "任务跳转：" + reason);
        updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.CANCEL.getStatus(), reason);

        // 5. 执行跳转
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(processInstanceId)
                    .moveSingleActivityIdToActivityIds(sourceTaskKey, targetTaskKeys)
                    .changeState();
            log.info("[jumpSingleToMulti][跳转成功] 从节点:{} 跳转到节点:{}",
                    sourceTaskKey, targetTaskKeys);
        } catch (Exception e) {
            log.error("[jumpSingleToMulti][跳转失败] 从节点:{} 跳转到节点:{}, 错误信息:{}",
                    sourceTaskKey, targetTaskKeys, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jumpExecutionToActivity(String executionId, String targetTaskKey, String reason) {
        // 获取流程实例和BPMN模型信息
        Object[] result = getProcessInstanceAndBpmnModel(executionId, targetTaskKey);
        if (result == null) {
            return;
        }

        ProcessInstance processInstance = (ProcessInstance) result[0];
        String processInstanceId = (String) result[1];

        // 4. 获取关联的任务
        Task task = taskService.createTaskQuery()
                .executionId(executionId)
                .active()
                .singleResult();

        if (task != null) {
            // 更新任务状态并添加备注
            taskService.addComment(task.getId(), processInstanceId,
                    BpmCommentTypeEnum.RETURN.getType(), "任务跳转：" + reason);
            updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RETURN.getStatus(), reason);
        }

        // 5. 执行跳转
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .moveExecutionToActivityId(executionId, targetTaskKey)
                    .changeState();
            log.info("[jumpExecutionToActivity][跳转成功] 执行实例:{} 跳转到节点:{}",
                    executionId, targetTaskKey);
        } catch (Exception e) {
            log.error("[jumpExecutionToActivity][跳转失败] 执行实例:{} 跳转到节点:{}, 错误信息:{}",
                    executionId, targetTaskKey, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jumpExecutionToMultiActivities(String executionId, List<String> targetTaskKeys, String reason) {
        // 1. 获取执行实例
        Execution execution = runtimeService.createExecutionQuery()
                .executionId(executionId)
                .singleResult();

        if (execution == null) {
            log.warn("[jumpExecutionToMultiActivities][未找到执行实例] executionId:{}", executionId);
            return;
        }

        // 2. 获取流程实例
        ProcessInstance processInstance = processInstanceService.getProcessInstance(execution.getProcessInstanceId());
        if (processInstance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }

        // 3. 获取流程模型
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processInstance.getProcessDefinitionId());
        for (String targetKey : targetTaskKeys) {
            FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, targetKey);
            if (targetElement == null) {
                throw exception(TASK_TARGET_NODE_NOT_EXISTS);
            }
        }

        // 4. 获取关联的任务
        Task task = taskService.createTaskQuery()
                .executionId(executionId)
                .active()
                .singleResult();

        if (task != null) {
            // 更新任务状态并添加备注
            taskService.addComment(task.getId(), processInstance.getId(),
                    BpmCommentTypeEnum.TIMEOUT_JUMP.getType(), "任务跳转：" + reason);
            updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.CANCEL.getStatus(), reason);
        }

        // 5. 执行跳转
        try {
            runtimeService.createChangeActivityStateBuilder()
                    .moveSingleExecutionToActivityIds(executionId, targetTaskKeys)
                    .changeState();
            log.info("[jumpExecutionToMultiActivities][跳转成功] 执行实例:{} 跳转到节点:{}",
                    executionId, targetTaskKeys);
        } catch (Exception e) {
            log.error("[jumpExecutionToMultiActivities][跳转失败] 执行实例:{} 跳转到节点:{}, 错误信息:{}",
                    executionId, targetTaskKeys, e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void jumpMultiExecutionsToActivity(List<String> executionIds, ProcessInstance processInstance, String targetTaskKey, String reason) {
        if (CollUtil.isEmpty(executionIds)) {
            log.warn("[jumpMultiExecutionsToActivity][执行实例为空]");
            return;
        }

        // 获取流程实例和BPMN模型信息
        Object[] result = getProcessInstanceAndBpmnModel(executionIds.get(0), targetTaskKey);
        if (result == null) {
            return;
        }

        // 使用传入的processInstance参数，如果为null则使用从执行ID获取的实例
        String processInstanceId;
        if (processInstance != null) {
            processInstanceId = processInstance.getId();
            log.info("[jumpMultiExecutionsToActivity] 使用传入的流程实例: {}", processInstanceId);
        } else {
            processInstanceId = (String) result[1];
            log.info("[jumpMultiExecutionsToActivity] 使用从执行ID获取的流程实例: {}", processInstanceId);
        }

        try {
            // 1. 更新当前分支任务的状态
            String executionId = executionIds.get(0); // 因为现在只传入当前分支的executionId
            Task task = taskService.createTaskQuery()
                    .executionId(executionId)
                    .active()
                    .singleResult();

            if (task != null) {
                // 更新任务状态并添加备注
                taskService.addComment(task.getId(), processInstanceId,
                        BpmCommentTypeEnum.RETURN.getType(), "任务跳转：" + reason);
                updateTaskStatusAndReason(task.getId(), BpmTaskStatusEnum.RETURN.getStatus(), reason);
                log.info("[jumpMultiExecutionsToActivity] 已更新任务状态 taskId:{}, executionId:{}", task.getId(), executionId);
            }

            log.info("[jumpMultiExecutionsToActivity] 执行跳转 processInstanceId: {}, targetTaskKey: {}, executionIds: {}",
                    processInstanceId, targetTaskKey, executionIds);

            // 2. 执行跳转
            runtimeService.createChangeActivityStateBuilder()
                    .moveExecutionsToSingleActivityId(executionIds, targetTaskKey)
                    .changeState();

            log.info("[jumpMultiExecutionsToActivity] 跳转成功 流程实例: {}, 目标节点: {}",
                    processInstanceId, targetTaskKey);
        } catch (Exception e) {
            log.error("[jumpMultiExecutionsToActivity] 跳转失败 流程实例: {}, 目标节点: {}, 错误信息: {}",
                    processInstanceId, targetTaskKey, e.getMessage(), e);
            throw e;
        }
    }
} 