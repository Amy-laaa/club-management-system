package com.club.entity;

import java.time.LocalDateTime;

/**
 * 社团解散申请实体, 对应 t_club_dissolve。
 *
 * 依据设计说明书表 1-1: 社团负责人可"解散社团", 社团联合会管理员"审批社团成立/注销"。
 * 流程: 负责人提交解散申请 -> 社联管理员审批 -> 通过后由负责人在系统内执行注销,
 * 执行时 t_club.status 置为 2(已注销), 该社团全部生效成员记录置为已退出。
 *
 * 状态: 0待审批 1已批准(待执行) 2已驳回 3已注销
 */
public class ClubDissolve {
    private Long dissolveId;
    private Long clubId;
    private Long applicantId;
    private String reason;
    private Integer status;
    private Long reviewerId;
    private String reviewRemark;
    private LocalDateTime reviewedAt;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;

    /** 列表展示冗余字段(联表查询) */
    private String clubName;
    private String applicantName;
    private String category;

    public Long getDissolveId() { return dissolveId; }
    public void setDissolveId(Long dissolveId) { this.dissolveId = dissolveId; }
    public Long getClubId() { return clubId; }
    public void setClubId(Long clubId) { this.clubId = clubId; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Long getReviewerId() { return reviewerId; }
    public void setReviewerId(Long reviewerId) { this.reviewerId = reviewerId; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
