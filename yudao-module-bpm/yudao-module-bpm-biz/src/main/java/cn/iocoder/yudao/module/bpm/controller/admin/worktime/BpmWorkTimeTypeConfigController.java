package cn.iocoder.yudao.module.bpm.controller.admin.worktime;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.BeanUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigBatchSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigPageReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time.BpmWorkTimeConfigSaveReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.type.BpmWorkTimeTypeSaveReqVO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeConfigDO;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeTypeDO;
import cn.iocoder.yudao.module.bpm.service.worktime.config.BpmWorkTimeConfigService;
import cn.iocoder.yudao.module.bpm.service.worktime.config.BpmWorkTimeTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 工作时间配置")
@RestController
@RequestMapping("/bpm/work-time-config")
@Validated
public class BpmWorkTimeTypeConfigController {

    @Resource
    private BpmWorkTimeConfigService workTimeConfigService;
    @Resource
    private BpmWorkTimeTypeService worTypeService;

    @PostMapping("/create")
    @Operation(summary = "创建工作时间配置")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:create')")
    public CommonResult<Long> createWorkTimeConfig(@Valid @RequestBody BpmWorkTimeConfigSaveReqVO createReqVO) {
        return success(workTimeConfigService.createWorkTimeConfig(createReqVO));
    }

    @PostMapping("/batch-create")
    @Operation(summary = "批量创建工作时间配置")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:create')")
    public CommonResult<List<Long>> batchCreateWorkTimeConfig(@Valid @RequestBody BpmWorkTimeConfigBatchSaveReqVO batchCreateReqVO) {
        return success(workTimeConfigService.batchCreateWorkTimeConfig(batchCreateReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新工作时间配置")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:update')")
    public CommonResult<Boolean> updateWorkTimeConfig(@Valid @RequestBody BpmWorkTimeConfigSaveReqVO updateReqVO) {
        workTimeConfigService.updateWorkTimeConfig(updateReqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工作时间配置")
    @Parameter(name = "id", description = "编号", required = true)
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:delete')")
    public CommonResult<Boolean> deleteWorkTimeConfig(@RequestParam("id") Long id) {
        workTimeConfigService.deleteWorkTimeConfig(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得工作时间配置")
    @Parameter(name = "id", description = "编号", required = true)
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:query')")
    public CommonResult<BpmWorkTimeConfigRespVO> getWorkTimeConfig(@RequestParam("id") Long id) {
        BpmWorkTimeConfigDO config = workTimeConfigService.getWorkTimeConfig(id);
        return success(BeanUtils.toBean(config, BpmWorkTimeConfigRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得工作时间配置分页")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:query')")
    public CommonResult<PageResult<BpmWorkTimeConfigRespVO>> getWorkTimeConfigPage(@Valid BpmWorkTimeConfigPageReqVO pageVO) {
        PageResult<BpmWorkTimeConfigDO> pageResult = workTimeConfigService.getWorkTimeConfigPage(pageVO);
        return success(BeanUtils.toBean(pageResult, BpmWorkTimeConfigRespVO.class));
    }
    //
    @PostMapping("/create-type")
    @Operation(summary = "增加类型配置")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:create')")
    public CommonResult<Long> createWorkTimeType(@Valid @RequestBody BpmWorkTimeTypeSaveReqVO createTypeDVO) {
        return success(worTypeService.createWorkTimeType(createTypeDVO));
    }
    //修改类型名称
    @PutMapping("/update-type")
    @Operation(summary = "更新类型配置")
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:update')")
    public CommonResult<Boolean> updateWorkTimeType(@Valid @RequestBody BpmWorkTimeTypeSaveReqVO updateReqVO) {
        worTypeService.updateWorkTimeType(updateReqVO);
        return success(true);
    }
    //
    @DeleteMapping("/delete-type")
    @Operation(summary = "删除类型配置")
    @Parameter(name = "id", description = "编号", required = true)
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:delete')")
    public CommonResult<Boolean> deleteWorkTimeType(@RequestParam("id") Long id) {
        worTypeService.deleteWorkTimeType(id);
        return success(true);
    }
    //查询所有的类型配置
    @GetMapping("/get-type")
    @Operation(summary = "获得类型配置")
    @Parameter(name = "id", description = "编号", required = true)
//    @PreAuthorize("@ss.hasPermission('bpm:work-time-config:query')")
    public CommonResult<Collection<BpmWorkTimeTypeDO>> getWorkTimeType(@RequestParam(name="id",required = false) Long id) {
        return success(worTypeService.getWorkTimeType(id));
    }
}