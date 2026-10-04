package com.club.dto;

import javax.validation.constraints.*;
import java.time.LocalDateTime;

/**
 * 发布活动请求体 (用例 bu_发布活动)。
 * 校验要点: 名额 1-500; 报名截止必须早于活动开始; 结束时间晚于开始时间。
 */
public class ActivityCreateDTO {

    @NotNull(message = "请选择所属社团")
    private Long clubId;

    /** 关联场地申请单, 可为空(不占场地或稍后补) */
    private Long venueAppId;

    @NotBlank(message = "请填写活动名称")
    @Size(max = 40, message = "活动名称不超过 40 字")
    private String title;

    /** 0内部 1公开; 公开活动需社联审核通过后才对全校可见 */
    @NotNull(message = "请选择活动类型")
    @Min(value = 0, message = "活动类型不合法")
    @Max(value = 1, message = "活动类型不合法")
    private Integer actType;

    @NotNull(message = "请选择开始时间")
    @Future(message = "活动开始时间必须在未来")
    private LocalDateTime startTime;

    @NotNull(message = "请选择结束时间")
    private LocalDateTime endTime;

    @NotBlank(message = "请填写活动地点")
    @Size(max = 80, message = "地点不超过 80 字")
    private String location;

    @NotNull(message = "请填写活动名额")
    @Min(value = 1, message = "名额需在 1-500 之间")
    @Max(value = 500, message = "名额需在 1-500 之间")
    private Integer capacity;

    @NotNull(message = "请选择报名截止时间")
    private LocalDateTime signupDeadline;

    @Size(max = 500, message = "简介不超过 500 字")
    private String intro;

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
    public LocalDateTime getSignupDeadline() { return signupDeadline; }
    public void setSignupDeadline(LocalDateTime signupDeadline) { this.signupDeadline = signupDeadline; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
}
