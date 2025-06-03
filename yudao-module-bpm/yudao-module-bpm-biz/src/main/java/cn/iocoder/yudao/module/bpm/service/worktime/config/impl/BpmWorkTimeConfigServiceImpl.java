package cn.iocoder.yudao.module.bpm.service.worktime.config.impl;


import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigBatchSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigPageReqVO;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeConfigDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.worktime.BpmWorkTimeConfigMapper;
import cn.iocoder.yudao.module.bpm.service.worktime.config.BpmWorkTimeConfigService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@Validated
public class BpmWorkTimeConfigServiceImpl implements BpmWorkTimeConfigService {

    @Resource
    private BpmWorkTimeConfigMapper workTimeConfigMapper;

    @Override
    public Long createWorkTimeConfig(BpmWorkTimeConfigSaveReqVO createReqVO) {
        BpmWorkTimeConfigDO config = BeanUtils.toBean(createReqVO, BpmWorkTimeConfigDO.class);
        workTimeConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreateWorkTimeConfig(BpmWorkTimeConfigBatchSaveReqVO batchCreateReqVO) {
        List<Long> resultIds = new ArrayList<>();

        // 验证数据
        validateBatchCreateData(batchCreateReqVO);

        // 批量创建
        for (BpmWorkTimeConfigSaveReqVO createReqVO : batchCreateReqVO.getConfigs()) {
            BpmWorkTimeConfigDO config = BeanUtils.toBean(createReqVO, BpmWorkTimeConfigDO.class);
            workTimeConfigMapper.insert(config);
            resultIds.add(config.getId());
        }

        return resultIds;
    }

    /**
     * 验证批量创建数据
     */
    private void validateBatchCreateData(BpmWorkTimeConfigBatchSaveReqVO batchCreateReqVO) {
        if (batchCreateReqVO.getConfigs() == null || batchCreateReqVO.getConfigs().isEmpty()) {
            throw new IllegalArgumentException("工作时间配置列表不能为空");
        }

        // 验证每个配置项
        for (BpmWorkTimeConfigSaveReqVO config : batchCreateReqVO.getConfigs()) {
            if (config.getStartTime() != null && config.getEndTime() != null
                && config.getStartTime().isAfter(config.getEndTime())) {
                throw new IllegalArgumentException("开始时间不能晚于结束时间");
            }
        }
    }

    @Override
    public void updateWorkTimeConfig(BpmWorkTimeConfigSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        BpmWorkTimeConfigDO updateObj = BeanUtils.toBean(updateReqVO, BpmWorkTimeConfigDO.class);
        workTimeConfigMapper.updateById(updateObj);
    }

    @Override
    public void deleteWorkTimeConfig(Long id) {
        validateExists(id);
        workTimeConfigMapper.deleteById(id);
    }

    private void validateExists(Long id) {
        if (id == null) {
            return;
        }
        if (workTimeConfigMapper.selectById(id) == null) {
            throw new IllegalArgumentException("work time config not found");
        }
    }

    @Override
    public BpmWorkTimeConfigDO getWorkTimeConfig(Long id) {
        return workTimeConfigMapper.selectById(id);
    }

    @Override
    public PageResult<BpmWorkTimeConfigDO> getWorkTimeConfigPage(BpmWorkTimeConfigPageReqVO pageReqVO) {
        return workTimeConfigMapper.selectPage(pageReqVO);
    }

    @Override
    public List<BpmWorkTimeConfigDO> getWorkTimeList(Integer type, LocalDate date) {
        return workTimeConfigMapper.selectListByDateAndType(date, type);
    }
}