package com.club.mapper;

import com.club.entity.VenueApplication;
import org.apache.ibatis.annotations.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 场地申请单数据访问。
 * 冲突判定口径: 同一场地 + 同一日期 + 同一时段, 状态为 0待审核 或 1已通过 即视为占用。
 * 唯一键 uk_venue_slot_status(venue_id, use_date, time_slot, status) 作为并发兜底。
 */
@Mapper
public interface VenueApplicationMapper {

    @Insert("INSERT INTO t_venue_application (venue_id, activity_id, applicant_id, use_date, time_slot, purpose, status, created_at) " +
            "VALUES (#{venueId}, #{activityId}, #{applicantId}, #{useDate}, #{timeSlot}, #{purpose}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "appId")
    int insert(VenueApplication app);

    @Select("SELECT * FROM t_venue_application WHERE app_id = #{appId}")
    VenueApplication selectById(Long appId);

    /** 同场地同日期同时段的有效占用数(0待审 1已通过) */
    @Select("SELECT COUNT(*) FROM t_venue_application " +
            "WHERE venue_id = #{venueId} AND use_date = #{useDate} AND time_slot = #{timeSlot} " +
            "AND status IN (0, 1)")
    int countOccupied(@Param("venueId") Long venueId,
                      @Param("useDate") LocalDate useDate,
                      @Param("timeSlot") String timeSlot);

    /** 某日期全部有效占用(用于场地×时段矩阵置灰) */
    @Select("SELECT * FROM t_venue_application " +
            "WHERE use_date = #{useDate} AND status IN (0, 1)")
    List<VenueApplication> selectOccupiedByDate(LocalDate useDate);

    @Select("SELECT * FROM t_venue_application WHERE applicant_id = #{applicantId} ORDER BY created_at DESC")
    List<VenueApplication> selectMine(Long applicantId);

    @Select("SELECT COUNT(*) FROM t_venue_application WHERE status = #{status}")
    long countByStatus(Integer status);

    @Select("SELECT * FROM t_venue_application WHERE status = #{status} ORDER BY created_at ASC LIMIT #{offset}, #{limit}")
    List<VenueApplication> selectPageByStatus(@Param("status") Integer status,
                                              @Param("offset") int offset,
                                              @Param("limit") int limit);

    /** 条件更新: 仅当仍处于待审核(0)时才允许流转, 返回影响行数 */
    @Update("UPDATE t_venue_application SET status = #{toStatus}, audit_remark = #{auditRemark} " +
            "WHERE app_id = #{appId} AND status = 0")
    int updateStatusFromPending(@Param("appId") Long appId,
                                @Param("toStatus") int toStatus,
                                @Param("auditRemark") String auditRemark);
}
