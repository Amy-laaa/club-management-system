package com.club.dto;

/**
 * 置顶 / 取消置顶请求体。
 *
 * <p>前端「公告中心」的置顶按钮走 {@code POST /api/notices/{id}/pin}。
 * {@code isPinned} 传 1 置顶、0 取消；**不传则按当前状态取反(切换)**，
 * 这样一个按钮即可完成置顶/取消置顶两种操作。
 */
public class NoticePinDTO {

    private Integer isPinned;

    public Integer getIsPinned() { return isPinned; }
    public void setIsPinned(Integer isPinned) { this.isPinned = isPinned; }
}
