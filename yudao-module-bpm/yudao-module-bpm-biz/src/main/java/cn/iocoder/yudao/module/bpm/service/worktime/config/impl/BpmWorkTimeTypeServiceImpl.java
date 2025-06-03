package cn.iocoder.yudao.module.bpm.service.worktime.config.impl;


import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.type.BpmWorkTimeTypeSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeTypeDO;
import cn.iocoder.yudao.module.bpm.dal.mysql.worktime.BpmWorkTimeConfigMapper;
import cn.iocoder.yudao.module.bpm.dal.mysql.worktime.BpmWorkTypeConfigMapper;
import cn.iocoder.yudao.module.bpm.service.worktime.config.BpmWorkTimeTypeService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.Collection;
import java.util.Collections;

@Service
@Validated
public class BpmWorkTimeTypeServiceImpl implements BpmWorkTimeTypeService {

    @Resource
    private BpmWorkTypeConfigMapper workTypeConfigMapper;
    @Resource
    private BpmWorkTimeConfigMapper workTimeConfigMapper;
    @Override
    public Long createWorkTimeType(BpmWorkTimeTypeSaveReqVO createReqVO) {
        BpmWorkTimeTypeDO config = BeanUtils.toBean(createReqVO, BpmWorkTimeTypeDO.class);
        workTypeConfigMapper.insert(config);
        return config.getId();
    }

    @Override
    public void updateWorkTimeType(BpmWorkTimeTypeSaveReqVO updateReqVO) {
        validateExists(updateReqVO.getId());
        BpmWorkTimeTypeDO updateObj = BeanUtils.toBean(updateReqVO, BpmWorkTimeTypeDO.class);
        workTypeConfigMapper.updateById(updateObj);
    }

    @Override
    public void deleteWorkTimeType(Long id) {
        validateExists(id);
        workTypeConfigMapper.deleteById(id);
        //判断
        workTimeConfigMapper.deleteByTypeId(id);
    }

    @Override
    public Collection<BpmWorkTimeTypeDO> getWorkTimeType(Long id) {
        if (id == null) {
            return workTypeConfigMapper.selectList();
        }
        BpmWorkTimeTypeDO workType = workTypeConfigMapper.selectById(id);
        return workType != null ? Collections.singletonList(workType) : Collections.emptyList();
    }

    private void validateExists(Long id) {
        if (id == null) {
            return;
        }
        if (workTypeConfigMapper.selectById(id) == null) {
            throw new IllegalArgumentException("work time config not found");
        }
    }

}