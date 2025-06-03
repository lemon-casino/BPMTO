package cn.iocoder.yudao.module.bpm.dal.mysql.task;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 流程实例评论 Mapper
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceCommentMapper extends BaseMapperX<BpmProcessInstanceCommentDO> {

    /**
     * 获取流程实例的评论列表
     *
     * @param processInstanceId 流程实例编号
     * @return 评论列表
     */
    default List<BpmProcessInstanceCommentDO> selectListByProcessInstanceId(String processInstanceId) {
        return selectList(new LambdaQueryWrapperX<BpmProcessInstanceCommentDO>()
                .eq(BpmProcessInstanceCommentDO::getProcessInstanceId, processInstanceId)
                .orderByDesc(BpmProcessInstanceCommentDO::getCreateTime));
    }
} 