package cn.iocoder.yudao.module.bpm.dal.dataobject.worktime;

import cn.iocoder.yudao.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @Package: cn.iocoder.yudao.module.bpm.dal.dataobject.worktime
 * @Description: < 工时类型>
 * @Author: 柠檬果肉
 * @Date: 2025/6/2 14:58
 * @Version V1.0
 */
@TableName("bpm_work_time_type")
@KeySequence("bpm_work_time_type_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BpmWorkTimeTypeDO extends BaseDO {

    /** 主键 */
    @TableId
    private Long id;
    /** 工作时间类型 */
    private Integer type;
    //类型名称
    private String name;
}
