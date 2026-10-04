package com.club.entity;

import java.time.LocalDateTime;

/**
 * 社团实体, 对应 t_club (设计说明书 表 2-12)。
 */
public class Club {
    private Long clubId;
    private Long leaderId;
    private String clubName;
    private String category;
    private String intro;
    private String charter;
    private String advisor;
    private Integer status;          // 0待审核 1已成立 2已注销
    private LocalDateTime recruitDeadline;
    private LocalDateTime createdAt;

    /** 列表展示用的冗余字段(非表字段) */
    private String leaderName;
    private Integer memberCount;

    public Long getClubId() { return clubId; }
    public void setClubId(Long clubId) { this.clubId = clubId; }
    public Long getLeaderId() { return leaderId; }
    public void setLeaderId(Long leaderId) { this.leaderId = leaderId; }
    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIntro() { return intro; }
    public void setIntro(String intro) { this.intro = intro; }
    public String getCharter() { return charter; }
    public void setCharter(String charter) { this.charter = charter; }
    public String getAdvisor() { return advisor; }
    public void setAdvisor(String advisor) { this.advisor = advisor; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getRecruitDeadline() { return recruitDeadline; }
    public void setRecruitDeadline(LocalDateTime recruitDeadline) { this.recruitDeadline = recruitDeadline; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public String getLeaderName() { return leaderName; }
    public void setLeaderName(String leaderName) { this.leaderName = leaderName; }
    public Integer getMemberCount() { return memberCount; }
    public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
}
