package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import lombok.Data;
import lombok.ToString;

/**
 * @Package: cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task
 * @Description: < >
 * @Author: 柠檬果肉
 * @Date: 2025/5/22 18:22
 * @Version V1.0
 */
@Data
@ToString
public class BpmTaskUrgeItemReqVO {
    private Long userId;
    private String processName;
    private String url;
    private String message;
    private String mobile;
}
