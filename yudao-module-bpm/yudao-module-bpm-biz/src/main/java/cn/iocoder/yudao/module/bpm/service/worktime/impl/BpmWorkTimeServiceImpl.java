package cn.iocoder.yudao.module.bpm.service.worktime.impl;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.module.bpm.dal.dataobject.worktime.BpmWorkTimeConfigDO;
import cn.iocoder.yudao.module.bpm.service.worktime.BpmWorkTimeService;
import cn.iocoder.yudao.module.bpm.service.worktime.config.BpmWorkTimeConfigService;
import cn.iocoder.yudao.module.infra.api.config.ConfigApi;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 工作时间计算实现
 */
@Service
@Slf4j
public class BpmWorkTimeServiceImpl implements BpmWorkTimeService {

    @Resource
    private ConfigApi configApi;

    @Resource
    private BpmWorkTimeConfigService workTimeConfigService;

    private static final List<LocalTime[]> DEFAULT_RANGES = Arrays.asList(
            new LocalTime[]{LocalTime.of(8, 0), LocalTime.of(12, 0)},
            new LocalTime[]{LocalTime.of(13, 0), LocalTime.of(18, 0)}
    );

    @Override
    public LocalDateTime calculateDueTime(LocalDateTime startTime, Duration duration, Integer workTimeType) {
        if (startTime == null || duration == null) {
            log.warn("[calculateDueTime][参数为空: startTime={}, duration={}]", startTime, duration);
            return null;
        }
        
        log.info("[calculateDueTime][开始计算工作时间: 开始时间={}, 持续时长={}秒, 工作时间类型={}]", 
                startTime, duration.toSeconds(), workTimeType);
        
        long seconds = duration.toSeconds();
        // 保持秒级以下的精度（纳秒）
        long nanos = duration.toNanos() % 1_000_000_000L;
        LocalDateTime cursor = startTime;
        int dayCount = 0; // 防止无限循环
        
        while (seconds > 0 && dayCount < 365) { // 最多计算一年
            List<LocalTime[]> ranges = getRanges(workTimeType, cursor.toLocalDate());
            
            log.debug("[calculateDueTime][第{}天计算: 日期={}, 剩余秒数={}, 工作时间段数={}]", 
                    dayCount + 1, cursor.toLocalDate(), seconds, ranges.size());
            
            boolean foundWorkTime = false;
            for (LocalTime[] r : ranges) {
                LocalDateTime rangeStart = LocalDateTime.of(cursor.toLocalDate(), r[0]);
                LocalDateTime rangeEnd = LocalDateTime.of(cursor.toLocalDate(), r[1]);
                
                // 如果当前时间在工作时间段之前，调整到工作时间开始
                if (cursor.isBefore(rangeStart)) {
                    cursor = rangeStart;
                    log.debug("[calculateDueTime][调整到工作时间开始: {}]", cursor);
                }
                
                // 如果当前时间在当前工作时间段之后，跳过这个时间段
                if (cursor.isAfter(rangeEnd)) {
                    log.debug("[calculateDueTime][跳过已过时间段: {} - {}]", rangeStart, rangeEnd);
                    continue;
                }
                
                // 计算在当前时间段内可用的时间
                long avail = Math.min(seconds, Duration.between(cursor, rangeEnd).toSeconds());
                if (avail > 0) {
                    foundWorkTime = true;
                    cursor = cursor.plusSeconds(avail);
                    seconds -= avail;
                    
                    log.debug("[calculateDueTime][在时间段{}中消耗{}秒，当前时间={}, 剩余={}秒]", 
                            rangeStart + "-" + rangeEnd, avail, cursor, seconds);
                }
                
                if (seconds <= 0) {
                    break;
                }
            }
            
            // 如果在当前日期没有找到工作时间或者剩余时间没有被消耗完，移动到下一天
            if (seconds > 0) {
                cursor = LocalDateTime.of(cursor.toLocalDate().plusDays(1), LocalTime.MIN);
                dayCount++;
                log.debug("[calculateDueTime][移动到下一天: {}，已计算{}天]", cursor.toLocalDate(), dayCount);
            }
        }
        
        if (dayCount >= 365) {
            log.error("[calculateDueTime][计算超过365天限制，可能存在配置问题]");
            return null;
        }
        
        // 添加剩余的纳秒精度
        if (nanos > 0) {
            cursor = cursor.plusNanos(nanos);
            log.debug("[calculateDueTime][添加纳秒精度: {}纳秒，最终时间={}]", nanos, cursor);
        }
        
        log.info("[calculateDueTime][工作时间计算完成: 最终时间={}，共计算{}天]", cursor, dayCount);
        return cursor;
    }

    private List<LocalTime[]> getRanges(Integer workTimeType, LocalDate date) {
        List<BpmWorkTimeConfigDO> list = workTimeConfigService.getWorkTimeList(workTimeType, date);
        
        if (CollUtil.isEmpty(list)) {
            log.debug("[getRanges][日期{}类型{}无配置，使用默认工作时间]", date, workTimeType);
            return DEFAULT_RANGES;
        }
        
        List<LocalTime[]> ranges = new ArrayList<>(list.size());
        for (BpmWorkTimeConfigDO item : list) {
            ranges.add(new LocalTime[]{item.getStartTime(), item.getEndTime()});
        }
        
        log.debug("[getRanges][日期{}类型{}找到{}个工作时间段]", date, workTimeType, ranges.size());
        return ranges;
    }
}