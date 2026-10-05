package com.club.dto;

/**
 * 发布公告请求体 (契约 #23)。
 * <p>title ≤40 字; content 非空。
 * <p>scope / isPinned 传值仅作意愿表达, 最终由 Service 按角色校正:
 * 社长 → 本社团公告(scope=0, club=自己的社团); 社联/系统管理员 → 全校公告(scope=1, club 空)。
 */
public class NoticeCreateDTO {

    private String title;
    private String content;
    private Integer scope;
    private Integer isPinned;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public Integer getScope() { return scope; }
    public void setScope(Integer scope) { this.scope = scope; }

    public Integer getIsPinned() { return isPinned; }
    public void setIsPinned(Integer isPinned) { this.isPinned = isPinned; }
}
