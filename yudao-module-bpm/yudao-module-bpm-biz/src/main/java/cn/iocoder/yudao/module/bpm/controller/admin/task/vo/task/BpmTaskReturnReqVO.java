package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.ToString;

@Schema(description = "管理后台 - 退回流程任务的 Request VO")
@Data
@ToString
public class BpmTaskReturnReqVO {

    @Schema(description = "任务编号taskId", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "任务编号不能为空")
    private String id;
    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private String processInstanceId;

    @Schema(description = "退回到的任务 Key", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotEmpty(message = "退回到的任务 Key 不能为空")
    private String targetTaskDefinitionKey;

    @Schema(description = "退回意见", requiredMode = Schema.RequiredMode.REQUIRED, example = "我就是想驳回")
    @NotEmpty(message = "退回意见不能为空")
    private String reason;
    @Schema(description = "是否是管理员", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private  Boolean managerial=false;

}
