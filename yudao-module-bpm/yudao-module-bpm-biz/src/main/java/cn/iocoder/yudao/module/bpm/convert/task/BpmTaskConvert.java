package cn.iocoder.yudao.module.bpm.convert.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.CollectionUtils;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmRunningProcessTaskRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmFormDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.enums.task.BpmTaskStatusEnum;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenTaskCreatedReqDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.service.impl.persistence.entity.TaskEntityImpl;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertMultiMap;
import static cn.iocoder.yudao.framework.common.util.collection.MapUtils.findAndThen;
/**
 * Bpm 任务 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmTaskConvert {

    BpmTaskConvert INSTANCE = Mappers.getMapper(BpmTaskConvert.class);

    default PageResult<BpmTaskRespVO> buildTodoTaskPage(PageResult<Task> pageResult,
                                                        Map<String, ProcessInstance> processInstanceMap,
                                                        Map<Long, AdminUserRespDTO> userMap,
                                                        Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        return BeanUtils.toBean(pageResult, BpmTaskRespVO.class, taskVO -> {
            ProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance == null) {
                return;
            }
            taskVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmTaskRespVO.ProcessInstance.class));
            AdminUserRespDTO startUser = userMap.get(FlowableUtils.getProcessInstanceStartUserId(processInstance));
            taskVO.getProcessInstance().setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
            // 摘要
            taskVO.getProcessInstance().setSummary(FlowableUtils.getSummary(processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                    processInstance.getProcessVariables()));
        });
    }

    default PageResult<BpmTaskRespVO> buildTaskPage(PageResult<HistoricTaskInstance> pageResult,
                                                    Map<String, HistoricProcessInstance> processInstanceMap,
                                                    Map<Long, AdminUserRespDTO> userMap,
                                                    Map<Long, DeptRespDTO> deptMap,
                                                    Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        List<BpmTaskRespVO> taskVOList = CollectionUtils.convertList(pageResult.getList(), task -> {
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);
            taskVO.setStatus(FlowableUtils.getTaskStatus(task)).setReason(FlowableUtils.getTaskReason(task));
            // 用户信息
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                findAndThen(deptMap, assignUser.getDeptId(), dept -> taskVO.getAssigneeUser().setDeptName(dept.getName()));
            }
            // 流程实例
            HistoricProcessInstance processInstance = processInstanceMap.get(taskVO.getProcessInstanceId());
            if (processInstance != null) {
                AdminUserRespDTO startUser = userMap.get(FlowableUtils.getProcessInstanceStartUserId(processInstance));
                taskVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmTaskRespVO.ProcessInstance.class));
                taskVO.getProcessInstance().setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
                // 摘要
                taskVO.getProcessInstance().setSummary(FlowableUtils.getSummary(processDefinitionInfoMap.get(processInstance.getProcessDefinitionId()),
                        processInstance.getProcessVariables()));
            }
            return taskVO;
        });
        return new PageResult<>(taskVOList, pageResult.getTotal());
    }

    default List<BpmTaskRespVO> buildTaskListByProcessInstanceId(List<HistoricTaskInstance> taskList,
                                                                 Map<Long, BpmFormDO> formMap,
                                                                 Map<Long, AdminUserRespDTO> userMap,
                                                                 Map<Long, DeptRespDTO> deptMap) {
        return CollectionUtils.convertList(taskList, task -> {
            // 特殊：已取消的任务，不返回
            BpmTaskRespVO taskVO = BeanUtils.toBean(task, BpmTaskRespVO.class);
            Integer taskStatus = FlowableUtils.getTaskStatus(task);
            if (BpmTaskStatusEnum.isCancelStatus(taskStatus)) {
                return null;
            }
            taskVO.setStatus(taskStatus).setReason(FlowableUtils.getTaskReason(task));
            // 表单信息
            BpmFormDO form = MapUtil.get(formMap, NumberUtils.parseLong(task.getFormKey()), BpmFormDO.class);
            if (form != null) {
                taskVO.setFormId(form.getId()).setFormName(form.getName()).setFormConf(form.getConf())
                        .setFormFields(form.getFields()).setFormVariables(FlowableUtils.getTaskFormVariable(task));
            }
            // 用户信息
            buildTaskAssignee(taskVO, task.getAssignee(), userMap, deptMap);
            buildTaskOwner(taskVO, task.getOwner(), userMap, deptMap);
            return taskVO;
        });
    }

    default List<BpmTaskRespVO> buildTaskListByParentTaskId(List<Task> taskList,
                                                            Map<Long, AdminUserRespDTO> userMap,
                                                            Map<Long, DeptRespDTO> deptMap) {
        return convertList(taskList, task -> BeanUtils.toBean(task, BpmTaskRespVO.class, taskVO -> {
            AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(task.getAssignee()));
            if (assignUser != null) {
                taskVO.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
                DeptRespDTO dept = deptMap.get(assignUser.getDeptId());
                if (dept != null) {
                    taskVO.getAssigneeUser().setDeptName(dept.getName());
                }
            }
            AdminUserRespDTO ownerUser = userMap.get(NumberUtils.parseLong(task.getOwner()));
            if (ownerUser != null) {
                taskVO.setOwnerUser(BeanUtils.toBean(ownerUser, UserSimpleBaseVO.class));
                findAndThen(deptMap, ownerUser.getDeptId(), dept -> taskVO.getOwnerUser().setDeptName(dept.getName()));
            }
        }));
    }

    default BpmTaskRespVO buildTodoTask(Task todoTask, List<Task> childrenTasks,
                                        Map<Integer, BpmTaskRespVO.OperationButtonSetting> buttonsSetting,
                                        BpmFormDO form) {
        BpmTaskRespVO bpmTaskRespVO = BeanUtils.toBean(todoTask, BpmTaskRespVO.class)
                .setStatus(FlowableUtils.getTaskStatus(todoTask)).setReason(FlowableUtils.getTaskReason(todoTask))
                .setButtonsSetting(buttonsSetting)
                .setChildren(convertList(childrenTasks, childTask -> BeanUtils.toBean(childTask, BpmTaskRespVO.class)
                        .setStatus(FlowableUtils.getTaskStatus(childTask))));
        if (form != null) {
            bpmTaskRespVO.setFormId(form.getId()).setFormName(form.getName())
                    .setFormConf(form.getConf()).setFormFields(form.getFields());
        }
        return bpmTaskRespVO;
    }

    default BpmMessageSendWhenTaskCreatedReqDTO convert(ProcessInstance processInstance, AdminUserRespDTO startUser,
                                                        Task task) {
        BpmMessageSendWhenTaskCreatedReqDTO reqDTO = new BpmMessageSendWhenTaskCreatedReqDTO();
        reqDTO.setProcessInstanceId(processInstance.getProcessInstanceId())
                .setProcessInstanceName(processInstance.getName()).setStartUserId(startUser.getId())
                .setStartUserNickname(startUser.getNickname()).setTaskId(task.getId()).setTaskName(task.getName())
                .setAssigneeUserId(NumberUtils.parseLong(task.getAssignee()));
        return reqDTO;
    }

    default void buildTaskOwner(BpmTaskRespVO task, String taskOwner,
                                Map<Long, AdminUserRespDTO> userMap,
                                Map<Long, DeptRespDTO> deptMap) {
        AdminUserRespDTO ownerUser = userMap.get(NumberUtils.parseLong(taskOwner));
        if (ownerUser != null) {
            task.setOwnerUser(BeanUtils.toBean(ownerUser, UserSimpleBaseVO.class));
            findAndThen(deptMap, ownerUser.getDeptId(), dept -> task.getOwnerUser().setDeptName(dept.getName()));
        }
    }

    default void buildTaskChildren(BpmTaskRespVO task, Map<String, List<Task>> childrenTaskMap,
                                   Map<Long, AdminUserRespDTO> userMap, Map<Long, DeptRespDTO> deptMap) {
        List<Task> childTasks = childrenTaskMap.get(task.getId());
        if (CollUtil.isNotEmpty(childTasks)) {
            task.setChildren(
                    convertList(childTasks, childTask -> {
                        BpmTaskRespVO childTaskVO = BeanUtils.toBean(childTask, BpmTaskRespVO.class);
                        childTaskVO.setStatus(FlowableUtils.getTaskStatus(childTask));
                        buildTaskOwner(childTaskVO, childTask.getOwner(), userMap, deptMap);
                        buildTaskAssignee(childTaskVO, childTask.getAssignee(), userMap, deptMap);
                        return childTaskVO;
                    })
            );
        }
    }

    default void buildTaskAssignee(BpmTaskRespVO task, String taskAssignee,
                                   Map<Long, AdminUserRespDTO> userMap,
                                   Map<Long, DeptRespDTO> deptMap) {
        AdminUserRespDTO assignUser = userMap.get(NumberUtils.parseLong(taskAssignee));
        if (assignUser != null) {
            task.setAssigneeUser(BeanUtils.toBean(assignUser, UserSimpleBaseVO.class));
            findAndThen(deptMap, assignUser.getDeptId(), dept -> task.getAssigneeUser().setDeptName(dept.getName()));
        }
    }

    /**
     * 将父任务的属性，拷贝到子任务（加签任务）
     * <p>
     * 为什么不使用 mapstruct 映射？因为 TaskEntityImpl 还有很多其他属性，这里我们只设置我们需要的。
     * 使用 mapstruct 会将里面嵌套的各个属性值都设置进去，会出现意想不到的问题。
     *
     * @param parentTask 父任务
     * @param childTask  加签任务
     */
    default void copyTo(TaskEntityImpl parentTask, TaskEntityImpl childTask) {
        childTask.setName(parentTask.getName());
        childTask.setDescription(parentTask.getDescription());
        childTask.setCategory(parentTask.getCategory());
        childTask.setParentTaskId(parentTask.getId());
        childTask.setProcessDefinitionId(parentTask.getProcessDefinitionId());
        childTask.setProcessInstanceId(parentTask.getProcessInstanceId());
        childTask.setTaskDefinitionKey(parentTask.getTaskDefinitionKey());
        childTask.setTaskDefinitionId(parentTask.getTaskDefinitionId());
        childTask.setPriority(parentTask.getPriority());
        childTask.setCreateTime(new Date());
        childTask.setTenantId(parentTask.getTenantId());
    }

    default List<BpmRunningProcessTaskRespVO> buildRunningProcessTaskList(List<HistoricProcessInstance> instances,
                                                                          Map<String, List<Task>> taskMap,
                                                                          Map<Long, AdminUserRespDTO> userMap) {
        return convertList(instances, instance -> {
            BpmRunningProcessTaskRespVO vo = new BpmRunningProcessTaskRespVO();
            vo.setId(instance.getId());
            vo.setName(instance.getName());
            vo.setCreatorUserId(Long.valueOf(instance.getStartUserId()));
            vo.setStartTime(DateUtils.of(instance.getStartTime()));
            vo.setCreatorMobile(userMap.get(vo.getCreatorUserId()).getMobile());
            List<Task> tasks = taskMap.get(instance.getId());
            if (CollUtil.isNotEmpty(tasks)) {
                Map<String, List<Task>> nodeMap = convertMultiMap(tasks, Task::getTaskDefinitionKey);
                vo.setNodes(convertList(nodeMap.entrySet(), entry -> {
                    BpmRunningProcessTaskRespVO.NodeTask node = new BpmRunningProcessTaskRespVO.NodeTask();
                    node.setId(entry.getKey());
                    Task first = CollUtil.getFirst(entry.getValue());
                    node.setName(first != null ? first.getName() : null);

                    // 创建用户列表，包装成UserTaskWrapper
                    List<BpmRunningProcessTaskRespVO.UserTaskWrapper> userTaskWrappers = convertList(entry.getValue(), t -> {
                        AdminUserRespDTO user = userMap.get(NumberUtils.parseLong(t.getAssignee()));
                        // 检查用户是否存在
                        if (user != null) {
                            BpmRunningProcessTaskRespVO.UserTaskWrapper wrapper = new BpmRunningProcessTaskRespVO.UserTaskWrapper();
                            wrapper.setUser(BeanUtils.toBean(user, AdminUserRespDTO.class));

                            // 设置taskId
                            wrapper.setTaskId(t.getId());  // 假设t.getId()是当前任务的ID
                            return wrapper;
                        }
                        return null;
                    });

                    // 过滤掉null值
                    userTaskWrappers = userTaskWrappers.stream()
                            .filter(Objects::nonNull)
                            .collect(Collectors.toList());

                    node.setUsers(userTaskWrappers);
                    return node;
                }));
            } else {
                // 当tasks为空或null时，设置nodes为空数组而不是null
                vo.setNodes(new ArrayList<>());
            }
            return vo;
        });
    }
}
