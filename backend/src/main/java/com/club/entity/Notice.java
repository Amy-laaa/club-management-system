package com.club.entity;

import java.time.LocalDateTime;

/**
 * 公告 (t_notice)。
 * <ul>
 *   <li>scope: 0=本社团可见, 1=全校可见</li>
 *   <li>isPinned: 0=普通, 1=置顶(校级同时最多 3 条)</li>
 *   <li>status: 0=已撤回, 1=已发布</li>
 * </ul>
 * publisherName / clubName 为联表展示字段, 非表列。
 */
public class Notice {

    private Long noticeId;
    private Long clubId;
    private Long publisherId;
    private String title;
    private String content;
    private Integer scope;
    private Integer isPinned;
    private Integer status;
    private LocalDateTime createdAt;

    private String publisherName;
    private String clubName;

    public Long getNoticeId() { return noticeId; }
    public void setNoticeId(Long noticeId) { this.noticeId = noticeId; }

    public Long getClubId() { return clubId; }
    public void setClubId(Long clubId) { this.clubId = clubId; }

    public Long getPublisherId() { return publisherId; }
    public void setPublisherId(Long publisherId) { this.publisherId = publisherId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getScope() { return scope; }
    public void setScope(Integer scope) { this.scope = scope; }

    public Integer getIsPinned() { return isPinned; }
    public void setIsPinned(Integer isPinned) { this.isPinned = isPinned; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getPublisherName() { return publisherName; }
    public void setPublisherName(String publisherName) { this.publisherName = publisherName; }

    public String getClubName() { return clubName; }
    public void setClubName(String clubName) { this.clubName = clubName; }
}
