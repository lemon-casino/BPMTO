package cn.iocoder.yudao.module.bpm.convert.task;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.collection.MapUtils;
import cn.iocoder.yudao.framework.common.util.collection.SetUtils;
import cn.iocoder.yudao.framework.common.util.number.NumberUtils;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.base.user.UserSimpleBaseVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.model.simple.BpmSimpleModelNodeVO;
import cn.iocoder.yudao.module.bpm.controller.admin.definition.vo.process.BpmProcessDefinitionRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmApprovalDetailRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceBpmnModelViewRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance.BpmProcessInstanceSimpleRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskRespVO;
import cn.iocoder.yudao.module.bpm.convert.definition.BpmProcessDefinitionConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmCategoryDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.definition.BpmProcessDefinitionInfoDO;
import cn.iocoder.yudao.module.bpm.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.enums.BpmnVariableConstants;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.BpmnModelUtils;
import cn.iocoder.yudao.module.bpm.framework.flowable.core.util.FlowableUtils;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceApproveReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceCancelReqDTO;
import cn.iocoder.yudao.module.bpm.service.message.dto.BpmMessageSendWhenProcessInstanceRejectReqDTO;
import cn.iocoder.yudao.module.system.api.dept.dto.DeptRespDTO;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.factory.Mappers;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.yudao.framework.common.util.collection.CollectionUtils.convertSet;

/**
 * 流程实例 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceConvert {

    BpmProcessInstanceConvert INSTANCE = Mappers.getMapper(BpmProcessInstanceConvert.class);

    default PageResult<BpmProcessInstanceRespVO> buildProcessInstancePage(PageResult<HistoricProcessInstance> pageResult,
                                                                          Map<String, ProcessDefinition> processDefinitionMap,
                                                                          Map<String, BpmCategoryDO> categoryMap,
                                                                          Map<String, List<Task>> taskMap,
                                                                          Map<Long, AdminUserRespDTO> userMap,
                                                                          Map<Long, DeptRespDTO> deptMap,
                                                                          Map<String, BpmProcessDefinitionInfoDO> processDefinitionInfoMap) {
        PageResult<BpmProcessInstanceRespVO> vpPageResult = BeanUtils.toBean(pageResult, BpmProcessInstanceRespVO.class);
        for (int i = 0; i < pageResult.getList().size(); i++) {
            BpmProcessInstanceRespVO respVO = vpPageResult.getList().get(i);
            respVO.setStatus(FlowableUtils.getProcessInstanceStatus(pageResult.getList().get(i)));
            MapUtils.findAndThen(processDefinitionMap, respVO.getProcessDefinitionId(),
                    processDefinition -> respVO.setCategory(processDefinition.getCategory())
                            .setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class)));
            MapUtils.findAndThen(categoryMap, respVO.getCategory(), category -> respVO.setCategoryName(category.getName()));
            respVO.setTasks(BeanUtils.toBean(taskMap.get(respVO.getId()), BpmProcessInstanceRespVO.Task.class));
            // user
            if (userMap != null) {
                Long startUserId = FlowableUtils.getProcessInstanceStartUserId(pageResult.getList().get(i));
                AdminUserRespDTO startUser = userMap.get(startUserId);
                if (startUser != null) {
                    respVO.setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
                    MapUtils.findAndThen(deptMap, startUser.getDeptId(), dept -> respVO.getStartUser().setDeptName(dept.getName()));
                }
            }
            // 摘要
            respVO.setSummary(FlowableUtils.getSummary(processDefinitionInfoMap.get(respVO.getProcessDefinitionId()),
                    pageResult.getList().get(i).getProcessVariables()));
            // 表单
            respVO.setFormVariables(pageResult.getList().get(i).getProcessVariables());
        }
        return vpPageResult;
    }

    default BpmProcessInstanceRespVO buildProcessInstance(HistoricProcessInstance processInstance,
                                                          ProcessDefinition processDefinition,
                                                          BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                          AdminUserRespDTO startUser,
                                                          DeptRespDTO dept) {
        BpmProcessInstanceRespVO respVO = BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class);
        respVO.setStatus(FlowableUtils.getProcessInstanceStatus(processInstance))
                .setFormVariables(FlowableUtils.getProcessInstanceFormVariable(processInstance));
        // definition
        respVO.setProcessDefinition(BeanUtils.toBean(processDefinition, BpmProcessDefinitionRespVO.class));
        copyTo(processDefinitionInfo, respVO.getProcessDefinition());
        // user
        if (startUser != null) {
            respVO.setStartUser(BeanUtils.toBean(startUser, UserSimpleBaseVO.class));
            if (dept != null) {
                respVO.getStartUser().setDeptName(dept.getName());
            }
        }
        return respVO;
    }

    @Mapping(source = "from.id", target = "to.id", ignore = true)
    void copyTo(BpmProcessDefinitionInfoDO from, @MappingTarget BpmProcessDefinitionRespVO to);

    default BpmProcessInstanceStatusEvent buildProcessInstanceStatusEvent(Object source, ProcessInstance instance, Integer status) {
        return new BpmProcessInstanceStatusEvent(source).setId(instance.getId()).setStatus(status)
                .setProcessDefinitionKey(instance.getProcessDefinitionKey()).setBusinessKey(instance.getBusinessKey());
    }

    default BpmMessageSendWhenProcessInstanceApproveReqDTO buildProcessInstanceApproveMessage(ProcessInstance instance) {
        return new BpmMessageSendWhenProcessInstanceApproveReqDTO()
                .setStartUserId(FlowableUtils.getProcessInstanceStartUserId(instance))
                .setProcessInstanceId(instance.getId())
                .setProcessInstanceName(instance.getName());
    }

    default BpmMessageSendWhenProcessInstanceRejectReqDTO buildProcessInstanceRejectMessage(ProcessInstance instance, String reason) {
        return new BpmMessageSendWhenProcessInstanceRejectReqDTO()
            .setProcessInstanceName(instance.getName())
            .setProcessInstanceId(instance.getId())
            .setReason(reason)
            .setStartUserId(FlowableUtils.getProcessInstanceStartUserId(instance));
    }

    default BpmMessageSendWhenProcessInstanceCancelReqDTO buildProcessInstanceCancelMessage(ProcessInstance processInstance) {
        return new BpmMessageSendWhenProcessInstanceCancelReqDTO()
                .setProcessInstanceId(processInstance.getId())
                .setProcessInstanceName(processInstance.getName())
                .setStartUserId(FlowableUtils.getProcessInstanceStartUserId(processInstance))
                .setReason((String) processInstance.getProcessVariables().get(BpmnVariableConstants.PROCESS_INSTANCE_VARIABLE_REASON));
    }

    default BpmProcessInstanceBpmnModelViewRespVO buildProcessInstanceBpmnModelView(HistoricProcessInstance processInstance,
                                                                                    List<HistoricTaskInstance> taskInstances,
                                                                                    BpmnModel bpmnModel,
                                                                                    BpmSimpleModelNodeVO simpleModel,
                                                                                    Set<String> unfinishedTaskActivityIds,
                                                                                    Set<String> finishedTaskActivityIds,
                                                                                    Set<String> finishedSequenceFlowActivityIds,
                                                                                    Set<String> rejectTaskActivityIds,
                                                                                    Map<Long, AdminUserRespDTO> userMap,
                                                                                    Map<Long, DeptRespDTO> deptMap) {
        BpmProcessInstanceBpmnModelViewRespVO respVO = new BpmProcessInstanceBpmnModelViewRespVO();
        // 基本信息
        respVO.setProcessInstance(BeanUtils.toBean(processInstance, BpmProcessInstanceRespVO.class, o -> o
                        .setStatus(FlowableUtils.getProcessInstanceStatus(processInstance)))
                        .setStartUser(buildUser(FlowableUtils.getProcessInstanceStartUserId(processInstance), userMap, deptMap)));
        respVO.setTasks(convertList(taskInstances, task -> BeanUtils.toBean(task, BpmTaskRespVO.class)
                .setStatus(FlowableUtils.getTaskStatus(task)).setReason(FlowableUtils.getTaskReason(task))
                .setAssigneeUser(buildUser(task.getAssignee(), userMap, deptMap))
                .setOwnerUser(buildUser(task.getOwner(), userMap, deptMap))));
        respVO.setBpmnXml(BpmnModelUtils.getBpmnXml(bpmnModel));
        respVO.setSimpleModel(simpleModel);
        // 进度信息
        respVO.setUnfinishedTaskActivityIds(unfinishedTaskActivityIds)
                .setFinishedTaskActivityIds(finishedTaskActivityIds)
                .setFinishedSequenceFlowActivityIds(finishedSequenceFlowActivityIds)
                .setRejectedTaskActivityIds(rejectTaskActivityIds);
        return respVO;
    }

    default UserSimpleBaseVO buildUser(String userIdStr,
                                       Map<Long, AdminUserRespDTO> userMap,
                                       Map<Long, DeptRespDTO> deptMap) {
        if (StrUtil.isEmpty(userIdStr)) {
            return null;
        }
        Long userId = NumberUtils.parseLong(userIdStr);
        return buildUser(userId, userMap, deptMap);
    }

    default UserSimpleBaseVO buildUser(Long userId,
                                                    Map<Long, AdminUserRespDTO> userMap,
                                                    Map<Long, DeptRespDTO> deptMap) {
        if (userId == null) {
            return null;
        }
        AdminUserRespDTO user = userMap.get(userId);
        if (user == null) {
            return null;
        }
        UserSimpleBaseVO userVO = BeanUtils.toBean(user, UserSimpleBaseVO.class);
        DeptRespDTO dept = user.getDeptId() != null ? deptMap.get(user.getDeptId()) : null;
        if (dept != null) {
            userVO.setDeptName(dept.getName());
        }
        return userVO;
    }

    default BpmApprovalDetailRespVO.ActivityNodeTask buildApprovalTaskInfo(HistoricTaskInstance task) {
        if (task == null) {
            return null;
        }
        return BeanUtils.toBean(task, BpmApprovalDetailRespVO.ActivityNodeTask.class)
                .setStatus(FlowableUtils.getTaskStatus(task)).setReason(FlowableUtils.getTaskReason(task))
                .setSignPicUrl(FlowableUtils.getTaskSignPicUrl(task));
    }

    default Set<Long> parseUserIds(HistoricProcessInstance processInstance,
                                   List<BpmApprovalDetailRespVO.ActivityNode> activityNodes,
                                   BpmTaskRespVO todoTask) {
        Set<Long> userIds = new HashSet<>();
        if (processInstance != null) {
            Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());
            // 优先使用流程变量中的用户ID
            Object variableStartUserId = processInstance.getProcessVariables().get("PROCESS_START_USER_ID");
            if (variableStartUserId != null) {
                Long varUserId = NumberUtils.parseLong(variableStartUserId.toString());
                userIds.add(varUserId);
            } else {
                userIds.add(startUserId);
            }
        }
        for (BpmApprovalDetailRespVO.ActivityNode activityNode : activityNodes) {
            CollUtil.addAll(userIds, convertSet(activityNode.getTasks(), BpmApprovalDetailRespVO.ActivityNodeTask::getAssignee));
            CollUtil.addAll(userIds, convertSet(activityNode.getTasks(), BpmApprovalDetailRespVO.ActivityNodeTask::getOwner));
            CollUtil.addAll(userIds, activityNode.getCandidateUserIds());
        }
        if (todoTask != null) {
            CollUtil.addIfAbsent(userIds, todoTask.getAssignee());
            CollUtil.addIfAbsent(userIds, todoTask.getOwner());
            if (CollUtil.isNotEmpty(todoTask.getChildren())) {
                CollUtil.addAll(userIds, convertSet(todoTask.getChildren(), BpmTaskRespVO::getAssignee));
                CollUtil.addAll(userIds, convertSet(todoTask.getChildren(), BpmTaskRespVO::getOwner));
            }
        }
        return userIds;
    }

    default Set<Long> parseUserIds02(HistoricProcessInstance processInstance,
                                     List<HistoricTaskInstance> tasks) {
        // 优先从流程变量中获取发起人ID
        Long startUserId = FlowableUtils.getProcessInstanceStartUserId(processInstance);
        Set<Long> userIds = SetUtils.asSet(startUserId);
        
        tasks.forEach(task -> {
            CollUtil.addIfAbsent(userIds, NumberUtils.parseLong((task.getAssignee())));
            CollUtil.addIfAbsent(userIds, NumberUtils.parseLong((task.getOwner())));
        });
        return userIds;
    }

    default BpmApprovalDetailRespVO buildApprovalDetail(BpmnModel bpmnModel,
                                                        ProcessDefinition processDefinition,
                                                        BpmProcessDefinitionInfoDO processDefinitionInfo,
                                                        HistoricProcessInstance processInstance,
                                                        Integer processInstanceStatus,
                                                        List<BpmApprovalDetailRespVO.ActivityNode> activityNodes,
                                                        BpmTaskRespVO todoTask,
                                                        Map<String, String> formFieldsPermission,
                                                        Map<Long, AdminUserRespDTO> userMap,
                                                        Map<Long, DeptRespDTO> deptMap) {
        // 1.1 流程实例
        BpmProcessInstanceRespVO processInstanceResp = null;
        if (processInstance != null) {
            Long startUserId = NumberUtils.parseLong(processInstance.getStartUserId());
            Object variableStartUserId = processInstance.getProcessVariables().get("PROCESS_START_USER_ID");
            // 优先使用流程变量中的用户ID，因为这是创建流程时实际使用的ID
            if (variableStartUserId != null) {
                Long varUserId = NumberUtils.parseLong(variableStartUserId.toString());
                // 如果变量中的用户ID在userMap中存在，则优先使用它
                if (userMap.containsKey(varUserId)) {
                    startUserId = varUserId;
                }
            }
            
            AdminUserRespDTO startUser = userMap.get(startUserId);
            DeptRespDTO dept = startUser != null ? deptMap.get(startUser.getDeptId()) : null;
            processInstanceResp = buildProcessInstance(processInstance, null, null, startUser, dept);
        }

        // 1.2 流程定义
        BpmProcessDefinitionRespVO definitionResp = BpmProcessDefinitionConvert.INSTANCE.buildProcessDefinition(
                processDefinition, null, processDefinitionInfo, null, null, bpmnModel);
        // 1.3 流程节点
        activityNodes.forEach(approveNode -> {
            if (approveNode.getTasks() != null) {
                approveNode.getTasks().forEach(task -> {
                    task.setAssigneeUser(buildUser(task.getAssignee(), userMap, deptMap));
                    task.setOwnerUser(buildUser(task.getOwner(), userMap, deptMap));
                });
            }
            approveNode.setCandidateUsers(convertList(approveNode.getCandidateUserIds(), userId -> buildUser(userId, userMap, deptMap)));
        });

        // 1.4 待办任务
        if (todoTask != null) {
            todoTask.setAssigneeUser(buildUser(todoTask.getAssignee(), userMap, deptMap));
            todoTask.setOwnerUser(buildUser(todoTask.getOwner(), userMap, deptMap));
            if (CollUtil.isNotEmpty(todoTask.getChildren())) {
                todoTask.getChildren().forEach(childTask -> {
                    childTask.setAssigneeUser(buildUser(childTask.getAssignee(), userMap, deptMap));
                    childTask.setOwnerUser(buildUser(childTask.getOwner(), userMap, deptMap));
                });
            }
        }

        // 2. 拼接起来
        return new BpmApprovalDetailRespVO().setStatus(processInstanceStatus)
                .setProcessDefinition(definitionResp)
                .setProcessInstance(processInstanceResp)
                .setFormFieldsPermission(formFieldsPermission)
                .setTodoTask(todoTask)
                .setActivityNodes(activityNodes);
    }

    /**
     * 构建流程实例的精简列表
     *
     * @param processInstances     流程实例列表
     * @param processDefinitionMap 流程定义Map
     * @return 流程实例精简列表
     */
    default List<BpmProcessInstanceSimpleRespVO> buildProcessInstanceSimpleList(List<HistoricProcessInstance> processInstances,
                                                                                Map<String, ProcessDefinition> processDefinitionMap) {
        return convertList(processInstances, processInstance -> {
            BpmProcessInstanceSimpleRespVO respVO = new BpmProcessInstanceSimpleRespVO();
            respVO.setId(processInstance.getId());
            respVO.setName(processInstance.getName());
            respVO.setCreate(processInstance.getStartTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            // 获取流程定义名称
            MapUtils.findAndThen(processDefinitionMap, processInstance.getProcessDefinitionId(),
                    processDefinition -> respVO.setNameTo(processDefinition.getName()));
            return respVO;
        });
    }

    /**
     * 构建流程实例的精简分页
     *
     * @param pageResult          流程实例分页
     * @param processDefinitionMap 流程定义Map
     * @return 流程实例精简分页
     */
    default PageResult<BpmProcessInstanceSimpleRespVO> buildProcessInstanceSimplePage(PageResult<HistoricProcessInstance> pageResult,
                                                                                     Map<String, ProcessDefinition> processDefinitionMap) {
        if (CollUtil.isEmpty(pageResult.getList())) {
            return PageResult.empty(pageResult.getTotal());
        }
        return new PageResult<>(buildProcessInstanceSimpleList(pageResult.getList(), processDefinitionMap), pageResult.getTotal());
    }

}
