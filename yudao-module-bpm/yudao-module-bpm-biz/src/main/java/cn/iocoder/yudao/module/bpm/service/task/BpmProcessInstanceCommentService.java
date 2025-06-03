package cn.iocoder.yudao.module.bpm.service.task;

import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskUrgeReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * 流程实例评论 Service 接口
 *
 * @author 芋道源码
 */
public interface BpmProcessInstanceCommentService {

    /**
     * 获得流程实例的评论列表
     *
     * @param processInstanceId 流程实例编号
     * @return 评论列表
     */
    List<BpmProcessInstanceCommentDO> getCommentListByProcessInstanceId(String processInstanceId);

    /**
     * 创建流程实例评论
     *
     * @param userId 用户编号
     * @param createReqVO 创建信息
     * @return 评论编号
     */
    Long createComment(Long userId, BpmProcessInstanceCommentCreateReqVO createReqVO);

    void addUrgeTask(@Valid BpmTaskUrgeReqVO reqVO);
} 