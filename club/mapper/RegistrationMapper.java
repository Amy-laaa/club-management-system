package com.club.mapper;

import com.club.entity.Registration;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 报名记录数据访问。
 * 防重放: 签到用条件更新(status=0 才可核销), 一凭证仅可核销一次。
 * 防重复报名: 唯一键 uk_act_user(activity_id, user_id) 兜底。
 */
@Mapper
public interface RegistrationMapper {

    @Insert("INSERT INTO t_registration (activity_id, user_id, activity_title, start_time, voucher_code, status, created_at) " +
            "VALUES (#{activityId}, #{userId}, #{activityTitle}, #{startTime}, #{voucherCode}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "regId")
    int insert(Registration reg);

    @Select("SELECT * FROM t_registration WHERE reg_id = #{regId}")
    Registration selectById(Long regId);

    @Select("SELECT * FROM t_registration WHERE activity_id = #{activityId} AND user_id = #{userId}")
    Registration selectByActivityAndUser(@Param("activityId") Long activityId, @Param("userId") Long userId);

    @Select("SELECT * FROM t_registration WHERE voucher_code = #{voucherCode}")
    Registration selectByVoucher(String voucherCode);

    @Select("SELECT * FROM t_registration WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<Registration> selectMine(Long userId);

    @Select("SELECT * FROM t_registration WHERE activity_id = #{activityId} AND status = #{status} ORDER BY created_at ASC")
    List<Registration> selectByActivityAndStatus(@Param("activityId") Long activityId,
                                                 @Param("status") Integer status);

    @Select("SELECT COUNT(*) FROM t_registration WHERE activity_id = #{activityId} AND status = #{status}")
    int countByActivityAndStatus(@Param("activityId") Long activityId, @Param("status") Integer status);

    /** 已取消(2)的记录重新报名时复用: 换新凭证并回到 已报名(0)/候补(3) */
    @Update("UPDATE t_registration SET status = #{status}, voucher_code = #{voucherCode}, " +
            "activity_title = #{activityTitle}, start_time = #{startTime}, checkin_time = NULL " +
            "WHERE reg_id = #{regId} AND status = 2")
    int reactivate(Registration reg);

    /** 取消报名: 仅"已报名"可取消, 影响行数用于决定是否回补名额 */
    @Update("UPDATE t_registration SET status = 2 WHERE reg_id = #{regId} AND status = 0")
    int cancelFromRegistered(Long regId);

    /** 取消候补: 候补不占名额, 无需回补 */
    @Update("UPDATE t_registration SET status = 2 WHERE reg_id = #{regId} AND status = 3")
    int cancelFromWaiting(Long regId);

    /** 签到核销: 仅"已报名"可核销, 影响行数为 0 说明已核销或状态不符(防重放) */
    @Update("UPDATE t_registration SET status = 1, checkin_time = NOW() WHERE reg_id = #{regId} AND status = 0")
    int checkin(Long regId);

    /** 活动取消: 把该活动下 已报名/候补 的记录置为已取消 */
    @Update("UPDATE t_registration SET status = 2 WHERE activity_id = #{activityId} AND status IN (0, 3)")
    int cancelAllByActivity(Long activityId);
}
