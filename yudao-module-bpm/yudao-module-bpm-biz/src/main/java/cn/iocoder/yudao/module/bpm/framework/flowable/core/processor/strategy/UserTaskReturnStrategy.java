package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.UserTask;
import org.flowable.task.api.Task;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户任务退回策略
 * 
 * 处理退回到用户任务节点的逻辑
 * 
 * @author 芋道源码
 */
@Slf4j
public class UserTaskReturnStrategy implements BpmReturnStrategy {

    @Override
    public void calculateReturnScope(BpmReturnContext context) {
        log.info("[calculateReturnScope] 开始计算用户任务退回范围，目标节点：{}", 
                context.getTargetElement().getId());

        // 1. 计算需要退回的任务范围
        calculateTasksToReturn(context);
        
        // 2. 计算需要移动的执行流
        calculateExecutionsToMove(context);
        
        // 3. 处理多实例任务的特殊情况
        handleMultiInstanceTasks(context);
        
        // 4. 分析退回路径
        analyzeReturnPath(context);

        log.info("[calculateReturnScope] 用户任务退回范围计算完成，任务数：{}，执行流数：{}", 
                context.getTasksToReturn().size(), context.getExecutionsToMove().size());
    }

    @Override
    public boolean validateReturn(BpmReturnContext context) {
        // 1. 检查目标节点是否为用户任务
        if (!(context.getTargetElement() instanceof UserTask)) {
            return false;
        }

        // 2. 检查是否存在有效的退回路径
        return hasValidReturnPath(context);
    }

    @Override
    public Class<?> getSupportedNodeType() {
        return UserTask.class;
    }

    /**
     * 计算需要退回的任务
     */
    private void calculateTasksToReturn(BpmReturnContext context) {
        // 1. 获取目标节点的出口连线，计算影响范围
        List<SequenceFlow> outgoingFlows = BpmnModelUtils.getElementOutgoingFlows(context.getTargetElement());
        Set<String> affectedTaskKeys = calculateAffectedTaskKeys(context, outgoingFlows);

        // 2. 筛选需要退回的任务
        for (Task task : context.getActiveTasks()) {
            if (shouldReturnTask(task, affectedTaskKeys, context)) {
                context.addTaskToReturn(task);
            }
        }

        log.debug("[calculateTasksToReturn] 计算出需要退回的任务：{}", 
                context.getTasksToReturn().stream().map(Task::getTaskDefinitionKey).collect(Collectors.toList()));
    }

    /**
     * 计算需要移动的执行流
     */
    private void calculateExecutionsToMove(BpmReturnContext context) {
        // 获取所有需要退回任务的执行流ID
        for (Task task : context.getTasksToReturn()) {
            context.addExecutionToMove(task.getExecutionId());
        }

        // 去重处理
        context.setExecutionsToMove(context.getExecutionsToMove().stream()
                .distinct()
                .collect(Collectors.toList()));
    }

    /**
     * 处理多实例任务
     */
    private void handleMultiInstanceTasks(BpmReturnContext context) {
        UserTask targetUserTask = (UserTask) context.getTargetElement();
        
        // 检查目标节点是否为多实例任务
        if (targetUserTask.getLoopCharacteristics() != null) {
            context.setMultiInstanceReturn(true);
            context.setRequireSpecialHandling(true);
            
            log.info("[handleMultiInstanceTasks] 检测到多实例任务退回，目标节点：{}", 
                    targetUserTask.getId());
            
            // 多实例任务需要特殊处理，确保所有实例都被正确处理
            handleMultiInstanceSpecialCase(context, targetUserTask);
        }
    }

    /**
     * 分析退回路径
     */
    private void analyzeReturnPath(BpmReturnContext context) {
        // 从当前节点到目标节点的路径分析
        FlowElement current = context.getSourceElement();
        FlowElement target = context.getTargetElement();
        
        // 构建退回路径
        buildReturnPath(context, current, target);
        
        // 检查路径中是否包含网关
        checkGatewaysInPath(context);
    }

    /**
     * 计算受影响的任务Key集合
     */
    private Set<String> calculateAffectedTaskKeys(BpmReturnContext context, List<SequenceFlow> outgoingFlows) {
        return BpmnModelUtils.getReachableTaskKeys(context.getBpmnModel(), 
                context.getTargetElement(), outgoingFlows);
    }

    /**
     * 判断任务是否需要退回
     */
    private boolean shouldReturnTask(Task task, Set<String> affectedTaskKeys, BpmReturnContext context) {
        // 1. 任务必须在受影响的范围内
        if (!affectedTaskKeys.contains(task.getTaskDefinitionKey())) {
            return false;
        }

        // 2. 排除已经完成的任务
        if (task.getEndTime() != null) {
            return false;
        }

        // 3. 排除暂停的任务
        if (task.isSuspended()) {
            return false;
        }

        return true;
    }

    /**
     * 检查是否存在有效的退回路径
     */
    private boolean hasValidReturnPath(BpmReturnContext context) {
        // 使用改进的路径检查算法
        return BpmnModelUtils.isSequentialReachable(
                context.getSourceElement(), 
                context.getTargetElement(), 
                null);
    }

    /**
     * 处理多实例任务的特殊情况
     */
    private void handleMultiInstanceSpecialCase(BpmReturnContext context, UserTask targetUserTask) {
        // 对于多实例任务，需要确保：
        // 1. 所有实例都被正确退回
        // 2. 实例计数器被重置
        // 3. 完成条件被重新评估
        
        boolean isSequential = targetUserTask.getLoopCharacteristics().isSequential();
        log.debug("[handleMultiInstanceSpecialCase] 多实例任务类型：{}", 
                isSequential ? "串行" : "并行");
        
        // 根据多实例类型进行不同处理
        if (isSequential) {
            handleSequentialMultiInstance(context, targetUserTask);
        } else {
            handleParallelMultiInstance(context, targetUserTask);
        }
    }

    /**
     * 处理串行多实例任务
     */
    private void handleSequentialMultiInstance(BpmReturnContext context, UserTask targetUserTask) {
        // 串行多实例任务退回时，需要重置到第一个实例
        log.debug("[handleSequentialMultiInstance] 处理串行多实例任务退回");
        // 具体实现逻辑...
    }

    /**
     * 处理并行多实例任务
     */
    private void handleParallelMultiInstance(BpmReturnContext context, UserTask targetUserTask) {
        // 并行多实例任务退回时，需要重新创建所有实例
        log.debug("[handleParallelMultiInstance] 处理并行多实例任务退回");
        // 具体实现逻辑...
    }

    /**
     * 构建退回路径
     */
    private void buildReturnPath(BpmReturnContext context, FlowElement current, FlowElement target) {
        // 实现路径构建逻辑
        context.addReturnPathElement(current);
        context.addReturnPathElement(target);
        // 更多路径分析逻辑...
    }

    /**
     * 检查路径中的网关
     */
    private void checkGatewaysInPath(BpmReturnContext context) {
        // 检查退回路径中是否包含网关，如果有则标记需要特殊处理
        for (FlowElement element : context.getReturnPath()) {
            if (BpmnModelUtils.isGateway(element)) {
                context.addAffectedGateway(element);
                context.setRequireSpecialHandling(true);
            }
        }
    }
}
