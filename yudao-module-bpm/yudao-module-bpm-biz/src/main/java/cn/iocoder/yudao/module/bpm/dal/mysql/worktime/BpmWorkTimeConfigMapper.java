package cn.iocoder.yudao.module.bpm.dal.mysql.worktime;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface BpmWorkTimeConfigMapper extends BaseMapperX<BpmWorkTimeConfigDO> {

    default PageResult<BpmWorkTimeConfigDO> selectPage(BpmWorkTimeConfigPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<BpmWorkTimeConfigDO>()
                .eqIfPresent(BpmWorkTimeConfigDO::getType, reqVO.getType())
                .eqIfPresent(BpmWorkTimeConfigDO::getDate, reqVO.getDate())
                .betweenIfPresent(BpmWorkTimeConfigDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(BpmWorkTimeConfigDO::getId));
    }

    default List<BpmWorkTimeConfigDO> selectListByDateAndType(LocalDate date, Integer type) {
        return selectList(new LambdaQueryWrapperX<BpmWorkTimeConfigDO>()
                .eqIfPresent(BpmWorkTimeConfigDO::getDate, date)
                .eqIfPresent(BpmWorkTimeConfigDO::getType, type)
                .orderByAsc(BpmWorkTimeConfigDO::getStartTime));
    }

    default int deleteByTypeId(Long type) {
        return    deleteByIds(selectList(new LambdaQueryWrapperX<BpmWorkTimeConfigDO>()
                .eq(BpmWorkTimeConfigDO::getType, type))
                .stream().map(BpmWorkTimeConfigDO::getId).toList());
    }
}