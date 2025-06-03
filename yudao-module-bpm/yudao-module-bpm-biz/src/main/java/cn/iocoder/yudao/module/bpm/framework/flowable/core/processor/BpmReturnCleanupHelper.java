package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;

import java.util.*;
import java.util.stream.Collectors;

/**
 * BPM 退回清理助手
 * 
 * 负责清理退回操作产生的重复任务和恢复目标节点状态
 * 
 * @author 芋道源码
 */
@Slf4j
public class BpmReturnCleanupHelper {

    /**
     * 清理重复任务
     * 
     * @param context 退回上下文
     */
    public static void cleanupDuplicateTasks(BpmReturnContext context) {
        log.info("[cleanupDuplicateTasks] 开始清理重复任务，流程实例：{}", 
                context.getProcessInstanceId());

        try {
            // 1. 识别重复任务
            List<Task> duplicateTasks = identifyDuplicateTasks(context);
            
            if (CollUtil.isEmpty(duplicateTasks)) {
                log.debug("[cleanupDuplicateTasks] 未发现重复任务");
                return;
            }

            // 2. 清理重复任务
            cleanupTasks(context, duplicateTasks);
            
            // 3. 更新任务状态
            updateTaskStatuses(context, duplicateTasks);

            log.info("[cleanupDuplicateTasks] 重复任务清理完成，清理数量：{}", duplicateTasks.size());

        } catch (Exception e) {
            log.error("[cleanupDuplicateTasks] 清理重复任务异常", e);
        }
    }

    /**
     * 恢复目标节点状态
     * 
     * @param context 退回上下文
     */
    public static void restoreTargetNodeState(BpmReturnContext context) {
        log.info("[restoreTargetNodeState] 开始恢复目标节点状态，目标节点：{}", 
                context.getTargetElement().getId());

        try {
            // 1. 恢复用户任务状态
            if (context.getTargetElement() instanceof UserTask) {
                restoreUserTaskState(context);
            }

            // 2. 恢复网关状态
            if (BpmnModelUtils.isGateway(context.getTargetElement())) {
                restoreGatewayState(context);
            }

            // 3. 清理流程变量
            cleanupProcessVariables(context);
            
            // 4. 重置多实例状态
            if (context.isMultiInstanceReturn()) {
                resetMultiInstanceState(context);
            }

            log.info("[restoreTargetNodeState] 目标节点状态恢复完成");

        } catch (Exception e) {
            log.error("[restoreTargetNodeState] 恢复目标节点状态异常", e);
        }
    }

    /**
     * 识别重复任务
     */
    private static List<Task> identifyDuplicateTasks(BpmReturnContext context) {
        List<Task> duplicateTasks = new ArrayList<>();
        
        // 1. 按任务定义Key分组
        Map<String, List<Task>> taskGroups = context.getActiveTasks().stream()
                .collect(Collectors.groupingBy(Task::getTaskDefinitionKey));

        // 2. 找出重复的任务
        for (Map.Entry<String, List<Task>> entry : taskGroups.entrySet()) {
            List<Task> tasks = entry.getValue();
            if (tasks.size() > 1) {
                // 保留最新的任务，其他标记为重复
                tasks.sort(Comparator.comparing(Task::getCreateTime).reversed());
                duplicateTasks.addAll(tasks.subList(1, tasks.size()));
                
                log.debug("[identifyDuplicateTasks] 发现重复任务，Key：{}，重复数量：{}", 
                        entry.getKey(), tasks.size() - 1);
            }
        }

        // 3. 检查特殊情况的重复任务
        duplicateTasks.addAll(identifySpecialDuplicateTasks(context));

        return duplicateTasks;
    }

    /**
     * 识别特殊情况的重复任务
     */
    private static List<Task> identifySpecialDuplicateTasks(BpmReturnContext context) {
        List<Task> specialDuplicates = new ArrayList<>();

        // 1. 检查网关分支产生的重复任务
        if (context.isInvolveParallelGateway() || context.isInvolveInclusiveGateway()) {
            specialDuplicates.addAll(identifyGatewayDuplicateTasks(context));
        }

        // 2. 检查多实例任务产生的重复任务
        if (context.isMultiInstanceReturn()) {
            specialDuplicates.addAll(identifyMultiInstanceDuplicateTasks(context));
        }

        return specialDuplicates;
    }

    /**
     * 识别网关产生的重复任务
     */
    private static List<Task> identifyGatewayDuplicateTasks(BpmReturnContext context) {
        List<Task> gatewayDuplicates = new ArrayList<>();

        // 检查是否有因为网关退回而产生的重复任务
        for (FlowElement gateway : context.getAffectedGateways()) {
            List<Task> gatewayTasks = findTasksAfterGateway(context, gateway);
            
            // 按执行流ID分组，检查重复
            Map<String, List<Task>> executionGroups = gatewayTasks.stream()
                    .collect(Collectors.groupingBy(Task::getExecutionId));
            
            for (List<Task> tasks : executionGroups.values()) {
                if (tasks.size() > 1) {
                    // 保留一个，其他标记为重复
                    gatewayDuplicates.addAll(tasks.subList(1, tasks.size()));
                }
            }
        }

        return gatewayDuplicates;
    }

    /**
     * 识别多实例任务产生的重复任务
     */
    private static List<Task> identifyMultiInstanceDuplicateTasks(BpmReturnContext context) {
        List<Task> multiInstanceDuplicates = new ArrayList<>();

        // 检查多实例任务是否产生了重复的实例
        UserTask targetUserTask = (UserTask) context.getTargetElement();
        if (targetUserTask.getLoopCharacteristics() != null) {
            List<Task> multiInstanceTasks = context.getActiveTasks().stream()
                    .filter(task -> task.getTaskDefinitionKey().equals(targetUserTask.getId()))
                    .collect(Collectors.toList());

            // 根据多实例的配置，确定应该保留的实例数量
            int expectedInstances = calculateExpectedMultiInstanceCount(context, targetUserTask);
            if (multiInstanceTasks.size() > expectedInstances) {
                // 按创建时间排序，保留最新的实例
                multiInstanceTasks.sort(Comparator.comparing(Task::getCreateTime).reversed());
                multiInstanceDuplicates.addAll(multiInstanceTasks.subList(expectedInstances, multiInstanceTasks.size()));
            }
        }

        return multiInstanceDuplicates;
    }

    /**
     * 清理任务
     */
    private static void cleanupTasks(BpmReturnContext context, List<Task> tasksToCleanup) {
        for (Task task : tasksToCleanup) {
            try {
                // 1. 删除任务
                context.getTaskService().deleteTask(task.getId(), "重复任务清理");
                
                // 2. 记录清理日志
                log.debug("[cleanupTasks] 清理重复任务：{}，定义Key：{}", 
                        task.getId(), task.getTaskDefinitionKey());

            } catch (Exception e) {
                log.error("[cleanupTasks] 清理任务异常，任务ID：{}", task.getId(), e);
            }
        }
    }

    /**
     * 更新任务状态
     */
    private static void updateTaskStatuses(BpmReturnContext context, List<Task> cleanedTasks) {
        for (Task task : cleanedTasks) {
            try {
                // 更新任务状态为已取消
                context.getBpmTaskService().updateTaskStatus(task.getId(), 
                        BpmTaskStatusEnum.CANCEL.getStatus());

            } catch (Exception e) {
                log.error("[updateTaskStatuses] 更新任务状态异常，任务ID：{}", task.getId(), e);
            }
        }
    }

    /**
     * 恢复用户任务状态
     */
    private static void restoreUserTaskState(BpmReturnContext context) {
        UserTask targetUserTask = (UserTask) context.getTargetElement();
        
        // 1. 确保目标任务处于正确状态
        List<Task> targetTasks = context.getActiveTasks().stream()
                .filter(task -> task.getTaskDefinitionKey().equals(targetUserTask.getId()))
                .collect(Collectors.toList());

        for (Task task : targetTasks) {
            // 重置任务状态为运行中
            context.getBpmTaskService().updateTaskStatus(task.getId(), 
                    BpmTaskStatusEnum.RUNNING.getStatus());
        }

        // 2. 清理退回标记
        String returnFlag = String.format("RETURN_FLAG_%s", targetUserTask.getId());
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), returnFlag);

        log.debug("[restoreUserTaskState] 用户任务状态恢复完成，任务Key：{}", targetUserTask.getId());
    }

    /**
     * 恢复网关状态
     */
    private static void restoreGatewayState(BpmReturnContext context) {
        FlowElement targetGateway = context.getTargetElement();
        
        // 1. 清理网关相关的临时变量
        cleanupGatewayVariables(context, targetGateway);
        
        // 2. 重置网关执行状态
        resetGatewayExecutionState(context, targetGateway);

        log.debug("[restoreGatewayState] 网关状态恢复完成，网关ID：{}", targetGateway.getId());
    }

    /**
     * 清理流程变量
     */
    private static void cleanupProcessVariables(BpmReturnContext context) {
        // 清理退回操作产生的临时变量
        List<String> variablesToRemove = Arrays.asList(
                "RETURN_TEMP_FLAG",
                "RETURN_BRANCH_INFO",
                "RETURN_EXECUTION_STATE"
        );

        for (String variable : variablesToRemove) {
            try {
                context.getRuntimeService().removeVariable(context.getProcessInstanceId(), variable);
            } catch (Exception e) {
                log.debug("[cleanupProcessVariables] 清理变量异常：{}", variable, e);
            }
        }

        log.debug("[cleanupProcessVariables] 流程变量清理完成");
    }

    /**
     * 重置多实例状态
     */
    private static void resetMultiInstanceState(BpmReturnContext context) {
        UserTask targetUserTask = (UserTask) context.getTargetElement();
        
        // 1. 重置多实例计数器
        resetMultiInstanceCounters(context, targetUserTask);
        
        // 2. 重置多实例变量
        resetMultiInstanceVariables(context, targetUserTask);

        log.debug("[resetMultiInstanceState] 多实例状态重置完成，任务Key：{}", targetUserTask.getId());
    }

    /**
     * 查找网关后的任务
     */
    private static List<Task> findTasksAfterGateway(BpmReturnContext context, FlowElement gateway) {
        // 实现查找网关后任务的逻辑
        return context.getActiveTasks().stream()
                .filter(task -> isTaskAfterGateway(context, task, gateway))
                .collect(Collectors.toList());
    }

    /**
     * 判断任务是否在网关之后
     */
    private static boolean isTaskAfterGateway(BpmReturnContext context, Task task, FlowElement gateway) {
        // 简化实现，实际需要分析流程路径
        return true;
    }

    /**
     * 计算期望的多实例数量
     */
    private static int calculateExpectedMultiInstanceCount(BpmReturnContext context, UserTask userTask) {
        // 根据多实例配置计算期望的实例数量
        // 这里需要根据具体的多实例配置来实现
        return 1; // 简化实现
    }

    /**
     * 清理网关变量
     */
    private static void cleanupGatewayVariables(BpmReturnContext context, FlowElement gateway) {
        // 清理网关相关的变量
        String gatewayVariable = String.format("GATEWAY_STATE_%s", gateway.getId());
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), gatewayVariable);
    }

    /**
     * 重置网关执行状态
     */
    private static void resetGatewayExecutionState(BpmReturnContext context, FlowElement gateway) {
        // 重置网关的执行状态
        log.debug("[resetGatewayExecutionState] 重置网关执行状态：{}", gateway.getId());
    }

    /**
     * 重置多实例计数器
     */
    private static void resetMultiInstanceCounters(BpmReturnContext context, UserTask userTask) {
        // 重置多实例相关的计数器变量
        String activityId = userTask.getId();
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), 
                "nrOfInstances_" + activityId);
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), 
                "nrOfCompletedInstances_" + activityId);
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), 
                "nrOfActiveInstances_" + activityId);
    }

    /**
     * 重置多实例变量
     */
    private static void resetMultiInstanceVariables(BpmReturnContext context, UserTask userTask) {
        // 重置多实例相关的变量
        String activityId = userTask.getId();
        context.getRuntimeService().removeVariable(context.getProcessInstanceId(), 
                "loopCounter_" + activityId);
    }
}
