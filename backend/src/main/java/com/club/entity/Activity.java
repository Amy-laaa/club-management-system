package com.club.entity;

import java.time.LocalDateTime;

/**
 * 活动实体, 对应 t_activity (设计说明书 表 2-13)。
 * status: 0待审核 1已发布 2进行中 3已结束 4已取消(含驳回)。
 * remain 为强一致资源, 报名用条件更新扣减以防超卖。
 */
public class Activity {
    private Long activityId;
    private Long clubId;
    private Long venueAppId;            // 关联场地申请单(0..1)
    private String title;
    private Integer actType;            // 0内部 1公开
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String location;
    private Integer capacity;           // 名额 1-500
    private Integer remain;             // 剩余名额
    private LocalDateTime signupDeadline;
    private String intro;
    private Integer status;
    private LocalDateTime createdAt;

    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public Long getClubId() { return clubId; }
    public void setClubId(Long clubId) { this.clubId = clubId; }
    public Long getVenueAppId() { return venueAppId; }
    public void setVenueAppId(Long venueAppId) { this.venueAppId = venueAppId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getActType() { return actType; }
    public void setActType(Integer actType) { this.actType = actType; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getCapacity() { return capacity; }
    public void setCapacity(Integer capacity) { this.capacity = capacity; }
    public Integer getRemain() { return remain; }
    public void setRemain(Integer remain) { this.remain = remain; }
    public LocalDateTime getSignupDeadline() { return signupDeadline; }
    public void setSignupDeadline(LocalDateTime signupDeadline) { this.signupDeadline = signupDeadline; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
