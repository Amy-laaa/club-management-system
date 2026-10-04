package com.club.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 场地申请请求体 (用例 bu_申请场地)。
 */
public class VenueApplyDTO {

    @NotNull(message = "请选择场地")
    private Long venueId;

    /** 关联活动编号, 允许为空(可先占场地再建活动) */
    private Long activityId;

    @NotNull(message = "请选择使用日期")
    private LocalDate useDate;

    /** 时段, 如 18:00-21:00 (前端场地×日期×时段矩阵中选择) */
    @NotBlank(message = "请选择使用时段")
    @Pattern(regexp = "^\\d{2}:\\d{2}-\\d{2}:\\d{2}$", message = "时段格式不正确, 应如 18:00-21:00")
    private String timeSlot;

    @NotBlank(message = "请填写活动用途")
    @Size(max = 200, message = "用途不超过 200 字")
    private String purpose;

    public Long getVenueId() { return venueId; }
    public void setVenueId(Long venueId) { this.venueId = venueId; }
    public Long getActivityId() { return activityId; }
    public void setActivityId(Long activityId) { this.activityId = activityId; }
    public LocalDate getUseDate() { return useDate; }
    public void setUseDate(LocalDate useDate) { this.useDate = useDate; }
    public String getTimeSlot() { return timeSlot; }
    public void setTimeSlot(String timeSlot) { this.timeSlot = timeSlot; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
}
