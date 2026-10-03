package com.club.entity;

import java.time.LocalDateTime;

/**
 * 审批记录实体, 对应 t_audit_log (设计说明书 表 2-19)。
 * biz_type + biz_id 多态关联社团/活动/场地申请。
 */
public class AuditLog {
    private Long logId;
    private String bizType;         // CLUB/ACTIVITY/VENUE_APP
    private Long bizId;
    private Long auditorId;
    private Integer result;         // 0驳回 1通过
    private String remark;
    private LocalDateTime createdAt;

    public Long getLogId() { return logId; }
    public void setLogId(Long logId) { this.logId = logId; }
    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }
    public Long getBizId() { return bizId; }
    public void setBizId(Long bizId) { this.bizId = bizId; }
    public Long getAuditorId() { return auditorId; }
    public void setAuditorId(Long auditorId) { this.auditorId = auditorId; }
    public Integer getResult() { return result; }
    public void setResult(Integer result) { this.result = result; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
