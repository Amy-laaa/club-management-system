package com.club.mapper;

import com.club.entity.Activity;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动表数据访问。
 * remain(剩余名额) 是强一致资源: 扣减/回补全部用条件更新, 不允许"先查后改"。
 */
@Mapper
public interface ActivityMapper {

    @Insert("INSERT INTO t_activity (club_id, venue_app_id, title, act_type, start_time, end_time, location, " +
            "capacity, remain, signup_deadline, intro, status, created_at) " +
            "VALUES (#{clubId}, #{venueAppId}, #{title}, #{actType}, #{startTime}, #{endTime}, #{location}, " +
            "#{capacity}, #{remain}, #{signupDeadline}, #{intro}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "activityId")
    int insert(Activity activity);

    @Select("SELECT * FROM t_activity WHERE activity_id = #{activityId}")
    Activity selectById(Long activityId);

    /** 对外可见活动列表(已发布 1 / 进行中 2), 支持社团与标题关键字筛选 */
    @Select("<script>SELECT * FROM t_activity WHERE status IN (1, 2) " +
            "<if test='clubId != null'> AND club_id = #{clubId}</if>" +
            "<if test='actType != null'> AND act_type = #{actType}</if>" +
            "<if test='keyword != null and keyword.length() > 0'> AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            " ORDER BY start_time DESC LIMIT #{offset}, #{limit}</script>")
    List<Activity> selectPublishedPage(@Param("clubId") Long clubId,
                                       @Param("actType") Integer actType,
                                       @Param("keyword") String keyword,
                                       @Param("offset") int offset,
                                       @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM t_activity WHERE status IN (1, 2) " +
            "<if test='clubId != null'> AND club_id = #{clubId}</if>" +
            "<if test='actType != null'> AND act_type = #{actType}</if>" +
            "<if test='keyword != null and keyword.length() > 0'> AND title LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "</script>")
    long countPublished(@Param("clubId") Long clubId,
                        @Param("actType") Integer actType,
                        @Param("keyword") String keyword);

    /**
     * 同社团同一时段最多一场活动(演示口径)。
     * 判定: 未结束且未被驳回的活动(0待审 1已发布 2进行中)中, 时间区间与待发布活动重叠。
     */
    @Select("SELECT COUNT(*) FROM t_activity WHERE club_id = #{clubId} AND status IN (0, 1, 2) " +
            "AND start_time < #{endTime} AND end_time > #{startTime}")
    int countTimeOverlap(@Param("clubId") Long clubId,
                         @Param("startTime") LocalDateTime startTime,
                         @Param("endTime") LocalDateTime endTime);

    @Select("SELECT COUNT(*) FROM t_activity WHERE club_id = #{clubId} AND status = #{status}")
    long countByClubAndStatus(@Param("clubId") Long clubId, @Param("status") Integer status);

    /** 本社团活动列表(负责人视角, 含待审核与已结束) */
    @Select("SELECT * FROM t_activity WHERE club_id = #{clubId} ORDER BY created_at DESC LIMIT #{offset}, #{limit}")
    List<Activity> selectPageByClub(@Param("clubId") Long clubId,
                                    @Param("offset") int offset,
                                    @Param("limit") int limit);

    /** 待审核公开活动分页(社联管理员) */
    @Select("SELECT * FROM t_activity WHERE status = 0 ORDER BY created_at ASC LIMIT #{offset}, #{limit}")
    List<Activity> selectPendingPage(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_activity WHERE status = 0")
    long countPending();

    /**
     * 防超卖核心: 剩余名额 > 0 且活动处于已发布状态才扣减。
     * 影响行数为 0 说明名额已被并发抢空或活动不可报名。
     */
    @Update("UPDATE t_activity SET remain = remain - 1 " +
            "WHERE activity_id = #{activityId} AND remain > 0 AND status = 1")
    int deductRemain(Long activityId);

    /** 释放名额(取消报名 / 活动取消时回补), 上限不超过 capacity */
    @Update("UPDATE t_activity SET remain = remain + 1 " +
            "WHERE activity_id = #{activityId} AND remain < capacity")
    int increaseRemain(Long activityId);

    /** 报名截止或活动开始前状态推进: 到点自动由已发布转为进行中(惰性刷新) */
    @Update("UPDATE t_activity SET status = 2 WHERE status = 1 AND start_time <= NOW() AND end_time > NOW()")
    int advanceToOngoing();

    /** 结束时间已过 → 已结束 */
    @Update("UPDATE t_activity SET status = 3 WHERE status IN (1, 2) AND end_time <= NOW()")
    int advanceToFinished();

    /** 公开活动审核: 仅待审核状态可流转, 返回影响行数 */
    @Update("UPDATE t_activity SET status = #{toStatus} WHERE activity_id = #{activityId} AND status = 0")
    int auditFromPending(@Param("activityId") Long activityId, @Param("toStatus") int toStatus);

    /** 负责人取消活动: 已发布/进行中 → 已取消 */
    @Update("UPDATE t_activity SET status = 4 WHERE activity_id = #{activityId} AND status IN (1, 2)")
    int cancelById(Long activityId);

    /** 活动取消后把剩余名额回补到"容量 - 已签到人数" */
    @Update("UPDATE t_activity SET remain = capacity - #{checkedIn} WHERE activity_id = #{activityId}")
    int resetRemainAfterCancel(@Param("activityId") Long activityId, @Param("checkedIn") int checkedIn);
}
