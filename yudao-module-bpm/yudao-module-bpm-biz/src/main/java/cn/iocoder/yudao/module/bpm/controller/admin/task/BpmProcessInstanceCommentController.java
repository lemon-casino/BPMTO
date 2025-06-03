package cn.iocoder.yudao.module.bpm.controller.admin.task;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentCreateReqVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.comment.BpmProcessInstanceCommentRespVO;
import cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task.BpmTaskUrgeReqVO;
import cn.iocoder.yudao.module.bpm.convert.task.BpmProcessInstanceCommentConvert;
import cn.iocoder.yudao.module.bpm.dal.dataobject.task.BpmProcessInstanceCommentDO;
import cn.iocoder.yudao.module.bpm.service.task.BpmProcessInstanceCommentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.yudao.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 流程实例评论")
@RestController
@RequestMapping("/bpm/process-instance/comment")
@Validated
public class BpmProcessInstanceCommentController {
    
    @Resource
    private BpmProcessInstanceCommentService commentService;

    @GetMapping("/list")
    @Operation(summary = "获得流程实例的评论列表")
    @Parameter(name = "processInstanceId", description = "流程实例的编号", required = true, example = "1024")
    public CommonResult<List<BpmProcessInstanceCommentRespVO>> getCommentList(
            @RequestParam("processInstanceId") String processInstanceId) {
        List<BpmProcessInstanceCommentDO> list = commentService.getCommentListByProcessInstanceId(processInstanceId);
        return success(BpmProcessInstanceCommentConvert.INSTANCE.convertList(list));
    }

    @PostMapping("/create")
    @Operation(summary = "创建流程实例评论")
    public CommonResult<Long> createComment(@Valid @RequestBody BpmProcessInstanceCommentCreateReqVO createReqVO) {
        Long userId = SecurityFrameworkUtils.getLoginUserId();
        return success(commentService.createComment(userId, createReqVO));
    }

    // 增加流程催办功能
    /**
     * @Description: < >
     * @Param: "void"
     * @Return: {@link  {@link Valid BpmTaskUrgeReqVO reqVO}}
     * @Author: Lemon
     * @CreateTime: 2025/5/22 18:02
     * @Version: 1.0
     */
    @PutMapping("/urge")
    public CommonResult<Boolean> addUrgeTask(@Valid @RequestBody BpmTaskUrgeReqVO reqVO) {
        commentService.addUrgeTask(reqVO);
        return success(true);
    }
}
