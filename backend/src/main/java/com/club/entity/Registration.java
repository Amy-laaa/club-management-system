package com.club.entity;

import java.time.LocalDateTime;

/**
 * 报名记录实体, 对应 t_registration (设计说明书 表 2-14)。
 * status: 0已报名 1已签到 2已取消 3候补。
 * 唯一键 uk_act_user(activity_id, user_id) 保证同一学生同一活动仅一条记录;
 * 唯一键 uk_voucher 保证凭证码唯一(防重放)。
 */
public class Registration {
    private Long regId;
    private Long activityId;
    private Long userId;
    private String activityTitle;       // 活动名称快照(活动改期/改名不影响凭证展示)
    private LocalDateTime startTime;    // 活动开始时间快照
    private String voucherCode;         // 电子凭证码
    private Integer status;
    private LocalDateTime checkinTime;
    private LocalDateTime createdAt;

    public Long getRegId() { return regId; }
    public void setRegId(Long regId) { this.regId = regId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getActivityTitle() { return activityTitle; }
    public void setActivityTitle(String activityTitle) { this.activityTitle = activityTitle; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public String getVoucherCode() { return voucherCode; }
    public void setVoucherCode(String voucherCode) { this.voucherCode = voucherCode; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCheckinTime() { return checkinTime; }
    public void setCheckinTime(LocalDateTime checkinTime) { this.checkinTime = checkinTime; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
