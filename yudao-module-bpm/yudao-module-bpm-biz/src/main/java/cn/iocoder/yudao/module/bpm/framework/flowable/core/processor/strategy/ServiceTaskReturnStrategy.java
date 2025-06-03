package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy;

import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.ServiceTask;
import org.flowable.task.api.Task;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 服务任务退回策略
 * 
 * 处理退回到服务任务节点的逻辑
 * 服务任务通常是自动执行的，退回时需要特殊处理
 * 
 * @author 芋道源码
 */
@Slf4j
public class ServiceTaskReturnStrategy implements BpmReturnStrategy {

    @Override
    public void calculateReturnScope(BpmReturnContext context) {
        log.info("[calculateReturnScope] 开始计算服务任务退回范围，目标节点：{}", 
                context.getTargetElement().getId());

        // 1. 分析服务任务的特性
        analyzeServiceTaskCharacteristics(context);
        
        // 2. 计算需要退回的任务范围
        calculateTasksToReturn(context);
        
        // 3. 处理服务任务的特殊情况
        handleServiceTaskSpecialCases(context);
        
        // 4. 计算执行流移动策略
        calculateServiceTaskExecutionMove(context);

        log.info("[calculateReturnScope] 服务任务退回范围计算完成，任务数：{}", 
                context.getTasksToReturn().size());
    }

    @Override
    public boolean validateReturn(BpmReturnContext context) {
        // 1. 检查目标节点是否为服务任务
        if (!(context.getTargetElement() instanceof ServiceTask)) {
            return false;
        }

        // 2. 检查服务任务是否支持退回
        return validateServiceTaskReturnability(context);
    }

    @Override
    public Class<?> getSupportedNodeType() {
        return ServiceTask.class;
    }

    /**
     * 分析服务任务的特性
     */
    private void analyzeServiceTaskCharacteristics(BpmReturnContext context) {
        ServiceTask targetServiceTask = (ServiceTask) context.getTargetElement();
        
        log.debug("[analyzeServiceTaskCharacteristics] 分析服务任务：{}", targetServiceTask.getId());
        
        // 1. 检查服务任务的实现类型
        String implementation = targetServiceTask.getImplementation();
        String implementationType = targetServiceTask.getImplementationType();
        
        log.debug("[analyzeServiceTaskCharacteristics] 实现类型：{}，实现：{}", implementationType, implementation);
        
        // 2. 检查是否为异步任务
        boolean isAsync = targetServiceTask.isAsynchronous();
        if (isAsync) {
            context.setRequireSpecialHandling(true);
            log.debug("[analyzeServiceTaskCharacteristics] 检测到异步服务任务，需要特殊处理");
        }
        
        // 3. 检查是否有失败重试配置
        boolean hasFailureHandling = hasFailureHandling(targetServiceTask);
        if (hasFailureHandling) {
            log.debug("[analyzeServiceTaskCharacteristics] 服务任务配置了失败处理");
        }
    }

    /**
     * 计算需要退回的任务
     */
    private void calculateTasksToReturn(BpmReturnContext context) {
        // 1. 服务任务通常是自动执行的，不会有对应的用户任务
        // 但是需要考虑服务任务之后的用户任务
        Set<String> affectedTaskKeys = calculateAffectedTaskKeys(context);
        
        // 2. 筛选需要退回的任务
        for (Task task : context.getActiveTasks()) {
            if (shouldReturnTask(task, affectedTaskKeys, context)) {
                context.addTaskToReturn(task);
            }
        }
        
        log.debug("[calculateTasksToReturn] 服务任务影响的任务Keys：{}，需要退回的任务数：{}", 
                affectedTaskKeys, context.getTasksToReturn().size());
    }

    /**
     * 处理服务任务的特殊情况
     */
    private void handleServiceTaskSpecialCases(BpmReturnContext context) {
        ServiceTask targetServiceTask = (ServiceTask) context.getTargetElement();
        
        // 1. 处理异步服务任务
        if (targetServiceTask.isAsynchronous()) {
            handleAsyncServiceTask(context, targetServiceTask);
        }
        
        // 2. 处理有状态的服务任务
        if (isStatefulServiceTask(targetServiceTask)) {
            handleStatefulServiceTask(context, targetServiceTask);
        }
        
        // 3. 处理外部系统调用
        if (isExternalSystemCall(targetServiceTask)) {
            handleExternalSystemCall(context, targetServiceTask);
        }
    }

    /**
     * 计算服务任务执行流移动策略
     */
    private void calculateServiceTaskExecutionMove(BpmReturnContext context) {
        // 1. 收集所有需要移动的执行流
        Set<String> executionIds = new HashSet<>();
        
        for (Task task : context.getTasksToReturn()) {
            executionIds.add(task.getExecutionId());
        }
        
        // 2. 服务任务的特殊处理
        handleServiceTaskExecutionSpecialCase(context, executionIds);
        
        // 3. 设置执行流移动列表
        context.setExecutionsToMove(executionIds.stream().collect(Collectors.toList()));
        
        log.debug("[calculateServiceTaskExecutionMove] 服务任务执行流移动数量：{}", executionIds.size());
    }

    /**
     * 计算受影响的任务Keys
     */
    private Set<String> calculateAffectedTaskKeys(BpmReturnContext context) {
        Set<String> taskKeys = new HashSet<>();
        
        // 从服务任务开始，向前查找所有可能受影响的任务
        FlowElement currentElement = context.getTargetElement();
        traverseForwardPath(context, currentElement, taskKeys, new HashSet<>());
        
        return taskKeys;
    }

    /**
     * 向前遍历路径
     */
    private void traverseForwardPath(BpmReturnContext context, FlowElement element, 
                                    Set<String> taskKeys, Set<String> visited) {
        if (element == null || visited.contains(element.getId())) {
            return;
        }
        
        visited.add(element.getId());
        
        // 如果是用户任务，添加到结果集
        if (BpmnModelUtils.isUserTask(element)) {
            taskKeys.add(element.getId());
        }
        
        // 继续遍历后续节点
        BpmnModelUtils.getElementOutgoingFlows(element).forEach(flow -> 
                traverseForwardPath(context, flow.getTargetFlowElement(), taskKeys, visited));
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
     * 处理异步服务任务
     */
    private void handleAsyncServiceTask(BpmReturnContext context, ServiceTask serviceTask) {
        log.debug("[handleAsyncServiceTask] 处理异步服务任务：{}", serviceTask.getId());
        
        // 异步服务任务可能正在执行中，需要考虑取消正在执行的作业
        // 这里可能需要查询作业表，取消相关的异步作业
        context.setRequireSpecialHandling(true);
    }

    /**
     * 处理有状态的服务任务
     */
    private void handleStatefulServiceTask(BpmReturnContext context, ServiceTask serviceTask) {
        log.debug("[handleStatefulServiceTask] 处理有状态的服务任务：{}", serviceTask.getId());
        
        // 有状态的服务任务可能需要回滚已经执行的操作
        // 这需要根据具体的业务逻辑来处理
        context.setRequireSpecialHandling(true);
    }

    /**
     * 处理外部系统调用
     */
    private void handleExternalSystemCall(BpmReturnContext context, ServiceTask serviceTask) {
        log.debug("[handleExternalSystemCall] 处理外部系统调用：{}", serviceTask.getId());
        
        // 外部系统调用可能需要发送补偿请求
        // 这需要根据具体的集成方式来处理
        context.setRequireSpecialHandling(true);
    }

    /**
     * 处理服务任务执行流的特殊情况
     */
    private void handleServiceTaskExecutionSpecialCase(BpmReturnContext context, Set<String> executionIds) {
        log.debug("[handleServiceTaskExecutionSpecialCase] 处理服务任务执行流特殊情况，执行流数：{}", executionIds.size());
        
        // 服务任务的执行流处理可能需要考虑：
        // 1. 取消正在执行的异步作业
        // 2. 清理服务任务产生的中间状态
        // 3. 回滚外部系统的操作
    }

    /**
     * 验证服务任务是否支持退回
     */
    private boolean validateServiceTaskReturnability(BpmReturnContext context) {
        ServiceTask serviceTask = (ServiceTask) context.getTargetElement();
        
        // 1. 检查服务任务的类型
        String implementationType = serviceTask.getImplementationType();
        if ("webService".equals(implementationType)) {
            log.warn("[validateServiceTaskReturnability] Web服务调用可能不支持退回：{}", serviceTask.getId());
            // 根据业务需求决定是否允许退回
        }
        
        // 2. 检查是否有补偿处理器
        boolean hasCompensationHandler = hasCompensationHandler(serviceTask);
        if (!hasCompensationHandler && isExternalSystemCall(serviceTask)) {
            log.warn("[validateServiceTaskReturnability] 外部系统调用没有补偿处理器：{}", serviceTask.getId());
        }
        
        return true; // 默认允许退回，具体限制根据业务需求调整
    }

    /**
     * 检查是否有失败处理配置
     */
    private boolean hasFailureHandling(ServiceTask serviceTask) {
        // 检查是否配置了失败重试、错误事件等
        return serviceTask.getFailedJobRetryTimeCycle() != null ||
               !serviceTask.getBoundaryEvents().isEmpty();
    }

    /**
     * 检查是否为有状态的服务任务
     */
    private boolean isStatefulServiceTask(ServiceTask serviceTask) {
        // 根据服务任务的实现类型或配置判断是否有状态
        // 这里可以根据具体的业务逻辑来判断
        String implementation = serviceTask.getImplementation();
        return implementation != null && implementation.contains("stateful");
    }

    /**
     * 检查是否为外部系统调用
     */
    private boolean isExternalSystemCall(ServiceTask serviceTask) {
        // 根据服务任务的实现类型判断是否为外部系统调用
        String implementationType = serviceTask.getImplementationType();
        return "webService".equals(implementationType) ||
               "external".equals(implementationType) ||
               (serviceTask.getImplementation() != null && 
                serviceTask.getImplementation().contains("external"));
    }

    /**
     * 检查是否有补偿处理器
     */
    private boolean hasCompensationHandler(ServiceTask serviceTask) {
        // 检查是否配置了补偿处理器
        return serviceTask.getBoundaryEvents().stream()
                .anyMatch(event -> "compensateEventDefinition".equals(event.getEventDefinitions().get(0).getClass().getSimpleName()));
    }
}
