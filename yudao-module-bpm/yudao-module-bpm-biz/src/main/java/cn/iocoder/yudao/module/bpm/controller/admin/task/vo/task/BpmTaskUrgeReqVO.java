package cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task;

import lombok.Data;
import lombok.ToString;

import java.util.List;

/**
 * @Package: cn.iocoder.yudao.module.bpm.controller.admin.task.vo.task
 * @Description: < 流程催办功能>
 * @Author: 柠檬果肉
 * @Date: 2025/5/22 18:02
 * @Version V1.0
 */
@Data
@ToString
public class BpmTaskUrgeReqVO {
    private  String processInstanceId;
    private List<BpmTaskUrgeItemReqVO> urgeList;
}