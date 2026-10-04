package com.club.service;

import com.club.constant.TimeSlots;
import com.club.mapper.StatsMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据统计服务。用例 bu_查看统计报表 (bur_viewStats)。
 *
 * <p>设计说明书 2.2.5 / 表 2-8: 统计模块提供社团活跃度、活动参与率、场地使用率
 * 三类聚合指标, 以卡片式看板呈现。本服务只读, 不产生任何副作用。
 *
 * <h3>指标口径 (与接口契约 §3.11 保持一致)</h3>
 * <ul>
 *   <li><b>社团活跃度</b>: 成员规模与活动频次加权, 各项按全体最大值归一化到 100 分。<br>
 *       得分 = 100 × (0.6 × 成员数/最大成员数 + 0.4 × 活动数/最大活动数)；
 *       activeClubs = 得分 ≥ {@value #ACTIVE_SCORE_THRESHOLD} 的已成立社团数。</li>
 *   <li><b>活动参与率</b>: 已签到人数 / 名额, 统计范围为已发布及之后的活动(1/2/3)。</li>
 *   <li><b>场地使用率</b>: 已通过时段数 / (在用场地数 × 每日时段数 × 统计天数),<br>
 *       统计窗口 = 今天起 {@value #USAGE_WINDOW_DAYS} 天(未来一个月的预订占用热度)。</li>
 * </ul>
 *
 * <p>注: 接口契约中的 avgRating(活动平均评分) 因本期数据库未设评价表而恒为 null,
 * 字段保留以便后续扩展(评价功能落地后只需补一张 t_rating 并填充此处)。
 */
@Service
public class StatsService {

    /** 场地使用率统计窗口天数: 今天起未来 30 天 */
    public static final int USAGE_WINDOW_DAYS = 30;
    /** 活跃社团判定阈值(满分 100) */
    public static final int ACTIVE_SCORE_THRESHOLD = 60;
    /** 活跃度权重: 成员规模 60% + 活动频次 40% */
    private static final double WEIGHT_MEMBER = 0.6;
    private static final double WEIGHT_ACTIVITY = 0.4;

    private final StatsMapper statsMapper;

    public StatsService(StatsMapper statsMapper) {
        this.statsMapper = statsMapper;
    }

    /** 统计看板总入口: 一次性返回社团 / 活动 / 场地三组卡片数据 */
    public Map<String, Object> overview() {
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("clubStats", clubStats());
        data.put("activityStats", activityStats());
        data.put("venueStats", venueStats());
        return data;
    }

    // ==================================================================
    // 维度一: 社团活跃度
    // ==================================================================

    public Map<String, Object> clubStats() {
        List<Map<String, Object>> raw = statsMapper.clubActivityRaw();

        // 1) 取各维度最大值, 用于归一化
        int maxMembers = 0;
        int maxActivities = 0;
        for (Map<String, Object> row : raw) {
            maxMembers = Math.max(maxMembers, toInt(row.get("memberCount")));
            maxActivities = Math.max(maxActivities, toInt(row.get("activityCount")));
        }

        // 2) 逐社团算分
        List<Map<String, Object>> ranked = new ArrayList<Map<String, Object>>();
        long memberSum = 0;
        int activeClubs = 0;
        for (Map<String, Object> row : raw) {
            int members = toInt(row.get("memberCount"));
            int activities = toInt(row.get("activityCount"));
            memberSum += members;
            double score = activityScore(members, maxMembers, activities, maxActivities);
            if (score >= ACTIVE_SCORE_THRESHOLD) {
                activeClubs++;
            }
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("clubId", row.get("clubId"));
            item.put("clubName", row.get("clubName"));
            item.put("memberCount", members);
            item.put("activityCount", activities);
            item.put("score", round(score, 1));
            ranked.add(item);
        }

        // 3) 按得分降序取前三(得分相同则成员多的在前)
        ranked.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                int byScore = Double.compare(toDouble(b.get("score")), toDouble(a.get("score")));
                return byScore != 0 ? byScore
                        : Integer.compare(toInt(b.get("memberCount")), toInt(a.get("memberCount")));
            }
        });
        List<Map<String, Object>> top3 = head(ranked, 3);

        Map<String, Object> clubStats = new HashMap<String, Object>();
        clubStats.put("total", statsMapper.countClubs());
        clubStats.put("activeClubs", activeClubs);
        clubStats.put("avgMembers", raw.isEmpty() ? 0.0 : round((double) memberSum / raw.size(), 1));
        clubStats.put("top3", top3);
        return clubStats;
    }

    /**
     * 活跃度得分: 成员规模(60%)与活动频次(40%)按全体最大值归一化后加权, 满分 100。
     * 拆成独立静态方法便于单元测试直接验证边界(全体为 0 时不发生除零)。
     */
    static double activityScore(int members, int maxMembers, int activities, int maxActivities) {
        double memberPart = maxMembers <= 0 ? 0.0 : (double) members / maxMembers;
        double activityPart = maxActivities <= 0 ? 0.0 : (double) activities / maxActivities;
        return 100.0 * (WEIGHT_MEMBER * memberPart + WEIGHT_ACTIVITY * activityPart);
    }

    // ==================================================================
    // 维度二: 活动参与率
    // ==================================================================

    public Map<String, Object> activityStats() {
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime nextMonthStart = monthStart.plusMonths(1);

        Map<String, Object> signup = statsMapper.signupRaw();
        int totalCapacity = toInt(signup == null ? null : signup.get("totalCapacity"));
        int totalCheckedIn = toInt(signup == null ? null : signup.get("totalCheckedIn"));
        double signupRate = totalCapacity <= 0 ? 0.0 : (double) totalCheckedIn / totalCapacity;

        Map<String, Object> activityStats = new HashMap<String, Object>();
        activityStats.put("total", statsMapper.countActivities());
        activityStats.put("monthCount", statsMapper.countActivitiesInRange(monthStart, nextMonthStart));
        activityStats.put("avgSignupRate", round(signupRate, 2));
        activityStats.put("capacityTotal", totalCapacity);      // 口径明细, 便于前端展示 "41/53 人"
        activityStats.put("checkedInTotal", totalCheckedIn);
        activityStats.put("avgRating", null);                   // 本期无评价表, 预留字段
        return activityStats;
    }

    // ==================================================================
    // 维度三: 场地使用率
    // ==================================================================

    public Map<String, Object> venueStats() {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(USAGE_WINDOW_DAYS - 1);

        int venueCount = statsMapper.countActiveVenues();
        int usedSlots = statsMapper.countApprovedSlots(from, to);
        int slotsTotal = venueCount * TimeSlots.PER_DAY * USAGE_WINDOW_DAYS;
        double usageRate = slotsTotal <= 0 ? 0.0 : (double) usedSlots / slotsTotal;

        // 各场地已通过时段数 → 降序取前三
        List<Map<String, Object>> usage = statsMapper.venueSlotUsage(from, to);
        List<Map<String, Object>> normalized = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> row : usage) {
            Map<String, Object> item = new HashMap<String, Object>();
            item.put("venueId", row.get("venueId"));
            item.put("venueName", row.get("venueName"));
            item.put("usedSlots", toInt(row.get("usedSlots")));
            normalized.add(item);
        }
        normalized.sort(new Comparator<Map<String, Object>>() {
            @Override
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                return Integer.compare(toInt(b.get("usedSlots")), toInt(a.get("usedSlots")));
            }
        });

        Map<String, Object> venueStats = new HashMap<String, Object>();
        venueStats.put("total", venueCount);
        venueStats.put("usageRate", round(usageRate, 4));
        venueStats.put("usedSlots", usedSlots);                 // 口径明细: 分子
        venueStats.put("slotsTotal", slotsTotal);               // 口径明细: 分母
        venueStats.put("windowDays", USAGE_WINDOW_DAYS);
        venueStats.put("top3", head(normalized, 3));
        return venueStats;
    }

    // ==================================================================
    // 工具方法
    // ==================================================================

    /** 取前 n 条(不足则全取) */
    private static List<Map<String, Object>> head(List<Map<String, Object>> list, int n) {
        return new ArrayList<Map<String, Object>>(list.subList(0, Math.min(n, list.size())));
    }

    /** 保留 scale 位小数, 四舍五入 */
    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    /** MyBatis 聚合结果可能是 Long/Integer/BigDecimal, 统一转 int */
    private static int toInt(Object v) {
        return v instanceof Number ? ((Number) v).intValue() : 0;
    }

    private static double toDouble(Object v) {
        return v instanceof Number ? ((Number) v).doubleValue() : 0.0;
    }
}
