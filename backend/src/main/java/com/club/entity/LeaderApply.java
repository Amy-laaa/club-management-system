package com.club.entity;

import java.time.LocalDateTime;

/**
 * 社团负责人资格申请实体, 对应 t_leader_apply。
 *
 * 流程: 学生提交申请(待审核) -> 平台管理员审批 -> 通过后 role_code 升级为 LEADER,
 * 之后才具备"创建社团 / 发布活动 / 成员管理"能力(bu_创建社团 的参与者是负责人)。
 *
 * 说明: 设计说明书 12 个核心用例未包含"申请成为负责人", 本模块是
 * 表 1-1「系统管理员: 账号 / 角色 / 权限分配」的落地方式, 属文档外扩展,
 * 报告与答辩中需按"扩展功能"表述。
 */
public class LeaderApply {
    private Long applyId;
    private Long userId;
    private String reason;
    private Integer status;         // 0待审核 1已通过 2已拒绝
    private Long reviewerId;
    private String reviewRemark;
    private LocalDateTime reviewedAt;
    private LocalDateTime createdAt;

    /** 列表展示冗余字段(联表查询) */
    private String studentNo;
    private String realName;
    private String mobile;
    private String roleCode;

    public Long getApplyId() { return applyId; }
    public void setApplyId(Long applyId) { this.applyId = applyId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
}
