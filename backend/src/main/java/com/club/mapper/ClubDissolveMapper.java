package com.club.mapper;

import com.club.entity.ClubDissolve;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 社团解散申请表数据访问。
 * 状态迁移一律用「条件更新 WHERE status = 0/1」实现, 由影响行数判定并发。
 */
@Mapper
public interface ClubDissolveMapper {

    @Insert("INSERT INTO t_club_dissolve (club_id, applicant_id, reason, status, created_at) " +
            "VALUES (#{clubId}, #{applicantId}, #{reason}, 0, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "dissolveId")
    int insert(ClubDissolve d);

    @Select("SELECT d.*, c.club_name, c.category, u.real_name AS applicant_name " +
            "FROM t_club_dissolve d JOIN t_club c ON c.club_id = d.club_id " +
            "JOIN t_user u ON u.user_id = d.applicant_id WHERE d.dissolve_id = #{id}")
    ClubDissolve selectById(@Param("id") Long id);

    /** 该社团是否存在待审批的解散申请(防重复提交) */
    @Select("SELECT COUNT(*) FROM t_club_dissolve WHERE club_id = #{clubId} AND status = 0")
    int countPendingByClub(@Param("clubId") Long clubId);

    /** 该社团是否有已批准待执行的解散申请 */
    @Select("SELECT d.* FROM t_club_dissolve d WHERE d.club_id = #{clubId} AND d.status = 1 " +
            "ORDER BY d.created_at DESC LIMIT 1")
    ClubDissolve selectApprovedByClub(@Param("clubId") Long clubId);

    /** 负责人查看本社团最近一条解散申请 */
    @Select("SELECT d.*, c.club_name, c.category, u.real_name AS applicant_name " +
            "FROM t_club_dissolve d JOIN t_club c ON c.club_id = d.club_id " +
            "JOIN t_user u ON u.user_id = d.applicant_id " +
            "WHERE d.club_id = #{clubId} ORDER BY d.created_at DESC LIMIT 1")
    ClubDissolve selectLatestByClub(@Param("clubId") Long clubId);

    /** 管理员: 按状态分页(0待审批 1已批准 2已驳回 3已注销) */
    @Select("SELECT d.*, c.club_name, c.category, u.real_name AS applicant_name " +
            "FROM t_club_dissolve d JOIN t_club c ON c.club_id = d.club_id " +
            "JOIN t_user u ON u.user_id = d.applicant_id " +
            "WHERE d.status = #{status} ORDER BY d.created_at ASC LIMIT #{offset}, #{limit}")
    List<ClubDissolve> selectPageByStatus(@Param("status") Integer status,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_club_dissolve WHERE status = #{status}")
    long countByStatus(@Param("status") Integer status);

    /** 审批: 仅允许 0 -> 1/2 */
    @Update("UPDATE t_club_dissolve SET status = #{status}, reviewer_id = #{reviewerId}, " +
            "review_remark = #{remark}, reviewed_at = NOW() " +
            "WHERE dissolve_id = #{id} AND status = 0")
    int reviewFromPending(@Param("id") Long id,
                          @Param("status") Integer status,
                          @Param("reviewerId") Long reviewerId,
                          @Param("remark") String remark);

    /** 执行注销: 仅允许 1 -> 3 */
    @Update("UPDATE t_club_dissolve SET status = 3, executed_at = NOW() " +
            "WHERE dissolve_id = #{id} AND status = 1")
    int markExecuted(@Param("id") Long id);
}
