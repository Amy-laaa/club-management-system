package com.club.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场地申请单实体, 对应 t_venue_application (设计说明书 表 2-17)。
 * status: 0待审核 1已通过(时段锁定) 2已驳回 3已释放(活动结束/取消后释放)。
 * 唯一键 uk_venue_slot_status(venue_id, use_date, time_slot, status) 兜底防时段冲突。
 */
public class VenueApplication {
    private Long appId;
    private Long venueId;
    private Long activityId;        // 可先占场地再建活动, 允许为空
    private Long applicantId;
    private LocalDate useDate;
    private String timeSlot;        // 如 18:00-21:00
    private String purpose;
    private Integer status;
    private String auditRemark;
    private LocalDateTime createdAt;

    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getAuditRemark() { return auditRemark; }
    public void setAuditRemark(String auditRemark) { this.auditRemark = auditRemark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
