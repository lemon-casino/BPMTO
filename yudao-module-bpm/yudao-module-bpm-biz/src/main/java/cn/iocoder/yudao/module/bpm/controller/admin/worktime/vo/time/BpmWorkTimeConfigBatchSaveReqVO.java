package cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "管理后台 - 工作时间配置批量创建 Request VO")
@Data
public class BpmWorkTimeConfigBatchSaveReqVO {

    @Schema(description = "工作时间配置列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "工作时间配置列表不能为空")
    @Valid
    private List<BpmWorkTimeConfigSaveReqVO> configs;

}
