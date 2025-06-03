package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - 流程实例的创建 Request VO")
@Data
public class BpmProcessInstanceCreateReqVO {

    @Schema(description = "流程定义的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotEmpty(message = "流程定义编号不能为空")
    private String processDefinitionId;

    @Schema(description = "变量实例（动态表单）")
    private Map<String, Object> variables;

    @Schema(description = "发起人自选审批人 Map", example = "{taskKey1: [1, 2]}")
    private Map<String, List<Long>> startUserSelectAssignees;

    //提供的用户id 如果有填写 则发起人是对应的用户id的身份发起
    @Schema(description = "发起人refreshToken", example = "0084fe4dgt516d3cg516d3cdf5c949ac949682a")
    private String refreshToken="";

}
