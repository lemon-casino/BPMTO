package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.*;
import cn.iocoder.yudao.module.bpm.convert.task.BpmTaskConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmCommentTypeEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.definition.BpmFormService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmModelService;
import cn.iocoder.yudao.module.bpm.service.definition.BpmProcessDefinitionService;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceService;
import cn.iocoder.yudao.module.bpm.service.task.BpmTaskService;
import cn.iocoder.yudao.module.bpm.service.worktime.BpmWorkTimeService;
import cn.iocoder.yudao.module.system.api.dept.DeptApi;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.UserTask;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Stream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.*;
import static cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.PROCESS_INSTANCE_NOT_EXISTS;
import static cn.iocoder.yudao.module.bpm.enums.ErrorCodeConstants.TASK_NOT_EXISTS;

@Tag(name = "管理后台 - 流程任务实例")
@RestController
@RequestMapping("/bpm/task")
@Validated
@Slf4j
public class BpmTaskController {

    @Resource
    private BpmTaskService bpmTaskService;
    @Resource
    private BpmProcessInstanceService processInstanceService;
    @Resource
    private BpmFormService formService;
    @Resource
    private BpmProcessDefinitionService processDefinitionService;
    @Resource
    private TaskService taskService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private DeptApi deptApi;
    @Resource
    private BpmModelService modelService;
    @Resource
    private BpmWorkTimeService workTimeService;
    @GetMapping("todo-page")
    @Operation(summary = "获取 Todo 待办任务分页")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskTodoPage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<Task> pageResult = bpmTaskService.getTaskTodoPage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, ProcessInstance> processInstanceMap = processInstanceService.getProcessInstanceMap(
                convertSet(pageResult.getList(), Task::getProcessInstanceId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(processInstanceMap.values(), instance -> Long.valueOf(instance.getStartUserId())));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), Task::getProcessDefinitionId));
        return success(BpmTaskConvert.INSTANCE.buildTodoTaskPage(pageResult, processInstanceMap, userMap, processDefinitionInfoMap));
    }

    @GetMapping("done-page")
    @Operation(summary = "获取 Done 已办任务分页")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskDonePage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<HistoricTaskInstance> pageResult = bpmTaskService.getTaskDonePage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessInstanceId));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(
                convertSet(processInstanceMap.values(), instance -> Long.valueOf(instance.getStartUserId())));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessDefinitionId));
        return success(BpmTaskConvert.INSTANCE.buildTaskPage(pageResult, processInstanceMap, userMap, null, processDefinitionInfoMap));
    }

    @GetMapping("manager-page")
    @Operation(summary = "获取全部任务的分页", description = "用于【流程任务】菜单")
    @PreAuthorize("@ss.hasPermission('bpm:task:mananger-query')")
    public CommonResult<PageResult<BpmTaskRespVO>> getTaskManagerPage(@Valid BpmTaskPageReqVO pageVO) {
        PageResult<HistoricTaskInstance> pageResult = bpmTaskService.getTaskPage(getLoginUserId(), pageVO);
        if (CollUtil.isEmpty(pageResult.getList())) {
            return success(PageResult.empty());
        }

        // 拼接数据
        Map<String, HistoricProcessInstance> processInstanceMap = processInstanceService.getHistoricProcessInstanceMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessInstanceId));
        // 获得 User 和 Dept Map
        Set<Long> userIds = convertSet(processInstanceMap.values(), instance -> Long.valueOf(instance.getStartUserId()));
        userIds.addAll(convertSet(pageResult.getList(), task -> NumberUtils.parseLong(task.getAssignee())));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap = processDefinitionService.getProcessDefinitionInfoMap(
                convertSet(pageResult.getList(), HistoricTaskInstance::getProcessDefinitionId));
        return success(BpmTaskConvert.INSTANCE.buildTaskPage(pageResult, processInstanceMap, userMap, deptMap, processDefinitionInfoMap));
    }

    @GetMapping("/list-by-process-instance-id")
    @Operation(summary = "获得指定流程实例的任务列表", description = "包括完成的、未完成的")
    @Parameter(name = "processInstanceId", description = "流程实例的编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByProcessInstanceId(
            @RequestParam("processInstanceId") String processInstanceId) {
        List<HistoricTaskInstance> taskList = bpmTaskService.getTaskListByProcessInstanceId(processInstanceId, true);
        if (CollUtil.isEmpty(taskList)) {
            return success(Collections.emptyList());
        }

        // 拼接数据
        Set<Long> userIds = convertSetByFlatMap(taskList, task ->
                Stream.of(NumberUtils.parseLong(task.getAssignee()), NumberUtils.parseLong(task.getOwner())));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        // 获得 Form Map
        Map<Long, BpmFormDO> formMap = formService.getFormMap(
                convertSet(taskList, task -> NumberUtils.parseLong(task.getFormKey())));
        return success(BpmTaskConvert.INSTANCE.buildTaskListByProcessInstanceId(taskList,
                formMap, userMap, deptMap));
    }

    @PutMapping("/approve")
    @Operation(summary = "通过任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> approveTask(@Valid @RequestBody BpmTaskApproveReqVO reqVO) {
        bpmTaskService.approveTask(getLoginUserId(), reqVO);
        return success(true);
    }
    //管理员修改流程表单
    @PutMapping("/warden-approve")
    @Operation(summary = "管理员修改流程允许中的表单")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> wardenApproveTask(@Valid @RequestBody BpmTaskApproveReqVO reqVO) {
        log.info("[wardenApproveTask][准备修改流程表单，入参：{}]", reqVO);
        // 1. 校验任务是否存在
        Task task = bpmTaskService.getTask(reqVO.getId());
        if (task == null) {
            throw exception(TASK_NOT_EXISTS);
        }
        log.info("[wardenApproveTask][任务信息：{}]", task);
        
        // 2. 校验流程实例是否存在
        ProcessInstance instance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
        if (instance == null) {
            throw exception(PROCESS_INSTANCE_NOT_EXISTS);
        }
        log.info("[wardenApproveTask][流程实例变量-修改前：{}]", instance.getProcessVariables());
        
        // 3. 校验当前用户是否是流程模型的管理员 - 修正为使用流程实例ID
        String processInstanceId = task.getProcessInstanceId();
        if (!modelService.isUserProcessInstanceModelManager(getLoginUserId(), processInstanceId)) {
            log.warn("[wardenApproveTask][用户非管理员，无权修改表单] userId={}, processInstanceId={}", 
                getLoginUserId(), processInstanceId);
            return success(false); // 如果不是管理员，直接返回失败
        }
        
        // 4. 更新流程实例变量（表单数据）
        boolean updateSuccess = false;
        if (CollUtil.isNotEmpty(reqVO.getVariables())) {
            Map<String, Object> variables = FlowableUtils.filterTaskFormVariable(reqVO.getVariables());
            log.info("[wardenApproveTask][过滤后的表单变量：{}]", variables);
            
            // 添加修改记录评论
            String reason = StrUtil.isNotBlank(reqVO.getReason()) ? reqVO.getReason() : "管理员修改表单";
            taskService.addComment(task.getId(), task.getProcessInstanceId(),
                BpmCommentTypeEnum.WARDEN_UPDATE.getType(), 
                BpmCommentTypeEnum.WARDEN_UPDATE.formatComment(
                    adminUserApi.getUser(getLoginUserId()).getNickname(), reason));
                
            // 处理表单变量并更新流程实例
            Map<String, Object> origVars = new HashMap<>(variables);
            variables = FlowableUtils.processFormVariables(variables);
            log.info("[wardenApproveTask][处理后的表单变量：{}]", variables);
            
            // 对比原始变量和处理后变量是否有差异
            Map<String, Object> finalVariables = variables;
            origVars.forEach((key, value) -> {
                Object processed = finalVariables.get(key);
                if (!Objects.equals(value, processed)) {
                    log.info("[wardenApproveTask][字段({})类型转换：{}({}) -> {}({})]", 
                            key, value, value != null ? value.getClass().getName() : "null", 
                            processed, processed != null ? processed.getClass().getName() : "null");
                }
            });
            
            try {
                // 更新流程实例变量
                processInstanceService.updateProcessInstanceVariables(task.getProcessInstanceId(), variables);
                
                // 获取更新后的流程实例，验证变量是否正确保存
                ProcessInstance updatedInstance = processInstanceService.getProcessInstance(task.getProcessInstanceId());
                Map<String, Object> updatedVars = updatedInstance.getProcessVariables();
                log.info("[wardenApproveTask][流程实例变量-修改后：{}]", updatedVars);
                
                // 逐个验证变量是否正确保存
                boolean allSaved = true;
                Map<String, Object> missingVars = new HashMap<>();
                for (Map.Entry<String, Object> entry : variables.entrySet()) {
                    String key = entry.getKey();
                    Object expected = entry.getValue();
                    Object actual = updatedVars.get(key);
                    
                    if (!Objects.equals(expected, actual)) {
                        allSaved = false;
                        missingVars.put(key, expected);
                        log.error("[wardenApproveTask][变量({})保存失败: 预期值={}, 实际值={}]", 
                                key, expected, actual);
                    }
                }
                
                if (!allSaved) {
                    log.error("[wardenApproveTask][部分变量保存失败: {}]", missingVars);
                } else {
                    log.info("[wardenApproveTask][所有变量保存成功]");
                    updateSuccess = true;
                }
            } catch (Exception e) {
                log.error("[wardenApproveTask][更新流程变量异常]", e);
            }
        }
        
        return success(updateSuccess);
    }
    @PutMapping("/reject")
    @Operation(summary = "不通过任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> rejectTask(@Valid @RequestBody BpmTaskRejectReqVO reqVO) {
        bpmTaskService.rejectTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/list-by-return")
    @Operation(summary = "获取所有可退回的节点", description = "用于【流程详情】的【退回】按钮")
    @Parameter(name = "taskId", description = "当前任务ID", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByReturn(@RequestParam("id") String id) {
        List<UserTask> userTaskList = bpmTaskService.getUserTaskListByReturn(id);
        return success(convertList(userTaskList, userTask -> // 只返回 id 和 name
                new BpmTaskRespVO().setName(userTask.getName()).setTaskDefinitionKey(userTask.getId())));
    }
    //获得所有的正在执行的节点
    @GetMapping("/current-node-list")
    @Operation(summary = "获取所有正在执行的活动节点", description = "用于【流程详情】的【管理员退回】按钮")
    @Parameter(name = "processInstance", description = "流程中的某个流程示例id", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<List<BpmTaskRespVO>> currentNodeList(@RequestParam("id") String id) {
        // 获取流程实例
        // 获取当前流程实例下所有运行中的任务
        List<Task> tasks = bpmTaskService.getRunningTaskListByProcessInstanceId(id, null, null);
        if (CollUtil.isEmpty(tasks)) {
            return success(Collections.emptyList());
        }
        
        // 获取用户和部门信息
        Set<Long> userIds = convertSetByFlatMap(tasks, task ->
                Stream.of(NumberUtils.parseLong(task.getAssignee()), NumberUtils.parseLong(task.getOwner())));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(userIds);
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        System.out.println(BpmTaskConvert.INSTANCE.buildTaskListByParentTaskId(tasks, userMap, deptMap).toString());
        // 转换为前端所需格式并返回
        return success(BpmTaskConvert.INSTANCE.buildTaskListByParentTaskId(tasks, userMap, deptMap));
    }

    @GetMapping("/running-list")
    @Operation(summary = "实时获取运行中的任务列表")
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<BpmRunningProcessTaskRespVO>> getRunningTaskList() {
        List<HistoricProcessInstance> instances = processInstanceService.getRunningProcessInstanceList();
        if (CollUtil.isEmpty(instances)) {
            return success(Collections.emptyList());
        }

        Set<String> instanceIds = convertSet(instances, HistoricProcessInstance::getId);
        List<Task> tasks = bpmTaskService.getRunningTasksByProcessInstanceIds(instanceIds);
        Map<String, List<Task>> taskMap = convertMultiMap(tasks, Task::getProcessInstanceId);
        Set<Long> userIds = convertSet(tasks, task -> NumberUtils.parseLong(task.getAssignee()));
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserAll();
        List<BpmRunningProcessTaskRespVO> list = BpmTaskConvert.INSTANCE.buildRunningProcessTaskList(instances, taskMap, userMap);

        // 计算超时时间
        for (BpmRunningProcessTaskRespVO vo : list) {
            List<Task> instanceTasks = taskMap.get(vo.getId());
            if (CollUtil.isEmpty(instanceTasks)) {
                continue;
            }
            Map<String, Task> map = convertMap(instanceTasks, Task::getId);
            for (BpmRunningProcessTaskRespVO.NodeTask node : vo.getNodes()) {
                for (BpmRunningProcessTaskRespVO.UserTaskWrapper wrapper : node.getUsers()) {
                    Task t = map.get(wrapper.getTaskId());
                    if (t == null) {
                        continue;
                    }
                    
                    // 对于启用工作时间计算的任务，直接使用task.getDueDate()
                    // 因为在任务创建时已经按工作时间重新计算了dueDate
                    if (t.getDueDate() != null) {
                        BpmnModel model = modelService.getBpmnModelByDefinitionId(t.getProcessDefinitionId());
                        FlowElement element = BpmnModelUtils.getFlowElementById(model, t.getTaskDefinitionKey());
                        Boolean workTimeEnable = BpmnModelUtils.parseWorkTimeEnable(element);
                        
                        if (Boolean.TRUE.equals(workTimeEnable)) {
                            // 工作时间模式：直接使用已计算的dueDate，不需要重新计算
                            wrapper.setDueTime(DateUtils.of(t.getDueDate()));
                            log.debug("[getRunningTaskList][任务({})启用工作时间，使用已计算的截止时间: {}]", 
                                    t.getId(), DateUtils.of(t.getDueDate()));
                        } else {
                            // 标准模式：使用原始dueDate
                            wrapper.setDueTime(DateUtils.of(t.getDueDate()));
                        }
                    }
                }
            }
        }

        return success(list);
    }



    @PutMapping("/return")
    @Operation(summary = "退回任务", description = "用于【流程详情】的【退回】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> returnTask(@Valid @RequestBody BpmTaskReturnReqVO reqVO) {
        Long currentUserId = getLoginUserId();
        //判断是否是管理员
        if (StrUtil.isNotBlank(reqVO.getProcessInstanceId())) {
            // 检查当前用户是否是流程实例的模型管理员
            reqVO.setManagerial(modelService.isUserProcessInstanceModelManager(currentUserId, reqVO.getProcessInstanceId()));
        }
        bpmTaskService.returnTask(currentUserId, reqVO);
        return success(true);
    }

    @PutMapping("/delegate")
    @Operation(summary = "委派任务", description = "用于【流程详情】的【委派】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> delegateTask(@Valid @RequestBody BpmTaskDelegateReqVO reqVO) {
        bpmTaskService.delegateTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/transfer")
    @Operation(summary = "转办任务", description = "用于【流程详情】的【转派】按钮")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> transferTask(@Valid @RequestBody BpmTaskTransferReqVO reqVO) {
        bpmTaskService.transferTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/create-sign")
    @Operation(summary = "加签", description = "before 前加签，after 后加签")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> createSignTask(@Valid @RequestBody BpmTaskSignCreateReqVO reqVO) {
        bpmTaskService.createSignTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @DeleteMapping("/delete-sign")
    @Operation(summary = "减签")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> deleteSignTask(@Valid @RequestBody BpmTaskSignDeleteReqVO reqVO) {
        bpmTaskService.deleteSignTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @PutMapping("/copy")
    @Operation(summary = "抄送任务")
    @PreAuthorize("@ss.hasPermission('bpm:task:update')")
    public CommonResult<Boolean> copyTask(@Valid @RequestBody BpmTaskCopyReqVO reqVO) {
        bpmTaskService.copyTask(getLoginUserId(), reqVO);
        return success(true);
    }

    @GetMapping("/list-by-parent-task-id")
    @Operation(summary = "获得指定父级任务的子任务列表") // 目前用于，减签的时候，获得子任务列表
    @Parameter(name = "parentTaskId", description = "父级任务编号", required = true)
    @PreAuthorize("@ss.hasPermission('bpm:task:query')")
    public CommonResult<List<BpmTaskRespVO>> getTaskListByParentTaskId(@RequestParam("parentTaskId") String parentTaskId) {
        List<Task> taskList = bpmTaskService.getTaskListByParentTaskId(parentTaskId);
        if (CollUtil.isEmpty(taskList)) {
            return success(Collections.emptyList());
        }
        // 拼接数据
        Map<Long, AdminUserRespDTO> userMap = adminUserApi.getUserMap(convertSetByFlatMap(taskList,
                user -> Stream.of(NumberUtils.parseLong(user.getAssignee()), NumberUtils.parseLong(user.getOwner()))));
        Map<Long, DeptRespDTO> deptMap = deptApi.getDeptMap(
                convertSet(userMap.values(), AdminUserRespDTO::getDeptId));
        return success(BpmTaskConvert.INSTANCE.buildTaskListByParentTaskId(taskList, userMap, deptMap));
    }


}
