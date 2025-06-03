package cn.iocoder.yudao.module.bpm.framework.flowable.core.listener;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.enums.definition.BpmBoundaryEventTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnModelConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import com.google.common.collect.ImmutableSet;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BoundaryEvent;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.delegate.event.AbstractFlowableEngineEventListener;
import org.flowable.engine.delegate.event.FlowableActivityCancelledEvent;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 监听 {@link Task} 的开始与完成
 *
 * @author jason
 */
@Component
@Slf4j
public class BpmTaskEventListener extends AbstractFlowableEngineEventListener {

    @Resource
    @Lazy // 延迟加载，避免循环依赖
    private BpmModelService modelService;
    @Resource
    @Lazy // 解决循环依赖
    private BpmTaskService taskService;

    public static final Set<FlowableEngineEventType> TASK_EVENTS = ImmutableSet.<FlowableEngineEventType>builder()
            .add(FlowableEngineEventType.TASK_CREATED)
            .add(FlowableEngineEventType.TASK_ASSIGNED)
//            .add(FlowableEngineEventType.TASK_COMPLETED) // 由于审批通过时，已经记录了 task 的 status 为通过，所以不需要监听了。
            .add(FlowableEngineEventType.ACTIVITY_CANCELLED)
            .add(FlowableEngineEventType.TIMER_FIRED) // 监听审批超时
            .build();

    public BpmTaskEventListener() {
        super(TASK_EVENTS);
    }

    @Override
    protected void taskCreated(FlowableEngineEntityEvent event) {
        taskService.processTaskCreated((Task) event.getEntity());
    }

    @Override
    protected void taskAssigned(FlowableEngineEntityEvent event) {
        taskService.processTaskAssigned((Task) event.getEntity());
    }

    @Override
    protected void activityCancelled(FlowableActivityCancelledEvent event) {
        log.info("[activityCancelled][开始处理活动取消事件] executionId:{}, activityId:{}, activityName:{}",
                event.getExecutionId(), event.getActivityId(), event.getActivityName());

        // 1. 先查找历史活动实例
        List<HistoricActivityInstance> activityList = taskService.getHistoricActivityListByExecutionId(event.getExecutionId());

        // 2. 如果历史实例为空，尝试获取运行中的任务
        if (CollUtil.isEmpty(activityList)) {
            List<Task> runningTasks = taskService.getRunningTaskListByProcessInstanceId(event.getProcessInstanceId(), null, event.getActivityId());
            if (CollUtil.isNotEmpty(runningTasks)) {
                log.info("[activityCancelled][处理运行中的任务] executionId:{}, taskSize:{}", event.getExecutionId(), runningTasks.size());
                runningTasks.forEach(task -> {
                    log.info("[activityCancelled][处理任务取消] taskId:{}, taskDefinitionKey:{}", task.getId(), task.getTaskDefinitionKey());
                    taskService.processTaskCanceled(task.getId());
                });
                return;
            }

            log.warn("[activityCancelled][未找到活动实例，可能是并行网关中的空分支] executionId:{}", event.getExecutionId());
            return;
        }

        // 3. 处理历史活动实例
        activityList.forEach(activity -> {
            if (StrUtil.isEmpty(activity.getTaskId())) {
                log.debug("[activityCancelled][跳过非任务节点] activityId:{}, activityType:{}",
                        activity.getActivityId(), activity.getActivityType());
                return;
            }
            log.info("[activityCancelled][处理任务取消] taskId:{}, activityId:{}",
                    activity.getTaskId(), activity.getActivityId());
            log.info("处理历史活动实例 --> processTaskCanceled");
            taskService.processTaskCanceled(activity.getTaskId());
        });

        log.info("[activityCancelled][完成活动取消事件处理] executionId:{}", event.getExecutionId());
    }

    @Override
    @SuppressWarnings("PatternVariableCanBeUsed")
    protected void timerFired(FlowableEngineEntityEvent event) {
        // 1.1 只处理 BoundaryEvent 边界计时时间
        String processDefinitionId = event.getProcessDefinitionId();
        BpmnModel bpmnModel = modelService.getBpmnModelByDefinitionId(processDefinitionId);
        Job entity = (Job) event.getEntity();
        FlowElement element = BpmnModelUtils.getFlowElementById(bpmnModel, entity.getElementId());
        if (!(element instanceof BoundaryEvent)) {
            return;
        }
        // 1.2 判断是否为超时处理
        BoundaryEvent boundaryEvent = (BoundaryEvent) element;
        String boundaryEventType = BpmnModelUtils.parseBoundaryEventExtensionElement(boundaryEvent,
                BpmnModelConstants.BOUNDARY_EVENT_TYPE);
        BpmBoundaryEventTypeEnum bpmTimerBoundaryEventType = BpmBoundaryEventTypeEnum.typeOf(NumberUtils.parseInt(boundaryEventType));

        // 2. 处理超时
        if (ObjectUtil.equal(bpmTimerBoundaryEventType, BpmBoundaryEventTypeEnum.USER_TASK_TIMEOUT)) {
            // 2.1 用户任务超时处理
            String timeoutHandlerType = BpmnModelUtils.parseBoundaryEventExtensionElement(boundaryEvent,
                    BpmnModelConstants.USER_TASK_TIMEOUT_HANDLER_TYPE);
            String taskKey = boundaryEvent.getAttachedToRefId();
            taskService.processTaskTimeout(event.getProcessInstanceId(), taskKey, NumberUtils.parseInt(timeoutHandlerType));
            // 2.2 延迟器超时处理
        } else if (ObjectUtil.equal(bpmTimerBoundaryEventType, BpmBoundaryEventTypeEnum.DELAY_TIMER_TIMEOUT)) {
            String taskKey = boundaryEvent.getAttachedToRefId();
            taskService.processDelayTimerTimeout(event.getProcessInstanceId(), taskKey);
        }
    }

}
