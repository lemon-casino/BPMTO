package cn.iocoder.yudao.module.bpm.service.worktime;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 工作时间计算 Service
 */
public interface BpmWorkTimeService {

    /**
     * 根据开始时间、持续时长及工作时间类型，计算结束时间
     *
     * @param startTime 开始时间
     * @param duration  持续时长
     * @param workTimeType 工作时间类型，允许为空
     * @return 结束时间
     */
    LocalDateTime calculateDueTime(LocalDateTime startTime, Duration duration, Integer workTimeType);
}