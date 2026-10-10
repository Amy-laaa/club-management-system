package com.club.mapper;

import com.club.entity.LeaderApply;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 社团负责人资格申请表数据访问。
 * 状态迁移一律用「条件更新 WHERE status = 0」实现, 由影响行数判定并发。
 */
@Mapper
public interface LeaderApplyMapper {

    @Insert("INSERT INTO t_leader_apply (user_id, reason, status, created_at) " +
            "VALUES (#{userId}, #{reason}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "applyId")
    int insert(LeaderApply a);

    @Select("SELECT a.*, u.student_no, u.real_name, u.mobile, u.role_code " +
            "FROM t_leader_apply a JOIN t_user u ON u.user_id = a.user_id " +
            "WHERE a.apply_id = #{id}")
    LeaderApply selectById(@Param("id") Long id);

    /** 同一学生是否存在待审核申请(防重复提交) */
    @Select("SELECT COUNT(*) FROM t_leader_apply WHERE user_id = #{userId} AND status = 0")
    int countPendingByUser(@Param("userId") Long userId);

    /** 学生视角: 我的申请记录 */
    @Select("SELECT a.*, u.student_no, u.real_name, u.mobile, u.role_code " +
            "FROM t_leader_apply a JOIN t_user u ON u.user_id = a.user_id " +
            "WHERE a.user_id = #{userId} ORDER BY a.created_at DESC")
    List<LeaderApply> selectMine(@Param("userId") Long userId);

    /** 管理员视角: 按状态分页(0待审 1已通过 2已拒绝) */
    @Select("SELECT a.*, u.student_no, u.real_name, u.mobile, u.role_code " +
            "FROM t_leader_apply a JOIN t_user u ON u.user_id = a.user_id " +
            "WHERE a.status = #{status} ORDER BY a.created_at ASC LIMIT #{offset}, #{limit}")
    List<LeaderApply> selectPageByStatus(@Param("status") Integer status,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_leader_apply WHERE status = #{status}")
    long countByStatus(@Param("status") Integer status);

    /** 状态迁移: 仅允许 0 -> 1/2, 条件更新防重复审批 */
    @Update("UPDATE t_leader_apply SET status = #{status}, reviewer_id = #{reviewerId}, " +
            "review_remark = #{remark}, reviewed_at = NOW() " +
            "WHERE apply_id = #{id} AND status = 0")
    int reviewFromPending(@Param("id") Long id,
                          @Param("status") Integer status,
                          @Param("reviewerId") Long reviewerId,
                          @Param("remark") String remark);
}
