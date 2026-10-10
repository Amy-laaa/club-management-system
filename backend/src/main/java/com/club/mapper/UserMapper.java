package com.club.mapper;

import com.club.entity.User;
import org.apache.ibatis.annotations.*;

/**
 * 用户表数据访问。SQL 直接写在注解里, 后续复杂查询再拆 XML。
 */
@Mapper
public interface UserMapper {

    @Select("SELECT * FROM t_user WHERE student_no = #{studentNo}")
    User selectByStudentNo(@Param("studentNo") String studentNo);

    @Select("SELECT * FROM t_user WHERE mobile = #{mobile}")
    User selectByMobile(@Param("mobile") String mobile);

    @Select("SELECT * FROM t_user WHERE user_id = #{userId}")
    User selectById(@Param("userId") Long userId);

    @Insert("INSERT INTO t_user (student_no, real_name, password_hash, mobile, role_code, status, created_at) " +
            "VALUES (#{studentNo}, #{realName}, #{passwordHash}, #{mobile}, #{roleCode}, 1, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(User user);

    @Update("UPDATE t_user SET last_login_at = NOW() WHERE user_id = #{userId}")
    int touchLogin(@Param("userId") Long userId);

    /** 角色变更: 负责人资格申请审批通过后 STUDENT -> LEADER */
    @Update("UPDATE t_user SET role_code = #{roleCode} WHERE user_id = #{userId}")
    int updateRoleCode(@Param("userId") Long userId, @Param("roleCode") String roleCode);
}
