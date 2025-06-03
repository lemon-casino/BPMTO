package cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.type;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 工作时间配置创建/修改 Request VO")
@Data
public class BpmWorkTimeTypeSaveReqVO {

    @Schema(description = "编号", example = "1")
    private Long id;

    @Schema(description = "工作时间类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "工作时间类型不能为空")
    private Integer type;
    @Schema(description = "名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "上班-工时名称")
    @NotNull(message = "名称不能为空")
    private String name;

}