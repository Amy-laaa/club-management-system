package com.club.mapper;

import com.club.entity.Club;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 社团表数据访问。
 */
@Mapper
public interface ClubMapper {

    /**
     * 分页查询。status 可为 null(全部); keyword 模糊匹配名称。
     */
    @Select("<script>" +
            "SELECT c.*, u.real_name AS leader_name," +
            "  (SELECT COUNT(*) FROM t_membership m WHERE m.club_id = c.club_id AND m.status = 1) AS member_count " +
            "FROM t_club c JOIN t_user u ON u.user_id = c.leader_id " +
            "<where>" +
            "  <if test='status != null'> AND c.status = #{status}</if>" +
            "  <if test='keyword != null and keyword != \"\"'> AND c.club_name LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "</where>" +
            " ORDER BY c.created_at DESC LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Club> selectPage(@Param("status") Integer status,
                          @Param("keyword") String keyword,
                          @Param("offset") int offset,
                          @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM t_club " +
            "<where>" +
            "  <if test='status != null'> AND status = #{status}</if>" +
            "  <if test='keyword != null and keyword != \"\"'> AND club_name LIKE CONCAT('%', #{keyword}, '%')</if>" +
            "</where></script>")
    long count(@Param("status") Integer status, @Param("keyword") String keyword);

    @Select("SELECT c.*, u.real_name AS leader_name FROM t_club c JOIN t_user u ON u.user_id = c.leader_id WHERE c.club_id = #{clubId}")
    Club selectById(@Param("clubId") Long clubId);

    @Select("SELECT COUNT(*) FROM t_club WHERE club_name = #{clubName}")
    int countByName(@Param("clubName") String clubName);

    /** 创建社团(状态=待审核) */
    @Insert("INSERT INTO t_club (leader_id, club_name, category, intro, charter, advisor, status, recruit_deadline, created_at) " +
            "VALUES (#{leaderId}, #{clubName}, #{category}, #{intro}, #{charter}, #{advisor}, 0, #{recruitDeadline}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "clubId")
    int insert(Club club);

    /** 一个负责人同一时刻只能有一个待审核社团(用例 bu_创建社团业务规则) */
    @Select("SELECT COUNT(*) FROM t_club WHERE leader_id = #{leaderId} AND status = 0")
    int countPendingByLeader(@Param("leaderId") Long leaderId);

    /** 状态迁移: 仅允许 待审核 -> 已成立/已注销, 条件更新防并发 */
    @Update("UPDATE t_club SET status = #{toStatus} WHERE club_id = #{clubId} AND status = 0")
    int updateStatusFromPending(@Param("clubId") Long clubId, @Param("toStatus") Integer toStatus);

    /** 该用户当前负责的「已成立」社团数。角色降级为 STUDENT 前的校验依据 */
    @Select("SELECT COUNT(*) FROM t_club WHERE leader_id = #{leaderId} AND status = 1")
    int countActiveByLeader(@Param("leaderId") Long leaderId);

    /** 解散执行: 已成立(1) -> 已注销(2), 条件更新防并发 */
    @Update("UPDATE t_club SET status = 2 WHERE club_id = #{clubId} AND status = 1")
    int updateStatusToDissolved(@Param("clubId") Long clubId);
}
