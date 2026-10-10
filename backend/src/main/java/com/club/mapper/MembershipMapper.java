package com.club.mapper;

import com.club.entity.Membership;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 成员资格表数据访问。
 */
@Mapper
public interface MembershipMapper {

    /** 新增记录(入社申请或负责人初始资格) */
    @Insert("INSERT INTO t_membership (user_id, club_id, member_role, apply_reason, status, joined_at, created_at) " +
            "VALUES (#{userId}, #{clubId}, #{memberRole}, #{applyReason}, #{status}, #{joinedAt}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "membershipId")
    int insert(Membership m);

    @Select("SELECT m.*, u.student_no, u.real_name, c.club_name " +
            "FROM t_membership m JOIN t_user u ON u.user_id = m.user_id JOIN t_club c ON c.club_id = m.club_id " +
            "WHERE m.membership_id = #{id}")
    Membership selectById(@Param("id") Long id);

    /** 同一学生同一社团是否存在生效记录(待审核/正式成员) */
    @Select("SELECT COUNT(*) FROM t_membership WHERE user_id = #{userId} AND club_id = #{clubId} AND status IN (0, 1)")
    int countActive(@Param("userId") Long userId, @Param("clubId") Long clubId);

    /** 学生视角: 我的社团与申请 */
    @Select("SELECT m.*, u.student_no, u.real_name, c.club_name " +
            "FROM t_membership m JOIN t_user u ON u.user_id = m.user_id JOIN t_club c ON c.club_id = m.club_id " +
            "WHERE m.user_id = #{userId} ORDER BY m.created_at DESC")
    List<Membership> selectMine(@Param("userId") Long userId);

    /** 社长审批列表: 某社团待审核申请(分页) */
    @Select("SELECT m.*, u.student_no, u.real_name, c.club_name " +
            "FROM t_membership m JOIN t_user u ON u.user_id = m.user_id JOIN t_club c ON c.club_id = m.club_id " +
            "WHERE m.club_id = #{clubId} AND m.status = #{status} ORDER BY m.created_at DESC LIMIT #{offset}, #{limit}")
    List<Membership> selectPageByClub(@Param("clubId") Long clubId,
                                      @Param("status") Integer status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM t_membership WHERE club_id = #{clubId} AND status = #{status}")
    long countByClub(@Param("clubId") Long clubId, @Param("status") Integer status);

    /** 状态迁移: 仅允许 0 -> 1/2, 由 Service 校验前置状态 */
    @Update("UPDATE t_membership SET status = #{status}, joined_at = #{joinedAt} WHERE membership_id = #{id} AND status = 0")
    int updateStatusFromPending(@Param("id") Long id, @Param("status") Integer status, @Param("joinedAt") java.time.LocalDateTime joinedAt);

    /** 正式成员数 */
    @Select("SELECT COUNT(*) FROM t_membership WHERE club_id = #{clubId} AND status = 1")
    int countMembers(@Param("clubId") Long clubId);

    /** 社团注销: 该社团全部生效记录(待审核/正式成员)置为已退出 */
    @Update("UPDATE t_membership SET status = 3 WHERE club_id = #{clubId} AND status IN (0, 1)")
    int dissolveAllByClub(@Param("clubId") Long clubId);
}
