package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 流程实例的精简 Response VO")
@Data
public class BpmProcessInstanceSimpleRespVO {

    @Schema(description = "流程实例的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String id;

    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "采购任务运营发布（全平台）")
    private String name;

    @Schema(description = "流程定义名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "采购任务运营发布（全平台）")
    private String nameTo;

    @Schema(description = "流程发起时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime create;
} 