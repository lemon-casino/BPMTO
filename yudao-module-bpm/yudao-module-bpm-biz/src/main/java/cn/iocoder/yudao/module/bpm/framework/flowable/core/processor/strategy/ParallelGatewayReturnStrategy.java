package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ParallelGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.task.api.Task;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 并行网关退回策略
 * 
 * 处理退回到并行网关节点的复杂逻辑
 * 
 * @author 芋道源码
 */
@Slf4j
public class ParallelGatewayReturnStrategy implements BpmReturnStrategy {

    @Override
    public void calculateReturnScope(BpmReturnContext context) {
        log.info("[calculateReturnScope] 开始计算并行网关退回范围，目标节点：{}", 
                context.getTargetElement().getId());

        context.setInvolveParallelGateway(true);
        context.setRequireSpecialHandling(true);

        // 1. 分析并行网关的分支结构
        analyzeParallelBranches(context);
        
        // 2. 计算需要退回的所有分支任务
        calculateAllBranchTasks(context);
        
        // 3. 处理网关聚合点
        handleGatewayJoinPoint(context);
        
        // 4. 计算执行流移动策略
        calculateParallelExecutionMove(context);

        log.info("[calculateReturnScope] 并行网关退回范围计算完成，涉及分支数：{}，任务数：{}", 
                context.getBranchesToCleanup().size(), context.getTasksToReturn().size());
    }

    @Override
    public boolean validateReturn(BpmReturnContext context) {
        // 1. 检查目标节点是否为并行网关
        if (!(context.getTargetElement() instanceof ParallelGateway)) {
            return false;
        }

        // 2. 检查并行网关的完整性
        return validateParallelGatewayIntegrity(context);
    }

    @Override
    public Class<?> getSupportedNodeType() {
        return ParallelGateway.class;
    }

    /**
     * 分析并行网关的分支结构
     */
    private void analyzeParallelBranches(BpmReturnContext context) {
        ParallelGateway targetGateway = (ParallelGateway) context.getTargetElement();
        
        // 1. 获取并行网关的所有出口分支
        List<SequenceFlow> outgoingFlows = targetGateway.getOutgoingFlows();
        log.debug("[analyzeParallelBranches] 并行网关出口分支数：{}", outgoingFlows.size());
        
        // 2. 分析每个分支的执行状态
        for (SequenceFlow flow : outgoingFlows) {
            analyzeBranchExecution(context, flow);
        }
        
        // 3. 查找对应的聚合网关
        FlowElement joinGateway = findCorrespondingJoinGateway(context, targetGateway);
        if (joinGateway != null) {
            context.addAffectedGateway(joinGateway);
            log.debug("[analyzeParallelBranches] 找到对应的聚合网关：{}", joinGateway.getId());
        }
    }

    /**
     * 计算需要退回的所有分支任务
     */
    private void calculateAllBranchTasks(BpmReturnContext context) {
        ParallelGateway targetGateway = (ParallelGateway) context.getTargetElement();
        
        // 1. 获取所有分支路径上的活动任务
        Set<String> allBranchTaskKeys = new HashSet<>();
        for (SequenceFlow flow : targetGateway.getOutgoingFlows()) {
            Set<String> branchTaskKeys = getBranchTaskKeys(context, flow);
            allBranchTaskKeys.addAll(branchTaskKeys);
            
            // 记录需要清理的分支
            context.addBranchToCleanup(flow.getId());
        }
        
        // 2. 筛选当前活动的任务
        for (Task task : context.getActiveTasks()) {
            if (allBranchTaskKeys.contains(task.getTaskDefinitionKey())) {
                context.addTaskToReturn(task);
            }
        }
        
        log.debug("[calculateAllBranchTasks] 并行分支任务Keys：{}，活动任务数：{}", 
                allBranchTaskKeys, context.getTasksToReturn().size());
    }

    /**
     * 处理网关聚合点
     */
    private void handleGatewayJoinPoint(BpmReturnContext context) {
        // 1. 查找聚合网关
        FlowElement joinGateway = findJoinGateway(context);
        if (joinGateway == null) {
            log.warn("[handleGatewayJoinPoint] 未找到对应的聚合网关");
            return;
        }
        
        // 2. 检查聚合网关后的任务
        List<SequenceFlow> joinOutgoingFlows = BpmnModelUtils.getElementOutgoingFlows(joinGateway);
        for (SequenceFlow flow : joinOutgoingFlows) {
            FlowElement targetElement = flow.getTargetFlowElement();
            if (BpmnModelUtils.isUserTask(targetElement)) {
                // 检查聚合后的任务是否需要退回
                checkPostJoinTasks(context, targetElement);
            }
        }
    }

    /**
     * 计算并行执行流移动策略
     */
    private void calculateParallelExecutionMove(BpmReturnContext context) {
        // 1. 收集所有需要移动的执行流
        Set<String> executionIds = new HashSet<>();
        
        for (Task task : context.getTasksToReturn()) {
            executionIds.add(task.getExecutionId());
        }
        
        // 2. 处理并行分支的特殊情况
        // 对于并行网关，需要确保所有分支的执行流都被正确处理
        handleParallelExecutionSpecialCase(context, executionIds);
        
        // 3. 设置执行流移动列表
        context.setExecutionsToMove(executionIds.stream().collect(Collectors.toList()));
        
        log.debug("[calculateParallelExecutionMove] 并行执行流移动数量：{}", executionIds.size());
    }

    /**
     * 分析分支执行状态
     */
    private void analyzeBranchExecution(BpmReturnContext context, SequenceFlow flow) {
        FlowElement targetElement = flow.getTargetFlowElement();
        String branchId = flow.getId();
        
        log.debug("[analyzeBranchExecution] 分析分支：{} -> {}", branchId, targetElement.getId());
        
        // 检查分支上是否有活动任务
        boolean hasActiveTasks = context.getActiveTasks().stream()
                .anyMatch(task -> isBranchTask(context, task, flow));
        
        if (hasActiveTasks) {
            context.addBranchToCleanup(branchId);
            log.debug("[analyzeBranchExecution] 分支 {} 有活动任务，需要清理", branchId);
        }
    }

    /**
     * 获取分支上的任务Keys
     */
    private Set<String> getBranchTaskKeys(BpmReturnContext context, SequenceFlow flow) {
        Set<String> taskKeys = new HashSet<>();
        FlowElement currentElement = flow.getTargetFlowElement();
        
        // 递归遍历分支路径
        traverseBranchPath(context, currentElement, taskKeys, new HashSet<>());
        
        return taskKeys;
    }

    /**
     * 递归遍历分支路径
     */
    private void traverseBranchPath(BpmReturnContext context, FlowElement element, 
                                   Set<String> taskKeys, Set<String> visited) {
        if (element == null || visited.contains(element.getId())) {
            return;
        }
        
        visited.add(element.getId());
        
        // 如果是用户任务，添加到结果集
        if (BpmnModelUtils.isUserTask(element)) {
            taskKeys.add(element.getId());
        }
        
        // 如果遇到聚合网关，停止遍历
        if (element instanceof ParallelGateway && isJoinGateway((ParallelGateway) element)) {
            return;
        }
        
        // 继续遍历后续节点
        List<SequenceFlow> outgoingFlows = BpmnModelUtils.getElementOutgoingFlows(element);
        for (SequenceFlow flow : outgoingFlows) {
            traverseBranchPath(context, flow.getTargetFlowElement(), taskKeys, visited);
        }
    }

    /**
     * 查找对应的聚合网关
     */
    private FlowElement findCorrespondingJoinGateway(BpmReturnContext context, ParallelGateway splitGateway) {
        // 实现查找逻辑，通过分析流程结构找到对应的聚合网关
        return BpmnModelUtils.findCorrespondingJoinGateway(context.getBpmnModel(), splitGateway);
    }

    /**
     * 查找聚合网关
     */
    private FlowElement findJoinGateway(BpmReturnContext context) {
        // 从受影响的网关中查找聚合网关
        return context.getAffectedGateways().stream()
                .filter(gateway -> gateway instanceof ParallelGateway)
                .filter(gateway -> isJoinGateway((ParallelGateway) gateway))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查聚合后的任务
     */
    private void checkPostJoinTasks(BpmReturnContext context, FlowElement element) {
        // 检查聚合网关后的任务是否需要一并退回
        for (Task task : context.getActiveTasks()) {
            if (task.getTaskDefinitionKey().equals(element.getId())) {
                context.addTaskToReturn(task);
                log.debug("[checkPostJoinTasks] 添加聚合后任务：{}", task.getTaskDefinitionKey());
            }
        }
    }

    /**
     * 处理并行执行流的特殊情况
     */
    private void handleParallelExecutionSpecialCase(BpmReturnContext context, Set<String> executionIds) {
        // 对于并行网关，可能需要特殊处理执行流的父子关系
        // 确保不会遗漏或重复处理执行流
        log.debug("[handleParallelExecutionSpecialCase] 处理并行执行流特殊情况，执行流数：{}", executionIds.size());
    }

    /**
     * 验证并行网关的完整性
     */
    private boolean validateParallelGatewayIntegrity(BpmReturnContext context) {
        ParallelGateway gateway = (ParallelGateway) context.getTargetElement();
        
        // 1. 检查网关是否有有效的分支
        if (CollUtil.isEmpty(gateway.getOutgoingFlows())) {
            log.warn("[validateParallelGatewayIntegrity] 并行网关没有出口分支：{}", gateway.getId());
            return false;
        }
        
        // 2. 检查是否能找到对应的聚合网关
        FlowElement joinGateway = findCorrespondingJoinGateway(context, gateway);
        if (joinGateway == null) {
            log.warn("[validateParallelGatewayIntegrity] 找不到对应的聚合网关：{}", gateway.getId());
            // 注意：某些情况下可能没有显式的聚合网关，这不一定是错误
        }
        
        return true;
    }

    /**
     * 判断任务是否属于指定分支
     */
    private boolean isBranchTask(BpmReturnContext context, Task task, SequenceFlow flow) {
        // 实现判断逻辑，检查任务是否在指定分支路径上
        return BpmnModelUtils.isTaskInBranch(context.getBpmnModel(), task.getTaskDefinitionKey(), flow);
    }

    /**
     * 判断是否为聚合网关
     */
    private boolean isJoinGateway(ParallelGateway gateway) {
        // 聚合网关通常有多个入口分支和一个出口分支
        return gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1;
    }
}
