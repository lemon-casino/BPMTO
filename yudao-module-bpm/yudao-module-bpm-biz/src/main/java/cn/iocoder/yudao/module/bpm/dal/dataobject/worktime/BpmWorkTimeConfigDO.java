package cn.iocoder.yudao.module.bpm.dal.dataobject.worktime;


import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 工作时间配置
 */
@TableName("bpm_work_time_config")
@KeySequence("bpm_work_time_config_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmWorkTimeConfigDO extends BaseDO {

    /** 主键 */
    @TableId
    private Long id;
    /** 工作时间类型 */
    private Integer type;
    /** 日期 */
    private LocalDate date;    /** 开始时间 */
    private LocalTime startTime;
    /** 结束时间 */
    private LocalTime endTime;

}