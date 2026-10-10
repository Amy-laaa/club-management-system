package com.club.dto;

import javax.validation.constraints.Size;

/**
 * 社团解散申请请求。审批复用 AuditDTO(result + remark)。
 */
public class DissolveApplyDTO {

    @Size(max = 200, message = "解散理由不超过 200 字")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
