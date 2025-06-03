package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @Package: cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task
 * @Description: < >
 * @Author: 柠檬果肉
 * @Date: 2025/5/27 11:11
 * @Version V1.0
 */

@Schema(description = "管理后台 - 运行中的流程任务 Response VO")
@Data
public class BpmRunningProcessTaskRespVO {

    @Schema(description = "流程实例编号", example = "1024")
    private String id;

    @Schema(description = "流程实例名称", example = "请假流程")
    private String name;

    @Schema(description = "流程开始时间")
    private LocalDateTime startTime;

    @Schema(description = "节点任务列表")
    private List<NodeTask> nodes;

    //流程创建者用户id
    private  Long creatorUserId;
    //创建者的手机号
    private String creatorMobile;

    @Data
    public static class NodeTask {
        @Schema(description = "节点编号", example = "Activity_1")
        private String id;

        @Schema(description = "节点名称", example = "审批")
        private String name;

        @Schema(description = "参与的用户")
        private List<UserTaskWrapper> users;
    }
    @Data
    public static class UserTaskWrapper  {
        @Schema(description = "用户")
        private AdminUserRespDTO user;
        @Schema(description = "任务id", example = "taskId")
        private String taskId;
        @Schema(description = "任务超时时间")
        private LocalDateTime dueTime;
    }
}