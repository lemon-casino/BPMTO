package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 流程实例状态统计 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BpmProcessInstanceStatsRespVO {

    @Schema(description = "审批中的流程数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long running;

    @Schema(description = "审批通过的流程数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "20")
    private Long approve;

    @Schema(description = "审批不通过的流程数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Long reject;

    @Schema(description = "已取消的流程数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Long cancel;

} 