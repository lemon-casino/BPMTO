package cn.iocoder.yudao.module.bpm.controller.admin.worktime.vo.time;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.util.date.DateUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工作时间配置分页 Request VO")
@Data
public class BpmWorkTimeConfigPageReqVO extends PageParam {

    @Schema(description = "工作时间类型", example = "1")
    private Integer type;

    @Schema(description = "日期")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate date;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;
}