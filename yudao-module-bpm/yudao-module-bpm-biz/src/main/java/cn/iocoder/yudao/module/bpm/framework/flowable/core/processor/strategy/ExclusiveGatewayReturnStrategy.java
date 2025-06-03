package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.task.api.Task;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排他网关退回策略
 * 
 * 处理退回到排他网关节点的逻辑
 * 排他网关只允许一个分支执行，基于条件选择
 * 
 * @author 芋道源码
 */
@Slf4j
public class ExclusiveGatewayReturnStrategy implements BpmReturnStrategy {

    @Override
    public void calculateReturnScope(BpmReturnContext context) {
        log.info("[calculateReturnScope] 开始计算排他网关退回范围，目标节点：{}", 
                context.getTargetElement().getId());

        // 1. 分析排他网关的条件分支
        analyzeExclusiveBranches(context);
        
        // 2. 计算当前执行分支上的任务
        calculateCurrentBranchTasks(context);
        
        // 3. 处理条件重新评估
        handleConditionReevaluation(context);
        
        // 4. 计算执行流移动策略
        calculateExclusiveExecutionMove(context);

        log.info("[calculateReturnScope] 排他网关退回范围计算完成，当前分支：{}，任务数：{}", 
                context.getBranchesToCleanup(), context.getTasksToReturn().size());
    }

    @Override
    public boolean validateReturn(BpmReturnContext context) {
        // 1. 检查目标节点是否为排他网关
        if (!(context.getTargetElement() instanceof ExclusiveGateway)) {
            return false;
        }

        // 2. 检查排他网关的条件完整性
        return validateExclusiveGatewayConditions(context);
    }

    @Override
    public Class<?> getSupportedNodeType() {
        return ExclusiveGateway.class;
    }

    /**
     * 分析排他网关的条件分支
     */
    private void analyzeExclusiveBranches(BpmReturnContext context) {
        ExclusiveGateway targetGateway = (ExclusiveGateway) context.getTargetElement();
        
        // 1. 获取排他网关的所有出口分支
        List<SequenceFlow> outgoingFlows = targetGateway.getOutgoingFlows();
        log.debug("[analyzeExclusiveBranches] 排他网关出口分支数：{}", outgoingFlows.size());
        
        // 2. 识别当前执行的分支
        SequenceFlow currentBranch = identifyCurrentExecutingBranch(context, outgoingFlows);
        if (currentBranch != null) {
            log.debug("[analyzeExclusiveBranches] 当前执行分支：{}", currentBranch.getId());
            context.addBranchToCleanup(currentBranch.getId());
        }
        
        // 3. 分析分支条件
        for (SequenceFlow flow : outgoingFlows) {
            analyzeBranchCondition(context, flow, targetGateway);
        }
    }

    /**
     * 计算当前执行分支上的任务
     */
    private void calculateCurrentBranchTasks(BpmReturnContext context) {
        ExclusiveGateway targetGateway = (ExclusiveGateway) context.getTargetElement();
        
        // 1. 获取当前执行分支上的所有任务
        Set<String> currentBranchTaskKeys = new HashSet<>();
        
        for (String branchId : context.getBranchesToCleanup()) {
            SequenceFlow branch = findSequenceFlowById(targetGateway, branchId);
            if (branch != null) {
                Set<String> branchTaskKeys = getBranchTaskKeys(context, branch);
                currentBranchTaskKeys.addAll(branchTaskKeys);
                
                log.debug("[calculateCurrentBranchTasks] 分支 {} 任务数：{}", 
                        branchId, branchTaskKeys.size());
            }
        }
        
        // 2. 筛选当前活动的任务
        for (Task task : context.getActiveTasks()) {
            if (currentBranchTaskKeys.contains(task.getTaskDefinitionKey())) {
                context.addTaskToReturn(task);
            }
        }
        
        log.debug("[calculateCurrentBranchTasks] 排他网关当前分支任务Keys：{}，活动任务数：{}", 
                currentBranchTaskKeys, context.getTasksToReturn().size());
    }

    /**
     * 处理条件重新评估
     */
    private void handleConditionReevaluation(BpmReturnContext context) {
        ExclusiveGateway targetGateway = (ExclusiveGateway) context.getTargetElement();
        
        // 1. 重新评估分支条件
        // 退回到排他网关时，需要重新评估哪个分支应该被选择
        SequenceFlow selectedBranch = reevaluateBranchSelection(context, targetGateway);
        
        if (selectedBranch != null) {
            log.debug("[handleConditionReevaluation] 重新评估后选择分支：{}", selectedBranch.getId());
            // 更新需要清理的分支
            context.getBranchesToCleanup().clear();
            context.addBranchToCleanup(selectedBranch.getId());
        }
        
        // 2. 处理默认分支
        handleDefaultBranch(context, targetGateway);
    }

    /**
     * 计算排他网关执行流移动策略
     */
    private void calculateExclusiveExecutionMove(BpmReturnContext context) {
        // 1. 收集所有需要移动的执行流
        Set<String> executionIds = new HashSet<>();
        
        for (Task task : context.getTasksToReturn()) {
            executionIds.add(task.getExecutionId());
        }
        
        // 2. 排他网关通常只有一个活动分支，处理相对简单
        handleExclusiveExecutionSpecialCase(context, executionIds);
        
        // 3. 设置执行流移动列表
        context.setExecutionsToMove(executionIds.stream().collect(Collectors.toList()));
        
        log.debug("[calculateExclusiveExecutionMove] 排他网关执行流移动数量：{}", executionIds.size());
    }

    /**
     * 识别当前执行的分支
     */
    private SequenceFlow identifyCurrentExecutingBranch(BpmReturnContext context, List<SequenceFlow> outgoingFlows) {
        // 通过检查哪个分支上有活动任务来识别当前执行的分支
        for (SequenceFlow flow : outgoingFlows) {
            boolean hasActiveTasks = context.getActiveTasks().stream()
                    .anyMatch(task -> isBranchTask(context, task, flow));
            
            if (hasActiveTasks) {
                return flow;
            }
        }
        
        return null;
    }

    /**
     * 分析分支条件
     */
    private void analyzeBranchCondition(BpmReturnContext context, SequenceFlow flow, ExclusiveGateway gateway) {
        String branchId = flow.getId();
        String condition = flow.getConditionExpression();
        boolean isDefaultBranch = branchId.equals(gateway.getDefaultFlow());
        
        log.debug("[analyzeBranchCondition] 分析分支：{}，条件：{}，是否默认：{}", 
                branchId, condition, isDefaultBranch);
        
        // 记录分支信息用于后续处理
        if (isDefaultBranch) {
            log.debug("[analyzeBranchCondition] 发现默认分支：{}", branchId);
        }
    }

    /**
     * 重新评估分支选择
     */
    private SequenceFlow reevaluateBranchSelection(BpmReturnContext context, ExclusiveGateway gateway) {
        // 1. 按顺序评估每个分支的条件
        for (SequenceFlow flow : gateway.getOutgoingFlows()) {
            // 跳过默认分支，最后处理
            if (flow.getId().equals(gateway.getDefaultFlow())) {
                continue;
            }
            
            String condition = flow.getConditionExpression();
            if (condition != null && !condition.trim().isEmpty()) {
                boolean conditionResult = evaluateCondition(context, condition);
                if (conditionResult) {
                    log.debug("[reevaluateBranchSelection] 分支 {} 条件满足，选择此分支", flow.getId());
                    return flow;
                }
            } else {
                // 无条件分支，直接选择
                log.debug("[reevaluateBranchSelection] 分支 {} 无条件，选择此分支", flow.getId());
                return flow;
            }
        }
        
        // 2. 如果没有分支条件满足，选择默认分支
        String defaultFlow = gateway.getDefaultFlow();
        if (defaultFlow != null) {
            SequenceFlow defaultBranch = findSequenceFlowById(gateway, defaultFlow);
            if (defaultBranch != null) {
                log.debug("[reevaluateBranchSelection] 选择默认分支：{}", defaultFlow);
                return defaultBranch;
            }
        }
        
        // 3. 如果没有默认分支，选择第一个分支
        if (!gateway.getOutgoingFlows().isEmpty()) {
            SequenceFlow firstBranch = gateway.getOutgoingFlows().get(0);
            log.debug("[reevaluateBranchSelection] 选择第一个分支：{}", firstBranch.getId());
            return firstBranch;
        }
        
        return null;
    }

    /**
     * 处理默认分支
     */
    private void handleDefaultBranch(BpmReturnContext context, ExclusiveGateway gateway) {
        String defaultFlow = gateway.getDefaultFlow();
        if (defaultFlow != null) {
            log.debug("[handleDefaultBranch] 处理默认分支：{}", defaultFlow);
            // 默认分支的特殊处理逻辑
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
        
        // 如果遇到网关，根据类型决定是否继续遍历
        if (BpmnModelUtils.isGateway(element)) {
            // 对于排他网关，通常会遇到聚合点
            if (element instanceof ExclusiveGateway && isJoinGateway((ExclusiveGateway) element)) {
                return;
            }
        }
        
        // 继续遍历后续节点
        List<SequenceFlow> outgoingFlows = BpmnModelUtils.getElementOutgoingFlows(element);
        for (SequenceFlow flow : outgoingFlows) {
            traverseBranchPath(context, flow.getTargetFlowElement(), taskKeys, visited);
        }
    }

    /**
     * 处理排他网关执行流的特殊情况
     */
    private void handleExclusiveExecutionSpecialCase(BpmReturnContext context, Set<String> executionIds) {
        // 排他网关的执行流处理相对简单，因为只有一个分支活动
        log.debug("[handleExclusiveExecutionSpecialCase] 处理排他网关执行流特殊情况，执行流数：{}", executionIds.size());
    }

    /**
     * 验证排他网关的条件完整性
     */
    private boolean validateExclusiveGatewayConditions(BpmReturnContext context) {
        ExclusiveGateway gateway = (ExclusiveGateway) context.getTargetElement();
        
        // 1. 检查网关是否有有效的分支
        if (CollUtil.isEmpty(gateway.getOutgoingFlows())) {
            log.warn("[validateExclusiveGatewayConditions] 排他网关没有出口分支：{}", gateway.getId());
            return false;
        }
        
        // 2. 检查是否有默认分支
        if (gateway.getDefaultFlow() == null) {
            // 检查是否至少有一个无条件分支
            boolean hasUnconditionalBranch = gateway.getOutgoingFlows().stream()
                    .anyMatch(flow -> flow.getConditionExpression() == null || 
                            flow.getConditionExpression().trim().isEmpty());
            
            if (!hasUnconditionalBranch) {
                log.warn("[validateExclusiveGatewayConditions] 排他网关没有默认分支或无条件分支：{}", gateway.getId());
            }
        }
        
        return true;
    }

    /**
     * 根据ID查找序列流
     */
    private SequenceFlow findSequenceFlowById(ExclusiveGateway gateway, String flowId) {
        return gateway.getOutgoingFlows().stream()
                .filter(flow -> flow.getId().equals(flowId))
                .findFirst()
                .orElse(null);
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
    private boolean isJoinGateway(ExclusiveGateway gateway) {
        // 聚合网关通常有多个入口分支和一个出口分支
        return gateway.getIncomingFlows().size() > 1 && gateway.getOutgoingFlows().size() == 1;
    }
}
