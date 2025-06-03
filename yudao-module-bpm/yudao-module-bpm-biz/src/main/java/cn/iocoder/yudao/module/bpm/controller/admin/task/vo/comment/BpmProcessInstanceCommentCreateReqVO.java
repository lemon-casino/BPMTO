package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 流程实例评论创建 Request VO")
@Data
public class BpmProcessInstanceCommentCreateReqVO {

    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotEmpty(message = "流程实例编号不能为空")
    private String processInstanceId;

    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "不错不错，通过！")
    @NotEmpty(message = "评论内容不能为空")
    private String content;

    @Schema(description = "评论图片地址数组", example = "[\"https://www.iocoder.cn/xxx.jpg\"]")
    private List<String> picUrls;
    @Schema(description = "艾特用户", example = "[1, 2]")
    private List<Long> atUserIds;
    @Schema(description = "流程名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "请假申请")
    @NotEmpty(message = "流程名称不能为空")
    private String processName;
} 