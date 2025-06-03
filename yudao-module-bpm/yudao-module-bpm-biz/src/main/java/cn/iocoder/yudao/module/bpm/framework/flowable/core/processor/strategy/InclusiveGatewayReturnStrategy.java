package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.task.api.Task;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 包容网关退回策略
 * 
 * 处理退回到包容网关节点的逻辑
 * 包容网关允许多个分支同时执行，但不要求所有分支都执行
 * 
 * @author 芋道源码
 */
@Slf4j
public class InclusiveGatewayReturnStrategy implements BpmReturnStrategy {

    @Override
    public void calculateReturnScope(BpmReturnContext context) {
        log.info("[calculateReturnScope] 开始计算包容网关退回范围，目标节点：{}", 
                context.getTargetElement().getId());

        context.setInvolveInclusiveGateway(true);
        context.setRequireSpecialHandling(true);

        // 1. 分析包容网关的条件分支
        analyzeInclusiveBranches(context);
        
        // 2. 计算活动分支上的任务
        calculateActiveBranchTasks(context);
        
        // 3. 处理条件评估和分支选择
        handleConditionEvaluation(context);
        
        // 4. 计算执行流移动策略
        calculateInclusiveExecutionMove(context);

        log.info("[calculateReturnScope] 包容网关退回范围计算完成，活动分支数：{}，任务数：{}", 
                context.getBranchesToCleanup().size(), context.getTasksToReturn().size());
    }

    @Override
    public boolean validateReturn(BpmReturnContext context) {
        // 1. 检查目标节点是否为包容网关
        if (!(context.getTargetElement() instanceof InclusiveGateway)) {
            return false;
        }

        // 2. 检查包容网关的条件完整性
        return validateInclusiveGatewayConditions(context);
    }

    @Override
    public Class<?> getSupportedNodeType() {
        return InclusiveGateway.class;
    }

    /**
     * 分析包容网关的条件分支
     */
    private void analyzeInclusiveBranches(BpmReturnContext context) {
        InclusiveGateway targetGateway = (InclusiveGateway) context.getTargetElement();
        
        // 1. 获取包容网关的所有出口分支
        List<SequenceFlow> outgoingFlows = targetGateway.getOutgoingFlows();
        log.debug("[analyzeInclusiveBranches] 包容网关出口分支数：{}", outgoingFlows.size());
        
        // 2. 分析每个分支的条件和执行状态
        for (SequenceFlow flow : outgoingFlows) {
            analyzeBranchConditionAndExecution(context, flow, targetGateway);
        }
        
        // 3. 查找对应的聚合网关
        FlowElement joinGateway = findCorrespondingJoinGateway(context, targetGateway);
        if (joinGateway != null) {
            context.addAffectedGateway(joinGateway);
            log.debug("[analyzeInclusiveBranches] 找到对应的聚合网关：{}", joinGateway.getId());
        }
    }

    /**
     * 计算活动分支上的任务
     */
    private void calculateActiveBranchTasks(BpmReturnContext context) {
        InclusiveGateway targetGateway = (InclusiveGateway) context.getTargetElement();
        
        // 1. 识别当前活动的分支
        Set<String> activeBranchTaskKeys = new HashSet<>();
        
        for (SequenceFlow flow : targetGateway.getOutgoingFlows()) {
            if (isBranchCurrentlyActive(context, flow)) {
                Set<String> branchTaskKeys = getBranchTaskKeys(context, flow);
                activeBranchTaskKeys.addAll(branchTaskKeys);
                context.addBranchToCleanup(flow.getId());
                
                log.debug("[calculateActiveBranchTasks] 活动分支：{}，任务数：{}", 
                        flow.getId(), branchTaskKeys.size());
            }
        }
        
        // 2. 筛选当前活动的任务
        for (Task task : context.getActiveTasks()) {
            if (activeBranchTaskKeys.contains(task.getTaskDefinitionKey())) {
                context.addTaskToReturn(task);
            }
        }
        
        log.debug("[calculateActiveBranchTasks] 包容网关活动分支任务Keys：{}，活动任务数：{}", 
                activeBranchTaskKeys, context.getTasksToReturn().size());
    }

    /**
     * 处理条件评估和分支选择
     */
    private void handleConditionEvaluation(BpmReturnContext context) {
        InclusiveGateway targetGateway = (InclusiveGateway) context.getTargetElement();
        
        // 1. 重新评估分支条件
        // 退回到包容网关时，需要重新评估哪些分支应该被激活
        evaluateBranchConditions(context, targetGateway);
        
        // 2. 处理默认分支
        handleDefaultBranch(context, targetGateway);
        
        // 3. 确保至少有一个分支被激活
        ensureAtLeastOneBranchActive(context, targetGateway);
    }

    /**
     * 计算包容网关执行流移动策略
     */
    private void calculateInclusiveExecutionMove(BpmReturnContext context) {
        // 1. 收集所有需要移动的执行流
        Set<String> executionIds = new HashSet<>();
        
        for (Task task : context.getTasksToReturn()) {
            executionIds.add(task.getExecutionId());
        }
        
        // 2. 处理包容网关的特殊情况
        // 包容网关可能有多个活动分支，需要特殊处理
        handleInclusiveExecutionSpecialCase(context, executionIds);
        
        // 3. 设置执行流移动列表
        context.setExecutionsToMove(executionIds.stream().collect(Collectors.toList()));
        
        log.debug("[calculateInclusiveExecutionMove] 包容网关执行流移动数量：{}", executionIds.size());
    }

    /**
     * 分析分支条件和执行状态
     */
    private void analyzeBranchConditionAndExecution(BpmReturnContext context, SequenceFlow flow, 
                                                   InclusiveGateway gateway) {
        String branchId = flow.getId();
        String condition = flow.getConditionExpression();
        boolean isDefaultBranch = branchId.equals(gateway.getDefaultFlow());
        
        log.debug("[analyzeBranchConditionAndExecution] 分析分支：{}，条件：{}，是否默认：{}", 
                branchId, condition, isDefaultBranch);
        
        // 检查分支上是否有活动任务
        boolean hasActiveTasks = context.getActiveTasks().stream()
                .anyMatch(task -> isBranchTask(context, task, flow));
        
        if (hasActiveTasks) {
            context.addBranchToCleanup(branchId);
            log.debug("[analyzeBranchConditionAndExecution] 分支 {} 有活动任务，需要清理", branchId);
        }
    }

    /**
     * 判断分支是否当前活动
     */
    private boolean isBranchCurrentlyActive(BpmReturnContext context, SequenceFlow flow) {
        // 检查分支路径上是否有活动任务
        return context.getActiveTasks().stream()
                .anyMatch(task -> isBranchTask(context, task, flow));
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
        if (element instanceof InclusiveGateway && isJoinGateway((InclusiveGateway) element)) {
            return;
        }
        
        // 继续遍历后续节点
        List<SequenceFlow> outgoingFlows = BpmnModelUtils.getElementOutgoingFlows(element);
        for (SequenceFlow flow : outgoingFlows) {
            traverseBranchPath(context, flow.getTargetFlowElement(), taskKeys, visited);
        }
    }

    /**
     * 评估分支条件
     */
    private void evaluateBranchConditions(BpmReturnContext context, InclusiveGateway gateway) {
        // 获取流程变量用于条件评估
        // 注意：退回时可能需要重新评估条件，因为流程变量可能已经改变
        log.debug("[evaluateBranchConditions] 重新评估包容网关分支条件");
        
        for (SequenceFlow flow : gateway.getOutgoingFlows()) {
            String condition = flow.getConditionExpression();
            if (condition != null && !condition.trim().isEmpty()) {
                // 这里应该使用流程引擎的条件评估器
                boolean conditionResult = evaluateCondition(context, condition);
                log.debug("[evaluateBranchConditions] 分支 {} 条件评估结果：{}", flow.getId(), conditionResult);
            }
        }
    }

    /**
     * 处理默认分支
     */
    private void handleDefaultBranch(BpmReturnContext context, InclusiveGateway gateway) {
        String defaultFlow = gateway.getDefaultFlow();
        if (defaultFlow != null) {
            log.debug("[handleDefaultBranch] 处理默认分支：{}", defaultFlow);
            // 默认分支的处理逻辑
        }
    }

    /**
     * 确保至少有一个分支被激活
     */
    private void ensureAtLeastOneBranchActive(BpmReturnContext context, InclusiveGateway gateway) {
        // 包容网关必须至少激活一个分支
        if (context.getBranchesToCleanup().isEmpty()) {
            // 如果没有活动分支，激活默认分支
            String defaultFlow = gateway.getDefaultFlow();
            if (defaultFlow != null) {
                context.addBranchToCleanup(defaultFlow);
                log.debug("[ensureAtLeastOneBranchActive] 激活默认分支：{}", defaultFlow);
            } else if (!gateway.getOutgoingFlows().isEmpty()) {
                // 如果没有默认分支，激活第一个分支
                String firstFlow = gateway.getOutgoingFlows().get(0).getId();
                context.addBranchToCleanup(firstFlow);
                log.debug("[ensureAtLeastOneBranchActive] 激活第一个分支：{}", firstFlow);
            }
        }
    }

    /**
     * 查找对应的聚合网关
     */
    private FlowElement findCorrespondingJoinGateway(BpmReturnContext context, InclusiveGateway splitGateway) {
        // 实现查找逻辑，通过分析流程结构找到对应的聚合网关
        return BpmnModelUtils.findCorrespondingJoinGateway(context.getBpmnModel(), splitGateway);
    }

    /**
     * 处理包容网关执行流的特殊情况
     */
    private void handleInclusiveExecutionSpecialCase(BpmReturnContext context, Set<String> executionIds) {
        // 包容网关的执行流处理可能比并行网关更复杂，因为分支的激活是有条件的
        log.debug("[handleInclusiveExecutionSpecialCase] 处理包容网关执行流特殊情况，执行流数：{}", executionIds.size());
    }

    /**
     * 验证包容网关的条件完整性
     */
    private boolean validateInclusiveGatewayConditions(BpmReturnContext context) {
        InclusiveGateway gateway = (InclusiveGateway) context.getTargetElement();
        
        // 1. 检查网关是否有有效的分支
        if (CollUtil.isEmpty(gateway.getOutgoingFlows())) {
            log.warn("[validateInclusiveGatewayConditions] 包容网关没有出口分支：{}", gateway.getId());
            return false;
        }
        
        // 2. 检查是否有默认分支或至少一个无条件分支
        boolean hasDefaultOrUnconditional = gateway.getDefaultFlow() != null ||
                gateway.getOutgoingFlows().stream()
                        .anyMatch(flow -> flow.getConditionExpression() == null || 
                                flow.getConditionExpression().trim().isEmpty());
        
        if (!hasDefaultOrUnconditional) {
            log.warn("[validateInclusiveGatewayConditions] 包容网关没有默认分支或无条件分支：{}", gateway.getId());
        }
        
        return true;
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateCondition(BpmReturnContext context, String condition) {
        // 这里应该使用Flowable的条件评估器
        // 暂时返回true，实际实现需要根据流程变量评估条件
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
    private boolean isJoinGateway(InclusiveGateway gateway) {
        // 聚合网关通常有多个入口分支和一个出口分支
        return gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1;
    }
}
