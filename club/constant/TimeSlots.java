package com.club.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 场地可预约时段(演示口径: 每天固定 6 个时段)。
 * 与前端「场地 × 日期 × 时段」矩阵的列一一对应, 同时作为
 * 统计模块计算场地使用率的分母口径(场地数 × 时段数 × 统计天数)。
 */
public final class TimeSlots {

    public static final List<String> ALL = Collections.unmodifiableList(Arrays.asList(
            "08:00-10:00",
            "10:00-12:00",
            "14:00-16:00",
            "16:00-18:00",
            "18:00-20:00",
            "20:00-22:00"
    ));

    /** 每日可约时段数(6) */
    public static final int PER_DAY = ALL.size();

    private TimeSlots() {
    }

    /** 校验时段是否合法 */
    public static boolean isValid(String timeSlot) {
        return timeSlot != null && ALL.contains(timeSlot);
    }
}
