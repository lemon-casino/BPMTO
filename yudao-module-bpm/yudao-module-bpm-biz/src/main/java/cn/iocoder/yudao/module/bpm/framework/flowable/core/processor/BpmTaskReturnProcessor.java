package cn.iocoder.yudao.module.bpm.framework.flowable.core.processor;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskReturnReqVO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.context.BpmReturnContext;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.processor.strategy.*;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPM 任务退回处理器
 * 
 * 重新设计的退回功能，解决以下问题：
 * 1. 复杂网关节点退回路径计算错误
 * 2. 多实例任务退回状态不一致
 * 3. 并行分支退回时产生重复节点
 * 4. 包容网关退回逻辑缺失
 * 
 * @author 芋道源码
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmTaskReturnProcessor {

    private final TaskService taskService;
    private final RuntimeService runtimeService;
    private final BpmModelService modelService;
    private final BpmTaskServiceImpl bpmTaskService;

    /**
     * 退回策略映射
     */
    private final Map<Class<? extends FlowElement>, BpmReturnStrategy> strategyMap = new HashMap<>();

    /**
     * 初始化退回策略
     */
    private void initStrategies() {
        if (strategyMap.isEmpty()) {
            strategyMap.put(org.flowable.bpmn.model.UserTask.class, new UserTaskReturnStrategy());
            strategyMap.put(org.flowable.bpmn.model.ParallelGateway.class, new ParallelGatewayReturnStrategy());
            strategyMap.put(org.flowable.bpmn.model.InclusiveGateway.class, new InclusiveGatewayReturnStrategy());
            strategyMap.put(org.flowable.bpmn.model.ExclusiveGateway.class, new ExclusiveGatewayReturnStrategy());
            strategyMap.put(org.flowable.bpmn.model.ServiceTask.class, new ServiceTaskReturnStrategy());
        }
    }

    /**
     * 处理任务退回
     *
     * @param userId 用户ID
     * @param reqVO  退回请求
     */
    public void processReturn(Long userId, BpmTaskReturnReqVO reqVO) {
        log.info("[processReturn] 开始处理任务退回，userId={}, taskId={}, targetTaskKey={}", 
                userId, reqVO.getId(), reqVO.getTargetTaskDefinitionKey());

        try {
            // 1. 构建退回上下文
            BpmReturnContext context = buildReturnContext(userId, reqVO);
            
            // 2. 执行退回前置校验
            validateReturnRequest(context);
            
            // 3. 计算退回路径和影响范围
            calculateReturnPath(context);
            
            // 4. 执行退回操作
            executeReturn(context);
            
            log.info("[processReturn] 任务退回处理完成，processInstanceId={}", 
                    context.getCurrentTask().getProcessInstanceId());
                    
        } catch (Exception e) {
            log.error("[processReturn] 任务退回处理失败，userId={}, taskId={}", userId, reqVO.getId(), e);
            throw e;
        }
    }

    /**
     * 构建退回上下文
     */
    private BpmReturnContext buildReturnContext(Long userId, BpmTaskReturnReqVO reqVO) {
        // 1. 获取当前任务
        Task currentTask = bpmTaskService.validateTask(userId, reqVO.getId());
        if (currentTask.isSuspended()) {
            throw new RuntimeException("任务已暂停，无法退回");
        }

        // 2. 获取流程模型
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(currentTask.getProcessDefinitionId());
        
        // 3. 获取源节点和目标节点
        FlowElement sourceElement = BpmnModelUtils.getFlowElementById(bpmnModel, currentTask.getTaskDefinitionKey());
        FlowElement targetElement = BpmnModelUtils.getFlowElementById(bpmnModel, reqVO.getTargetTaskDefinitionKey());
        
        if (sourceElement == null) {
            throw new RuntimeException("源节点不存在：" + currentTask.getTaskDefinitionKey());
        }
        if (targetElement == null) {
            throw new RuntimeException("目标节点不存在：" + reqVO.getTargetTaskDefinitionKey());
        }

        // 4. 获取流程实例中的所有活动任务
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(currentTask.getProcessInstanceId())
                .list();

        return BpmReturnContext.builder()
                .userId(userId)
                .currentTask(currentTask)
                .targetTaskDefinitionKey(reqVO.getTargetTaskDefinitionKey())
                .reason(reqVO.getReason())
                .bpmnModel(bpmnModel)
                .sourceElement(sourceElement)
                .targetElement(targetElement)
                .activeTasks(activeTasks)
                .taskService(taskService)
                .runtimeService(runtimeService)
                .bpmTaskService(bpmTaskService)
                .build();
    }

    /**
     * 校验退回请求
     */
    private void validateReturnRequest(BpmReturnContext context) {
        // 1. 校验目标节点类型是否支持退回
        if (!isSupportedReturnTarget(context.getTargetElement())) {
            throw new RuntimeException("目标节点类型不支持退回：" + context.getTargetElement().getClass().getSimpleName());
        }

        // 2. 校验退回路径的可达性
        if (!isReturnPathValid(context)) {
            throw new RuntimeException("无法从当前节点退回到目标节点，请检查流程路径");
        }

        // 3. 校验是否存在循环退回
        if (hasCircularReturn(context)) {
            throw new RuntimeException("检测到循环退回，操作被拒绝");
        }
    }

    /**
     * 计算退回路径和影响范围
     */
    private void calculateReturnPath(BpmReturnContext context) {
        initStrategies();
        
        // 1. 根据目标节点类型选择策略
        BpmReturnStrategy strategy = strategyMap.get(context.getTargetElement().getClass());
        if (strategy == null) {
            throw new RuntimeException("不支持的目标节点类型：" + context.getTargetElement().getClass().getSimpleName());
        }

        // 2. 计算需要撤回的任务和执行流
        strategy.calculateReturnScope(context);
        
        log.info("[calculateReturnPath] 计算退回范围完成，需要撤回的任务数量：{}, 执行流数量：{}", 
                context.getTasksToReturn().size(), context.getExecutionsToMove().size());
    }

    /**
     * 执行退回操作
     */
    private void executeReturn(BpmReturnContext context) {
        // 1. 处理任务状态和评论
        processTaskStatusAndComments(context);
        
        // 2. 设置流程变量标记
        setReturnProcessVariables(context);
        
        // 3. 执行流程引擎退回
        executeFlowableReturn(context);
        
        // 4. 清理和恢复操作
        cleanupAndRestore(context);
    }

    /**
     * 处理任务状态和评论
     */
    private void processTaskStatusAndComments(BpmReturnContext context) {
        context.getTasksToReturn().forEach(task -> {
            if (bpmTaskService.isAssignUserTask(context.getUserId(), task)) {
                // 自己的任务，标记为退回
                taskService.addComment(task.getId(), context.getCurrentTask().getProcessInstanceId(), 
                        BpmCommentTypeEnum.RETURN.getType(),
                        BpmCommentTypeEnum.RETURN.formatComment(context.getReason()));
                bpmTaskService.updateTaskStatusAndReason(task.getId(), 
                        BpmTaskStatusEnum.RETURN.getStatus(), context.getReason());
            } else {
                // 他人的任务，标记为取消
                bpmTaskService.processTaskCanceled(task.getId());
            }
        });
    }

    /**
     * 设置流程变量标记
     */
    private void setReturnProcessVariables(BpmReturnContext context) {
        String returnFlag = String.format("RETURN_FLAG_%s", context.getTargetTaskDefinitionKey());
        runtimeService.setVariable(context.getCurrentTask().getProcessInstanceId(), returnFlag, Boolean.TRUE);
    }

    /**
     * 执行Flowable引擎退回
     */
    private void executeFlowableReturn(BpmReturnContext context) {
        if (CollUtil.isNotEmpty(context.getExecutionsToMove())) {
            runtimeService.createChangeActivityStateBuilder()
                    .processInstanceId(context.getCurrentTask().getProcessInstanceId())
                    .moveExecutionsToSingleActivityId(context.getExecutionsToMove(), 
                            context.getTargetTaskDefinitionKey())
                    .changeState();
        }
    }

    /**
     * 清理和恢复操作
     */
    private void cleanupAndRestore(BpmReturnContext context) {
        // 清理可能产生的重复任务
        cleanupDuplicateTasks(context);
        
        // 恢复目标节点的正确状态
        restoreTargetNodeState(context);
    }

    // ========== 辅助方法 ==========

    private boolean isSupportedReturnTarget(FlowElement element) {
        initStrategies();
        return strategyMap.containsKey(element.getClass());
    }

    private boolean isReturnPathValid(BpmReturnContext context) {
        // 使用改进的路径验证算法
        return BpmReturnPathValidator.validateReturnPath(context);
    }

    private boolean hasCircularReturn(BpmReturnContext context) {
        // 检查是否存在循环退回
        return BpmReturnPathValidator.hasCircularReturn(context);
    }

    private void cleanupDuplicateTasks(BpmReturnContext context) {
        // 清理重复任务的逻辑
        BpmReturnCleanupHelper.cleanupDuplicateTasks(context);
    }

    private void restoreTargetNodeState(BpmReturnContext context) {
        // 恢复目标节点状态的逻辑
        BpmReturnCleanupHelper.restoreTargetNodeState(context);
    }
}
