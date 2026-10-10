package com.club.dto;

import javax.validation.constraints.Size;

/**
 * 社团负责人资格申请请求。审批复用 AuditDTO(result + remark)。
 */
public class LeaderApplyDTO {

    @Size(max = 200, message = "申请理由不超过 200 字")
    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
