package cn.iocoder.yudao.module.bpm.service.worktime.config;

import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.type.BpmWorkTimeTypeSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeTypeDO;
import jakarta.validation.Valid;

import java.util.Collection;

/**
 * 工作时间配置 Service
 */
public interface BpmWorkTimeTypeService {

    Long createWorkTimeType(@Valid BpmWorkTimeTypeSaveReqVO createReqVO);

    void updateWorkTimeType(@Valid BpmWorkTimeTypeSaveReqVO updateReqVO);

    void deleteWorkTimeType(Long id);

    Collection<BpmWorkTimeTypeDO> getWorkTimeType(Long id);
/*
    BpmWorkTimeConfigDO getWorkTimeConfig(Long id);

    PageResult<BpmWorkTimeConfigDO> getWorkTimeConfigPage(BpmWorkTimeTypeSaveReqVO pageReqVO);

    List<BpmWorkTimeConfigDO> getWorkTimeList(Integer type, LocalDate date);*/
}