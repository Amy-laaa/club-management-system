package com.club.mapper;

import com.club.entity.Notice;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 公告表数据访问 (t_notice)。
 *
 * <p>对应接口契约 #22 公告列表 / #23 发布公告。SQL 以内联注解方式编写,
 * 与项目其它 Mapper 保持一致(见 {\@code VenueApplicationMapper})。
 *
 * <p>关于「社长属于哪个社团」: 项目既有口径是 t_club.leader_id
 * (见 {\@code ActivityService#requireActivityLeader}), 因此这里直接查 t_club,
 * 不引入 ClubMapper, 避免改动他人文件。
 *
 * <p>列表方法把所有列都显式起了小驼峰别名, 不依赖
 * {\@code map-underscore-to-camel-case} 配置, 单独跑测试也安全。
 */
@Mapper
public interface NoticeMapper {

    /**
     * 公告列表: 取指定 scope(可选再按 clubId 收窄)的已发布公告。
     * 排序「置顶优先、时间倒序」, 与索引 ix_scope_pin_time 顺序一致。
     */
    @Select("<script>" +
            "SELECT n.notice_id AS noticeId, n.club_id AS clubId, n.publisher_id AS publisherId, " +
            "       n.title AS title, n.content AS content, n.scope AS scope, " +
            "       n.is_pinned AS isPinned, n.status AS status, n.created_at AS createdAt, " +
            "       u.real_name AS publisherName, c.club_name AS clubName " +
            "FROM t_notice n " +
            "JOIN t_user u ON u.user_id = n.publisher_id " +
            "LEFT JOIN t_club c ON c.club_id = n.club_id " +
            "WHERE n.status = 1 AND n.scope = #{scope} " +
            "<if test='clubId != null'> AND n.club_id = #{clubId} </if>" +
            "ORDER BY n.is_pinned DESC, n.created_at DESC, n.notice_id DESC " +
            "LIMIT #{offset}, #{limit}" +
            "</script>")
    List<Notice> selectPage(@Param("scope") Integer scope,
                            @Param("clubId") Long clubId,
                            @Param("offset") int offset,
                            @Param("limit") int limit);

    /** 列表总数, 与 {@link #selectPage} 的 where 条件严格一致 */
    @Select("<script>" +
            "SELECT COUNT(*) FROM t_notice n " +
            "WHERE n.status = 1 AND n.scope = #{scope} " +
            "<if test='clubId != null'> AND n.club_id = #{clubId} </if>" +
            "</script>")
    long count(@Param("scope") Integer scope, @Param("clubId") Long clubId);

    /** 单条公告(含发布人姓名、社团名, 供详情/撤回前校验) */
    @Select("SELECT n.notice_id AS noticeId, n.club_id AS clubId, n.publisher_id AS publisherId, " +
            "       n.title AS title, n.content AS content, n.scope AS scope, " +
            "       n.is_pinned AS isPinned, n.status AS status, n.created_at AS createdAt, " +
            "       u.real_name AS publisherName, c.club_name AS clubName " +
            "FROM t_notice n " +
            "JOIN t_user u ON u.user_id = n.publisher_id " +
            "LEFT JOIN t_club c ON c.club_id = n.club_id " +
            "WHERE n.notice_id = #{id}")
    Notice selectById(@Param("id") Long id);

    /** 发布公告; 回填自增主键 noticeId */
    @Insert("INSERT INTO t_notice (club_id, publisher_id, title, content, scope, is_pinned, status, created_at) " +
            "VALUES (#{clubId}, #{publisherId}, #{title}, #{content}, #{scope}, #{isPinned}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "noticeId")
    int insert(Notice notice);

    /** 撤回公告: 仅「已发布(status = 1)」可撤回, 条件更新防止并发重复撤回 */
    @Update("UPDATE t_notice SET status = 0 WHERE notice_id = #{id} AND status = 1")
    int withdraw(@Param("id") Long id);

    /** 置顶 / 取消置顶: 已撤回的公告不可改, 条件更新防并发 */
    @Update("UPDATE t_notice SET is_pinned = #{isPinned} WHERE notice_id = #{id} AND status = 1")
    int updatePinned(@Param("id") Long id, @Param("isPinned") Integer isPinned);

    /** 校级置顶公告条数(业务规则: 校级同时最多 3 条置顶) */
    @Select("SELECT COUNT(*) FROM t_notice WHERE scope = 1 AND is_pinned = 1 AND status = 1")
    int countPinnedUnion();

    /** 社长所负责的已成立社团; 一个负责人 1:1 对应一个社团, 取一条即可 */
    @Select("SELECT club_id FROM t_club WHERE leader_id = #{leaderId} AND status = 1 LIMIT 1")
    Long selectClubIdByLeader(@Param("leaderId") Long leaderId);

    /** 是否该社团正式成员(status = 1), 用于本社团公告的可见性判断 */
    @Select("SELECT COUNT(*) FROM t_membership WHERE user_id = #{userId} AND club_id = #{clubId} AND status = 1")
    int countMember(@Param("userId") Long userId, @Param("clubId") Long clubId);
}
