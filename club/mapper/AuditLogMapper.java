package com.club.mapper;

import com.club.entity.AuditLog;
import org.apache.ibatis.annotations.*;

/**
 * 审批记录表数据访问。
 */
@Mapper
public interface AuditLogMapper {

    @Insert("INSERT INTO t_audit_log (biz_type, biz_id, auditor_id, result, remark, created_at) " +
            "VALUES (#{bizType}, #{bizId}, #{auditorId}, #{result}, #{remark}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "logId")
    int insert(AuditLog log);

    @Select("SELECT * FROM t_audit_log WHERE biz_type = #{bizType} AND biz_id = #{bizId} ORDER BY created_at DESC")
    java.util.List<AuditLog> selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
