package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context;

import cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl;
import lombok.Builder;
import lombok.Data;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BPM 任务退回上下文
 * 
 * 包含退回操作所需的所有上下文信息
 * 
 * @author 芋道源码
 */
@Data
@Builder
public class BpmReturnContext {

    // ========== 基础信息 ==========
    
    /**
     * 操作用户ID
     */
    private Long userId;
    
    /**
     * 当前任务
     */
    private Task currentTask;
    
    /**
     * 目标任务定义Key
     */
    private String targetTaskDefinitionKey;
    
    /**
     * 退回原因
     */
    private String reason;

    // ========== 流程模型信息 ==========
    
    /**
     * BPMN模型
     */
    private BpmnModel bpmnModel;
    
    /**
     * 源节点元素
     */
    private FlowElement sourceElement;
    
    /**
     * 目标节点元素
     */
    private FlowElement targetElement;

    // ========== 任务和执行流信息 ==========
    
    /**
     * 流程实例中的所有活动任务
     */
    private List<Task> activeTasks;
    
    /**
     * 需要退回的任务列表
     */
    @Builder.Default
    private List<Task> tasksToReturn = new ArrayList<>();
    
    /**
     * 需要移动的执行流ID列表
     */
    @Builder.Default
    private List<String> executionsToMove = new ArrayList<>();
    
    /**
     * 需要取消的任务列表
     */
    @Builder.Default
    private List<Task> tasksToCancel = new ArrayList<>();

    // ========== 路径分析信息 ==========
    
    /**
     * 退回路径上的所有节点
     */
    @Builder.Default
    private List<FlowElement> returnPath = new ArrayList<>();
    
    /**
     * 受影响的网关节点
     */
    @Builder.Default
    private Set<FlowElement> affectedGateways = new HashSet<>();
    
    /**
     * 需要清理的分支路径
     */
    @Builder.Default
    private Set<String> branchesToCleanup = new HashSet<>();

    // ========== 服务依赖 ==========
    
    /**
     * 任务服务
     */
    private TaskService taskService;
    
    /**
     * 运行时服务
     */
    private RuntimeService runtimeService;
    
    /**
     * BPM任务服务
     */
    private BpmTaskServiceImpl bpmTaskService;

    // ========== 状态标记 ==========
    
    /**
     * 是否为多实例任务退回
     */
    @Builder.Default
    private boolean multiInstanceReturn = false;
    
    /**
     * 是否涉及并行网关
     */
    @Builder.Default
    private boolean involveParallelGateway = false;
    
    /**
     * 是否涉及包容网关
     */
    @Builder.Default
    private boolean involveInclusiveGateway = false;
    
    /**
     * 是否需要特殊处理
     */
    @Builder.Default
    private boolean requireSpecialHandling = false;

    // ========== 辅助方法 ==========
    
    /**
     * 添加需要退回的任务
     */
    public void addTaskToReturn(Task task) {
        if (task != null && !tasksToReturn.contains(task)) {
            tasksToReturn.add(task);
        }
    }
    
    /**
     * 添加需要移动的执行流
     */
    public void addExecutionToMove(String executionId) {
        if (executionId != null && !executionsToMove.contains(executionId)) {
            executionsToMove.add(executionId);
        }
    }
    
    /**
     * 添加需要取消的任务
     */
    public void addTaskToCancel(Task task) {
        if (task != null && !tasksToCancel.contains(task)) {
            tasksToCancel.add(task);
        }
    }
    
    /**
     * 添加退回路径节点
     */
    public void addReturnPathElement(FlowElement element) {
        if (element != null && !returnPath.contains(element)) {
            returnPath.add(element);
        }
    }
    
    /**
     * 添加受影响的网关
     */
    public void addAffectedGateway(FlowElement gateway) {
        if (gateway != null) {
            affectedGateways.add(gateway);
        }
    }
    
    /**
     * 添加需要清理的分支
     */
    public void addBranchToCleanup(String branchId) {
        if (branchId != null) {
            branchesToCleanup.add(branchId);
        }
    }
    
    /**
     * 获取流程实例ID
     */
    public String getProcessInstanceId() {
        return currentTask != null ? currentTask.getProcessInstanceId() : null;
    }
    
    /**
     * 获取流程定义ID
     */
    public String getProcessDefinitionId() {
        return currentTask != null ? currentTask.getProcessDefinitionId() : null;
    }
    
    /**
     * 检查是否有任务需要处理
     */
    public boolean hasTasksToProcess() {
        return !tasksToReturn.isEmpty() || !tasksToCancel.isEmpty();
    }
    
    /**
     * 检查是否有执行流需要移动
     */
    public boolean hasExecutionsToMove() {
        return !executionsToMove.isEmpty();
    }
    
    /**
     * 获取所有需要处理的任务
     */
    public List<Task> getAllTasksToProcess() {
        List<Task> allTasks = new ArrayList<>(tasksToReturn);
        allTasks.addAll(tasksToCancel);
        return allTasks;
    }
    
    /**
     * 重置计算结果
     */
    public void resetCalculationResults() {
        tasksToReturn.clear();
        executionsToMove.clear();
        tasksToCancel.clear();
        returnPath.clear();
        affectedGateways.clear();
        branchesToCleanup.clear();
        multiInstanceReturn = false;
        involveParallelGateway = false;
        involveInclusiveGateway = false;
        requireSpecialHandling = false;
    }
    
    /**
     * 获取上下文摘要信息（用于日志）
     */
    public String getSummary() {
        return String.format("ReturnContext[processInstance=%s, source=%s, target=%s, tasksToReturn=%d, executionsToMove=%d]",
                getProcessInstanceId(),
                sourceElement != null ? sourceElement.getId() : "null",
                targetElement != null ? targetElement.getId() : "null",
                tasksToReturn.size(),
                executionsToMove.size());
    }
}
