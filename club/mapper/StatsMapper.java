package com.club.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 统计模块数据访问 (只读聚合)。
 *
 * <p>设计说明书 2.2.1: 「统计模块只读聚合视图」——本类不含任何写操作,
 * 也不新建数据表, 全部指标由 t_club / t_membership / t_activity /
 * t_registration / t_venue / t_venue_application 现存数据聚合而来。
 *
 * <p>返回 {@code Map} 时, 键名即 SQL 中的列别名(小驼峰), 便于 Service 直接读取。
 */
@Mapper
public interface StatsMapper {

    // ------------------------------------------------------------------
    // 维度一: 社团活跃度
    // ------------------------------------------------------------------

    /** 社团总数(不含已注销 status = 2, 含待审核) */
    @Select("SELECT COUNT(*) FROM t_club WHERE status <> 2")
    int countClubs();

    /**
     * 各已成立社团的活跃度原料: 正式成员数 + 有效活动数。
     * 归一化与加权在 Service 内完成(便于单元测试, 也避免 SQL 里嵌套子查询取全局最大值)。
     */
    @Select("SELECT c.club_id AS clubId, c.club_name AS clubName, " +
            "(SELECT COUNT(*) FROM t_membership m WHERE m.club_id = c.club_id AND m.status = 1) AS memberCount, " +
            "(SELECT COUNT(*) FROM t_activity a WHERE a.club_id = c.club_id AND a.status <> 4) AS activityCount " +
            "FROM t_club c WHERE c.status = 1 ORDER BY c.club_id")
    List<Map<String, Object>> clubActivityRaw();

    // ------------------------------------------------------------------
    // 维度二: 活动参与率
    // ------------------------------------------------------------------

    /** 有效活动数(不含已取消/被驳回 status = 4) */
    @Select("SELECT COUNT(*) FROM t_activity WHERE status <> 4")
    int countActivities();

    /** 指定时间区间内开始的有效活动数(用于「本月活动数」) */
    @Select("SELECT COUNT(*) FROM t_activity WHERE status <> 4 " +
            "AND start_time >= #{from} AND start_time < #{to}")
    int countActivitiesInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /**
     * 参与率原料: 已发布及之后(1已发布/2进行中/3已结束)活动的
     * 「名额合计」与「已签到人数合计」。
     * 口径(接口契约 3.11): 活动参与率 = 已签到人数 / 名额。
     */
    @Select("SELECT IFNULL(SUM(a.capacity), 0) AS totalCapacity, " +
            "IFNULL(SUM((SELECT COUNT(*) FROM t_registration r " +
            "            WHERE r.activity_id = a.activity_id AND r.status = 1)), 0) AS totalCheckedIn " +
            "FROM t_activity a WHERE a.status IN (1, 2, 3)")
    Map<String, Object> signupRaw();

    // ------------------------------------------------------------------
    // 维度三: 场地使用率
    // ------------------------------------------------------------------

    /** 在用场地数(仅统计 status = 1 可用场地, 与使用率分母口径一致) */
    @Select("SELECT COUNT(*) FROM t_venue WHERE status = 1")
    int countActiveVenues();

    /** 统计窗口内「已通过(status = 1)」的时段占用数, 即使用率的分子 */
    @Select("SELECT COUNT(*) FROM t_venue_application " +
            "WHERE status = 1 AND use_date BETWEEN #{from} AND #{to}")
    int countApprovedSlots(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** 各场地在统计窗口内的已通过时段数(用于场地使用率 TOP 排行) */
    @Select("SELECT v.venue_id AS venueId, v.venue_name AS venueName, " +
            "(SELECT COUNT(*) FROM t_venue_application va " +
            " WHERE va.venue_id = v.venue_id AND va.status = 1 " +
            "   AND va.use_date BETWEEN #{from} AND #{to}) AS usedSlots " +
            "FROM t_venue v WHERE v.status = 1 ORDER BY v.venue_id")
    List<Map<String, Object>> venueSlotUsage(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
