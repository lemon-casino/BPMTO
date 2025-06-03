package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 流程实例评论 Response VO")
@Data
public class BpmProcessInstanceCommentRespVO {

    @Schema(description = "评论编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流程实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private String processInstanceId;

    @Schema(description = "评论用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long userId;

    @Schema(description = "评论用户昵称", requiredMode = Schema.RequiredMode.REQUIRED, example = "芋道源码")
    private String userNickname;

    @Schema(description = "评论用户头像", example = "https://www.iocoder.cn/xxx.jpg")
    private String userAvatar;

    @Schema(description = "评论内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "不错不错，通过！")
    private String content;

    @Schema(description = "评论图片地址数组", example = "[\"https://www.iocoder.cn/xxx.jpg\"]")
    private List<String> picUrls;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
    //@用户
    @Schema(description = "艾特用户", example = "[1]")
    private List<Long> atUserIds;

} 