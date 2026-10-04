package com.club.entity;

import java.time.LocalDateTime;

/**
 * 成员资格实体, 对应 t_membership (设计说明书 表 2-13)。
 * 同一 (user, club) 仅一条记录, 靠唯一约束兜底防重复申请。
 */
public class Membership {
    private Long membershipId;
    private Long userId;
    private Long clubId;
    private String memberRole;      // LEADER/ADMIN/MEMBER
    private String applyReason;
    private Integer status;         // 0待审核 1正式成员 2已拒绝 3已退出
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;

    /** 列表展示冗余字段(联表查询) */
    private String studentNo;
    private String realName;
    private String clubName;

    public Long getMembershipId() { return membershipId; }
    public void setMembershipId(Long membershipId) { this.membershipId = membershipId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getClubId() { return clubId; }
    public void setClubId(Long clubId) { this.clubId = clubId; }
    public String getMemberRole() { return memberRole; }
    public void setMemberRole(String memberRole) { this.memberRole = memberRole; }
    public String getApplyReason() { return applyReason; }
    public void setApplyReason(String applyReason) { this.applyReason = applyReason; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getJoinedAt() { return joinedAt; }
    public void setJoinedAt(LocalDateTime joinedAt) { this.joinedAt = joinedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}
