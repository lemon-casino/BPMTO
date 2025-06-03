package cn.iocoder.yudao.module.bpm.convert.task;

import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentRespVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

/**
 * 流程实例评论 Convert
 *
 * @author 芋道源码
 */
@Mapper
public interface BpmProcessInstanceCommentConvert {

    BpmProcessInstanceCommentConvert INSTANCE = Mappers.getMapper(BpmProcessInstanceCommentConvert.class);

    /**
     * 将 DO 转化为 VO
     *
     * @param bean DO
     * @return VO
     */
    BpmProcessInstanceCommentRespVO convert(BpmProcessInstanceCommentDO bean);

    /**
     * 将 DO 列表转化为 VO 列表
     *
     * @param list DO 列表
     * @return VO 列表
     */
    List<BpmProcessInstanceCommentRespVO> convertList(List<BpmProcessInstanceCommentDO> list);

    /**
     * 将 CreateReqVO 转化为 DO
     *
     * @param bean CreateReqVO
     * @return DO
     */
    default BpmProcessInstanceCommentDO convert(BpmProcessInstanceCommentCreateReqVO bean) {
        return BeanUtils.toBean(bean, BpmProcessInstanceCommentDO.class);
    }

} 