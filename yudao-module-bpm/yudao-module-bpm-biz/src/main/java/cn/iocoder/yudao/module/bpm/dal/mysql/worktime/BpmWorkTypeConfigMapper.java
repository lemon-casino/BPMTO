package cn.iocoder.yudao.module.bpm.dal.mysql.worktime;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeTypeDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BpmWorkTypeConfigMapper extends BaseMapperX<BpmWorkTimeTypeDO> {
}