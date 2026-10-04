package com.club.entity;

import java.time.LocalDateTime;

/**
 * 用户实体, 对应 t_user (设计说明书 表 2-11)。
 */
public class User {
    private Long userId;
    private String studentNo;
    private String realName;
    private String passwordHash;   // 永不返回前端
    private String mobile;
    private String roleCode;       // STUDENT/LEADER/UNION_ADMIN/SYS_ADMIN
    private Integer status;        // 0停用 1正常 2锁定
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getMobile() { return mobile; }
    public void setMobile(String mobile) { this.mobile = mobile; }
    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}
