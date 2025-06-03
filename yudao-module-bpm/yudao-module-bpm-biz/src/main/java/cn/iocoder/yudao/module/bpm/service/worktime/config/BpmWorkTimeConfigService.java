package cn.iocoder.yudao.module.bpm.service.worktime.config;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigBatchSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeConfigDO;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

/**
 * 工作时间配置 Service
 */
public interface BpmWorkTimeConfigService {

    Long createWorkTimeConfig(@Valid BpmWorkTimeConfigSaveReqVO createReqVO);

    /**
     * 批量创建工作时间配置
     *
     * @param batchCreateReqVO 批量创建请求
     * @return 创建的配置ID列表
     */
    List<Long> batchCreateWorkTimeConfig(@Valid BpmWorkTimeConfigBatchSaveReqVO batchCreateReqVO);

    void updateWorkTimeConfig(@Valid BpmWorkTimeConfigSaveReqVO updateReqVO);

    void deleteWorkTimeConfig(Long id);

    BpmWorkTimeConfigDO getWorkTimeConfig(Long id);

    PageResult<BpmWorkTimeConfigDO> getWorkTimeConfigPage(BpmWorkTimeConfigPageReqVO pageReqVO);

    List<BpmWorkTimeConfigDO> getWorkTimeList(Integer type, LocalDate date);
}